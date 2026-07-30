package com.neviswealth.searchapi;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.neviswealth.searchapi.document.summary.Summarizer;
import com.neviswealth.searchapi.elasticsearch.ElasticsearchIndexService;
import com.neviswealth.searchapi.embedding.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private ElasticsearchIndexService esIndexService;

    @MockBean
    protected EmbeddingClient embeddingClient;

    @MockBean
    protected Summarizer summarizer;

    @BeforeEach
    void resetDataAndMocks() {
        // Clean Postgres
        jdbc.execute("DELETE FROM documents");
        jdbc.execute("DELETE FROM clients");

        // Drop ES indices then recreate with correct mappings
        deleteIndex("clients");
        deleteIndex("documents");
        try {
            esIndexService.recreateIndices();
        } catch (Exception e) {
            throw new RuntimeException("Failed to recreate ES indices in test setup", e);
        }

        // Default stubs
        when(embeddingClient.embed(anyString())).thenReturn(unitVector(0));
        when(summarizer.summarize(anyString(), anyInt())).thenReturn("Test summary.");
    }

    private void deleteIndex(String index) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(index)).value();
            if (exists) {
                esClient.indices().delete(d -> d.index(index));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Force ES to make all pending writes searchable immediately.
     * Call this in tests after creating data and before searching —
     * ES has a ~1s default refresh interval which causes flaky empty results.
     */
    protected void refreshIndices() {
        try {
            esClient.indices().refresh(r -> r.index("clients", "documents"));
        } catch (Exception ignored) {}
    }

    protected static float[] unitVector(int i) {
        float[] v = new float[384];
        v[i] = 1.0f;
        return v;
    }

    protected static float[] blend(int i, float wi, int j, float wj) {
        float[] v = new float[384];
        v[i] = wi;
        v[j] = wj;
        double norm = Math.sqrt(wi * wi + wj * wj);
        v[i] /= (float) norm;
        v[j] /= (float) norm;
        return v;
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}