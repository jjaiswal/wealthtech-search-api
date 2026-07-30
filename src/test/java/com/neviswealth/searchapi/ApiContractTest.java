package com.neviswealth.searchapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies REST behaviour and correct HTTP status codes (the spec requires proper codes),
 * plus edge cases (blank query, no matches, duplicate email, missing client).
 */
class ApiContractTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    // ---- create client -------------------------------------------------------------------

    @Test
    void createClient_returns201() {
        ResponseEntity<Map> resp = postClient(Map.of(
                "first_name", "John", "last_name", "Doe", "email", "john@example.com"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat(resp.getBody().get("created_at")).isNotNull();
    }

    @Test
    void createClient_missingRequiredFields_returns400() {
        ResponseEntity<Map> resp = postClient(Map.of("first_name", "OnlyFirst"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createClient_invalidEmail_returns400() {
        ResponseEntity<Map> resp = postClient(Map.of(
                "first_name", "A", "last_name", "B", "email", "not-an-email"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createClient_duplicateEmail_returns409() {
        Map<String, Object> req = Map.of("first_name", "A", "last_name", "B", "email", "dup@example.com");
        assertThat(postClient(req).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postClient(req).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- create document -----------------------------------------------------------------

    @Test
    void createDocument_forMissingClient_returns404() {
        String fakeClient = "00000000-0000-0000-0000-000000000000";
        ResponseEntity<Map> resp = rest.postForEntity(
                baseUrl() + "/clients/{id}/documents",
                json(Map.of("title", "t", "content", "c")), Map.class, fakeClient);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createDocument_missingContent_returns400() {
        String clientId = (String) postClient(Map.of(
                "first_name", "A", "last_name", "B", "email", "doc@example.com")).getBody().get("id");
        ResponseEntity<Map> resp = rest.postForEntity(
                baseUrl() + "/clients/{id}/documents",
                json(Map.of("title", "only title")), Map.class, clientId);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- search edge cases ---------------------------------------------------------------

    @Test
    void search_blankQuery_returns400() {
        ResponseEntity<Map> resp = rest.getForEntity(baseUrl() + "/search?q=", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_missingQueryParam_returns400() {
        ResponseEntity<Map> resp = rest.getForEntity(baseUrl() + "/search", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_noMatches_returns200WithEmptyArray() {
        ResponseEntity<List> resp = rest.getForEntity(baseUrl() + "/search?q=zzznomatch", List.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    void search_likeWildcard_isMatchedLiterally_notAsAWildcard() {
        // A bare '%' must NOT behave as a SQL LIKE wildcard matching every client.
        postClient(Map.of("first_name", "A", "last_name", "B", "email", "a@example.com"));
        postClient(Map.of("first_name", "C", "last_name", "D", "email", "c@example.com"));

        ResponseEntity<List> resp = rest.getForEntity(baseUrl() + "/search?q=%25", List.class); // %25 = '%'
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // No client contains a literal '%', so escaping means zero matches (not "all clients").
        assertThat(resp.getBody()).isEmpty();
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResponseEntity<Map> postClient(Map<String, Object> body) {
        return rest.postForEntity(baseUrl() + "/clients", json(new HashMap<>(body)), Map.class);
    }

    private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, h);
    }
}
