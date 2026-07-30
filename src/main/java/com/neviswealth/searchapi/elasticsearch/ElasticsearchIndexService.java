package com.neviswealth.searchapi.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexService.class);

    private final ElasticsearchClient es;
    private final ElasticsearchSyncService esSyncService;

    @Value("${elasticsearch.init-max-attempts:3}")
    private int maxAttempts;

    @Value("${elasticsearch.init-retry-delay-ms:3000}")
    private long retryDelayMs;

    public ElasticsearchIndexService(ElasticsearchClient es,
                                     ElasticsearchSyncService esSyncService) {
        this.es = es;
        this.esSyncService = esSyncService;
    }

    public void reindexAll() {
        int attempt = 0;
        while (attempt < maxAttempts) {
            try {
                createIndices();
                log.info("Elasticsearch indices created");
                esSyncService.reindexAllClients();
                esSyncService.reindexAllDocuments();
                log.info("Elasticsearch reindex complete");
                return;
            } catch (Exception e) {
                attempt++;
                log.warn("ES not ready (attempt {}/{}): {}", attempt, maxAttempts, e.getMessage());
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        throw new IllegalStateException("Elasticsearch unreachable after " + maxAttempts + " attempts");
    }

    public void recreateIndices() throws Exception {
        createIndices();
    }

    private void createIndices() {
        createClientsIndex();
        createDocumentsIndex();
    }

    private void createClientsIndex() {
        try {
            boolean exists = es.indices().exists(
                    ExistsRequest.of(r -> r.index("clients"))
            ).value();
            if (exists) {
                log.info("Index 'clients' already exists, skipping creation");
                return;
            }
            CreateIndexResponse response = es.indices().create(c -> c
                    .index("clients")
                    .mappings(m -> m
                            .properties("id",          p -> p.keyword(k -> k))
                            .properties("first_name",  p -> p.text(t -> t))
                            .properties("last_name",   p -> p.text(t -> t))
                            .properties("email",       p -> p.keyword(k -> k))
                            .properties("description", p -> p.text(t -> t))
                            .properties("created_at",  p -> p.date(d -> d))
                    )
            );
            log.info("Created index 'clients': {}", response.acknowledged());
        } catch (Exception e) {
            log.error("Failed to create 'clients' index", e);
        }
    }

    private void createDocumentsIndex() {
        try {
            boolean exists = es.indices().exists(
                    ExistsRequest.of(r -> r.index("documents"))
            ).value();
            if (exists) {
                log.info("Index 'documents' already exists, skipping creation");
                return;
            }
            CreateIndexResponse response = es.indices().create(c -> c
                    .index("documents")
                    .mappings(m -> m
                            .properties("id",         p -> p.keyword(k -> k))
                            .properties("client_id",  p -> p.keyword(k -> k))
                            .properties("title",      p -> p.text(t -> t))
                            .properties("content",    p -> p.text(t -> t))
                            .properties("summary",    p -> p.text(t -> t))
                            .properties("created_at", p -> p.date(d -> d))
                            .properties("embedding",  p -> p.denseVector(d -> d
                                    .dims(384)
                                    .index(true)
                                    .similarity("cosine")
                            ))
                    )
            );
            log.info("Created index 'documents': {}", response.acknowledged());
        } catch (Exception e) {
            log.error("Failed to create 'documents' index", e);
        }
    }
}