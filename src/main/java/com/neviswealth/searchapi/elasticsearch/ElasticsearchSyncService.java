package com.neviswealth.searchapi.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.neviswealth.searchapi.client.ClientRepository;
import com.neviswealth.searchapi.document.DocumentVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ElasticsearchSyncService {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSyncService.class);

    private final ElasticsearchClient es;
    private final DocumentVectorRepository documentVectorRepository;
    private final ClientRepository clientRepository;

    public ElasticsearchSyncService(ElasticsearchClient es,
                                    DocumentVectorRepository documentVectorRepository,
                                    ClientRepository clientRepository) {
        this.es = es;
        this.documentVectorRepository = documentVectorRepository;
        this.clientRepository = clientRepository;
    }

    public record ScoredHit(Map<String, Object> source, double score) {}

    // ── Index a client ────────────────────────────────────────

    public void indexClient(Map<String, Object> client) {
        try {
            es.index(i -> i
                    .index("clients")
                    .id(client.get("id").toString())
                    .document(client)
            );
            log.info("Indexed client {}", client.get("id"));
        } catch (Exception e) {
            log.warn("Failed to index client {} — will be picked up by reindex on startup: {}",
                    client.get("id"), e.getMessage());
        }
    }

    // ── Index a document ──────────────────────────────────────

    public void indexDocument(Map<String, Object> document) {
        try {
            es.index(i -> i
                    .index("documents")
                    .id(document.get("id").toString())
                    .document(document)
            );
            log.info("Indexed document {}", document.get("id"));
        } catch (Exception e) {
            log.warn("Failed to index document {} — will be picked up by reindex on startup: {}",
                    document.get("id"), e.getMessage());
        }
    }

    // ── Search clients (lexical) ──────────────────────────────

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchClients(String query) {
        try {
            SearchResponse<Map> response = es.search(s -> s
                            .index("clients")
                            .query(q -> q
                                    .bool(b -> b
                                            .should(sh -> sh
                                                    .multiMatch(m -> m
                                                            .query(query)
                                                            .fields("first_name^2", "last_name^2", "email^3", "description")
                                                            .fuzziness("AUTO")
                                                    )
                                            )
                                            .should(sh -> sh
                                                    .wildcard(w -> w
                                                            .field("email")
                                                            .value("*" + query.toLowerCase() + "*")
                                                    )
                                            )
                                            .should(sh -> sh
                                                    .matchPhrasePrefix(m -> m
                                                            .field("first_name")
                                                            .query(query.toLowerCase())
                                                    )
                                            )
                                            .should(sh -> sh
                                                    .matchPhrasePrefix(m -> m
                                                            .field("last_name")
                                                            .query(query.toLowerCase())
                                                    )
                                            )
                                            .minimumShouldMatch("1")
                                    )
                            ),
                    Map.class
            );
            return response.hits().hits().stream()
                    .map(hit -> (Map<String, Object>) hit.source())
                    .toList();
        } catch (Exception e) {
            log.error("Client search failed for query: {}", query, e);
            return List.of();
        }
    }

    // ── Search documents (semantic + hybrid) ─────────────────

    @SuppressWarnings("unchecked")
    public List<ScoredHit> searchDocuments(String query, List<Float> queryEmbedding) {
        try {
            SearchResponse<Map> response = es.search(s -> s
                            .index("documents")
                            .knn(k -> k
                                    .field("embedding")
                                    .queryVector(queryEmbedding)
                                    .k(10)
                                    .numCandidates(100)
                            )
                            .query(q -> q
                                    .multiMatch(m -> m
                                            .query(query)
                                            .fields("title^2", "content", "summary")
                                            .fuzziness("AUTO")
                                    )
                            ),
                    Map.class
            );
            return response.hits().hits().stream()
                    .map(hit -> new ScoredHit(
                            (Map<String, Object>) hit.source(),
                            hit.score() != null ? hit.score() : 0.0
                    ))
                    .toList();
        } catch (Exception e) {
            log.error("Document search failed for query: {}", query, e);
            return List.of();
        }
    }

    // ── Reindex all clients from Postgres ─────────────────────

    public void reindexAllClients() throws Exception {
        es.ping();
        clientRepository.findAll().forEach(client -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",          client.getId().toString());
            map.put("first_name",  client.getFirstName());
            map.put("last_name",   client.getLastName());
            map.put("email",       client.getEmail());
            map.put("description", client.getDescription() != null ? client.getDescription() : "");
            map.put("created_at",  client.getCreatedAt() != null ? client.getCreatedAt().toString() : null);
            indexClient(map);
        });
        log.info("Client reindex complete");
    }

    // ── Reindex all documents from Postgres ───────────────────

    public void reindexAllDocuments() throws Exception {
        es.ping();
        documentVectorRepository.findAllForReindex().forEach(doc -> {
            List<Float> embeddingList = new ArrayList<>();
            if (doc.embedding() != null) {
                for (float v : doc.embedding()) embeddingList.add(v);
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id",         doc.id().toString());
            map.put("client_id",  doc.clientId().toString());
            map.put("title",      doc.title());
            map.put("content",    doc.content());
            map.put("summary",    doc.summary() != null ? doc.summary() : "");
            map.put("created_at", doc.createdAt() != null ? doc.createdAt().toString() : null);
            map.put("embedding",  embeddingList);

            indexDocument(map);
        });
        log.info("Reindex complete");
    }
}