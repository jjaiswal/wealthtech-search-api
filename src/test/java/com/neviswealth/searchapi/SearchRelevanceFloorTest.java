package com.neviswealth.searchapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the document relevance floor at its production default (DESIGN §D6a):
 * {@code search.min-score=0.58}. Documents whose hybrid score is at or above 0.58
 * are kept; those below are dropped. Mock embeddings use orthogonal unit vectors so
 * the relevant doc scores near 1.0 (identical direction to query) and the noise doc
 * scores near 0.5 (orthogonal — KNN contributes ~0, hybrid stays low), cleanly
 * straddling the floor regardless of BM25 variance.
 */
@TestPropertySource(properties = "search.min-score=0.58")
class SearchRelevanceFloorTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void defaultFloorKeepsGenuineMatchesAndDropsNoise() {
        String clientId = createClient("Jane", "Roe", "jane@example.com");

        // Query and relevant doc share axis 0 → cosine similarity = 1.0 → high hybrid score
        // Noise doc points along axis 1 → cosine similarity = 0.0 → low hybrid score
        when(embeddingClient.embed(eq("axis zero query"))).thenReturn(unitVector(0));
        when(embeddingClient.embed(eq("relevant"))).thenReturn(unitVector(0));
        when(embeddingClient.embed(eq("noise"))).thenReturn(unitVector(1));
        createDocument(clientId, "Relevant", "relevant");
        createDocument(clientId, "Noise", "noise");
        refreshIndices();

        List<Map<String, Object>> docs = hitsOfType(search("axis zero query"), "document");

        assertThat(docs).hasSize(1);
        assertThat(entity(docs.get(0)).get("title")).isEqualTo("Relevant");
        assertThat((Double) docs.get(0).get("score")).isGreaterThanOrEqualTo(0.58);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entity(Map<String, Object> hit) {
        return (Map<String, Object>) hit.get("entity");
    }

    private List<Map<String, Object>> hitsOfType(List<Map<String, Object>> hits, String type) {
        return hits.stream().filter(h -> type.equals(h.get("type"))).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> search(String q) {
        ResponseEntity<List> resp = rest.getForEntity(baseUrl() + "/search?q={q}", List.class, q);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private String createClient(String first, String last, String email) {
        ResponseEntity<Map> resp = rest.postForEntity(baseUrl() + "/clients",
                json(Map.of("first_name", first, "last_name", last, "email", email)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private void createDocument(String clientId, String title, String content) {
        ResponseEntity<Map> resp = rest.postForEntity(baseUrl() + "/clients/{id}/documents",
                json(Map.of("title", title, "content", content)), Map.class, clientId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }
}