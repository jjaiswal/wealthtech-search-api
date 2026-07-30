package com.neviswealth.searchapi.search;

import com.neviswealth.searchapi.client.ClientRepository;
import com.neviswealth.searchapi.client.dto.ClientResponse;
import com.neviswealth.searchapi.document.DocumentMatch;
import com.neviswealth.searchapi.document.DocumentVectorRepository;
import com.neviswealth.searchapi.elasticsearch.ElasticsearchSyncService;
import com.neviswealth.searchapi.embedding.EmbeddingClient;
import com.neviswealth.searchapi.search.dto.DocumentSummaryView;
import com.neviswealth.searchapi.search.dto.SearchHit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 20;

    private final ClientRepository clients;
    private final DocumentVectorRepository documentVectors;
    private final EmbeddingClient embeddingClient;
    private final ElasticsearchSyncService esSyncService;

    /**
     * Minimum cosine similarity for a document to be returned — a relevance floor that drops
     * near-orthogonal noise (see DESIGN §D6a). Externalized so it can be tuned per corpus/model
     * without a code change; the default sits in the gap between genuine matches (~0.30 for the
     * spec's "address proof" → utility bill) and noise (~0.17).
     */
    private final double minScore;

    public SearchService(ElasticsearchSyncService esSyncService,
                         ClientRepository clients,
                         DocumentVectorRepository documentVectors,
                         EmbeddingClient embeddingClient,
                         @Value("${search.min-score:0.2}") double minScore) {
        this.esSyncService = esSyncService;
        this.clients = clients;
        this.documentVectors = documentVectors;
        this.embeddingClient = embeddingClient;
        this.minScore = minScore;
    }

    /**
     * Searches clients (lexical) and documents (semantic) and returns a flat array of typed
     * hits. Clients come first, then documents ranked by cosine similarity.
     *
     * @param clientId if non-null, scope document search to this client; null = global search.
     */
    public List<SearchHit> search(String q, UUID clientId) {
        List<SearchHit> results = new ArrayList<>();

        // 1. Client search (lexical via ES multi_match)
        List<Map<String, Object>> clientHits = esSyncService.searchClients(q);
        for (Map<String, Object> hit : clientHits) {
            if (clientId != null && !clientId.toString().equals(hit.get("id"))) continue;
            results.add(SearchHit.client(toClientResponse(hit)));
        }

        // 2. Document search (semantic + hybrid via ES knn)
        float[] embeddingArr = embeddingClient.embed(q);
        List<Float> queryEmbedding = new ArrayList<>();
        for (float f : embeddingArr) queryEmbedding.add(f);

        List<ElasticsearchSyncService.ScoredHit> docHits = esSyncService.searchDocuments(q, queryEmbedding);
        for (ElasticsearchSyncService.ScoredHit hit : docHits) {
            if (hit.score() < minScore) continue;
            if (clientId == null || clientId.toString().equals(hit.source().get("client_id"))) {
                results.add(SearchHit.document(hit.score(), toDocumentSummaryView(hit.source())));
            }
        }

        return results;
    }

    private ClientResponse toClientResponse(Map<String, Object> hit) {
        return new ClientResponse(
                UUID.fromString((String) hit.get("id")),
                (String) hit.get("first_name"),
                (String) hit.get("last_name"),
                (String) hit.get("email"),
                (String) hit.get("description"),
                hit.get("social_links") != null
                        ? (List<String>) hit.get("social_links")
                        : List.of(),
                hit.get("created_at") != null
                        ? OffsetDateTime.parse((String) hit.get("created_at"))
                        : null
        );
    }

    private DocumentSummaryView toDocumentSummaryView(Map<String, Object> hit) {
        return new DocumentSummaryView(
                UUID.fromString((String) hit.get("id")),
                UUID.fromString((String) hit.get("client_id")),
                (String) hit.get("title"),
                (String) hit.get("summary"),
                hit.get("created_at") != null
                        ? OffsetDateTime.parse((String) hit.get("created_at"))
                        : null
        );
    }

}
