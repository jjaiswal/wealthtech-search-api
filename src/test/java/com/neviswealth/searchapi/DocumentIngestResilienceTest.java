package com.neviswealth.searchapi;

import com.neviswealth.searchapi.document.summary.SummarizationException;
import com.neviswealth.searchapi.embedding.EmbeddingException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Ingest degrades by dependency criticality (DESIGN §7 / §9):
 * <ul>
 *   <li><b>Embedding is required</b> — without a vector the document is unsearchable, a silent
 *       data hole, so an embedder failure fails the write (503) and persists nothing.</li>
 *   <li><b>Summary is enrichment (R3)</b> — its absence breaks nothing, so a summarizer failure
 *       still stores the document with a {@code null} summary rather than failing ingest.</li>
 * </ul>
 * These assert the asymmetry directly, so a later "wrap everything in try/catch" change that
 * silently created unsearchable documents would fail here.
 */
class DocumentIngestResilienceTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void summarizerDown_documentIsStillCreatedWithoutASummary() {
        when(summarizer.summarize(anyString(), anyInt()))
                .thenThrow(new SummarizationException("Ollama unavailable"));
        String clientId = createClient("Sam", "Text", "sam@example.com");

        ResponseEntity<Map> resp = postDocument(clientId, "Utility bill", "electricity bill content");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Persisted with no summary.
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE summary IS NULL", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void embedderDown_ingestFailsWith503AndPersistsNothing() {
        when(embeddingClient.embed(anyString()))
                .thenThrow(new EmbeddingException("embedder unavailable"));
        String clientId = createClient("Ed", "Bedder", "ed@example.com");

        ResponseEntity<Map> resp = postDocument(clientId, "Utility bill", "electricity bill content");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        // No unsearchable document left behind.
        Integer count = jdbc.queryForObject("SELECT count(*) FROM documents", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    // ---- helpers -------------------------------------------------------------------------

    private String createClient(String first, String last, String email) {
        ResponseEntity<Map> resp = rest.postForEntity(baseUrl() + "/clients",
                json(Map.of("first_name", first, "last_name", last, "email", email)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private ResponseEntity<Map> postDocument(String clientId, String title, String content) {
        return rest.postForEntity(baseUrl() + "/clients/{id}/documents",
                json(Map.of("title", title, "content", content)), Map.class, clientId);
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }
}
