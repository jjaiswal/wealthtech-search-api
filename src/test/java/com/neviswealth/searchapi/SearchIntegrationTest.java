package com.neviswealth.searchapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void findsClientBySubstringOfEmail_caseInsensitive() {
        createClient("John", "Doe", "john.doe@neviswealth.com", null);
        refreshIndices();

        List<Map<String, Object>> clients = hitsOfType(search("NevisWealth", null), "client");

        assertThat(clients).hasSize(1);
        assertThat(entity(clients.get(0)).get("email")).isEqualTo("john.doe@neviswealth.com");
    }

    @Test
    void findsClientByNameSubstring() {
        createClient("Alice", "Wonderland", "alice@example.com", null);
        refreshIndices();

        assertThat(hitsOfType(search("wonder", null), "client")).hasSize(1);
    }

    @Test
    void ranksSemanticallyClosestDocumentFirst() {
        String clientId = createClient("Jane", "Roe", "jane@example.com", null);

        stubEmbedding("utility bill for electricity, proof of residence", unitVector(0));
        stubEmbedding("quarterly investment portfolio and dividends",     unitVector(1));
        createDocument(clientId, "Utility bill", "utility bill for electricity, proof of residence");
        createDocument(clientId, "Portfolio",    "quarterly investment portfolio and dividends");

        stubEmbedding("address proof", blend(0, 0.9f, 1, 0.1f));
        refreshIndices();

        List<Map<String, Object>> docs = hitsOfType(search("address proof", null), "document");

        assertThat(docs).hasSize(2);
        assertThat(entity(docs.get(0)).get("title")).isEqualTo("Utility bill");
        assertThat(entity(docs.get(1)).get("title")).isEqualTo("Portfolio");
    }

    @Test
    void documentSearchCanBeScopedToOneClient() {
        String a = createClient("A", "A", "a@example.com", null);
        String b = createClient("B", "B", "b@example.com", null);
        createDocument(a, "Doc A", "content a");
        createDocument(b, "Doc B", "content b");
        refreshIndices();

        assertThat(hitsOfType(search("anything", null), "document")).hasSize(2);

        List<Map<String, Object>> scoped = hitsOfType(search("anything", a), "document");
        assertThat(scoped).hasSize(1);
        assertThat(entity(scoped.get(0)).get("title")).isEqualTo("Doc A");
    }

    @Test
    void documentResultsIncludeAnInlineSummary() {
        String clientId = createClient("Sam", "Text", "sam@example.com", null);
        String content = "This is an electricity utility bill. The bill covers March usage.";
        when(summarizer.summarize(eq(content), anyInt())).thenReturn("Electricity bill for March.");
        createDocument(clientId, "Utility bill", content);
        refreshIndices();

        List<Map<String, Object>> docs = hitsOfType(search("anything", null), "document");
        assertThat(docs).hasSize(1);
        String summary = (String) entity(docs.get(0)).get("summary");
        assertThat(summary).isEqualTo("Electricity bill for March.");
    }

    @Test
    void resultsAreClientsFirstThenDocuments() {
        createClient("Zed", "Zaddress", "zed@example.com", "address proof related");
        String clientId = createClient("Doc", "Owner", "owner@example.com", null);
        createDocument(clientId, "Some doc", "address proof related content");
        refreshIndices();

        List<Map<String, Object>> hits = search("address", null);

        assertThat(hits.get(0).get("type")).isEqualTo("client");
        assertThat(hits.get(hits.size() - 1).get("type")).isEqualTo("document");
        assertThat(hits.get(0)).doesNotContainKey("score");
        assertThat(hits.get(hits.size() - 1)).containsKey("score");
    }

    // ---- helpers -------------------------------------------------------------------------

    private void stubEmbedding(String text, float[] vector) {
        when(embeddingClient.embed(eq(text))).thenReturn(vector);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entity(Map<String, Object> hit) {
        return (Map<String, Object>) hit.get("entity");
    }

    private List<Map<String, Object>> hitsOfType(List<Map<String, Object>> hits, String type) {
        return hits.stream().filter(h -> type.equals(h.get("type"))).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> search(String q, String clientId) {
        String url = baseUrl() + "/search?q={q}" + (clientId != null ? "&client_id={c}" : "");
        ResponseEntity<List> resp = clientId != null
                ? rest.getForEntity(url, List.class, q, clientId)
                : rest.getForEntity(url, List.class, q);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private String createClient(String first, String last, String email, String description) {
        Map<String, Object> req = Map.of(
                "first_name", first, "last_name", last, "email", email,
                "description", description == null ? "" : description);
        ResponseEntity<Map> resp = rest.postForEntity(baseUrl() + "/clients", json(req), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private void createDocument(String clientId, String title, String content) {
        Map<String, Object> req = Map.of("title", title, "content", content);
        ResponseEntity<Map> resp = rest.postForEntity(
                baseUrl() + "/clients/{id}/documents", json(req), Map.class, clientId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }
}