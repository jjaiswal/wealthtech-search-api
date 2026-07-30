package com.neviswealth.searchapi.document;

import com.neviswealth.searchapi.client.ClientRepository;
import com.neviswealth.searchapi.common.NotFoundException;
import com.neviswealth.searchapi.document.dto.CreateDocumentRequest;
import com.neviswealth.searchapi.document.summary.Summarizer;
import com.neviswealth.searchapi.elasticsearch.ElasticsearchSyncService;
import com.neviswealth.searchapi.embedding.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int MAX_SUMMARY_SENTENCES = 2;

    private final ClientRepository clients;
    private final DocumentVectorRepository documents;
    private final EmbeddingClient embeddingClient;
    private final Summarizer summarizer;
    private final ElasticsearchSyncService esSyncService;

    public DocumentService(ClientRepository clients,
                           DocumentVectorRepository documents,
                           EmbeddingClient embeddingClient,
                           Summarizer summarizer,
                           ElasticsearchSyncService esSyncService) {
        this.clients = clients;
        this.documents = documents;
        this.embeddingClient = embeddingClient;
        this.summarizer = summarizer;
        this.esSyncService = esSyncService;
    }

    /**
     * Creates a document, computing its embedding and summary at ingest so search never
     * re-computes them. HTTP calls run outside any transaction so no DB connection is held
     * across network I/O.
     *
     * <p>The embedding is required — without it the document is unsearchable, so an embedder
     * failure fails the ingest (surfaced as 503). The summary is an enrichment (R3): if the
     * summarizer is down, the document is still stored with a {@code null} summary rather than
     * failing the write. This avoids an availability inversion where a non-critical dependency
     * (Ollama) can block a core operation (document creation); the summary can be backfilled.
     */
    public Document create(UUID clientId, CreateDocumentRequest req) {
        if (!clients.existsById(clientId)) {
            throw new NotFoundException("No client with id '" + clientId + "'");
        }

        float[] embedding = embeddingClient.embed(req.content());   // required
        String summary = trySummarize(req.content());               // best-effort

        Document saved = documents.insert(clientId, req.title(), req.content(), summary, embedding);
        esSyncService.indexDocument(toMap(saved, embedding));
        return saved;
    }

    private Map<String, Object> toMap(Document d, float[] embedding) {
        List<Float> embeddingList = new ArrayList<>();
        for (float f : embedding) embeddingList.add(f);

        return Map.of(
                "id",         d.getId().toString(),
                "client_id",  d.getClientId().toString(),
                "title",      d.getTitle(),
                "content",    d.getContent(),
                "summary",    d.getSummary() != null ? d.getSummary() : "",
                "created_at", d.getCreatedAt().toString(),
                "embedding",  embeddingList
        );
    }

    /**
     * Summarization is best-effort: on any failure, log and store no summary
     * rather than failing ingest. Catches all exceptions (not just SummarizationException)
     * to guard against unexpected runtime failures from the summarizer dependency.
     */
    private String trySummarize(String content) {
        try {
            return summarizer.summarize(content, MAX_SUMMARY_SENTENCES);
        } catch (Exception e) {
            log.warn("Summary unavailable; storing document without one: {}", e.getMessage());
            return null;
        }
    }
}