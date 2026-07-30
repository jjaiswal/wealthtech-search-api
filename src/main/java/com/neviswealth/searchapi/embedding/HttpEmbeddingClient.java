package com.neviswealth.searchapi.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;

/**
 * Calls the local Python embedder sidecar over HTTP. Configured via {@code embedder.base-url}
 * and {@code embedder.timeout-ms}. Failures surface as {@link EmbeddingException} (→ 503).
 */
@Component
public class HttpEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;

    public HttpEmbeddingClient(@Value("${embedder.base-url}") String baseUrl,
                               @Value("${embedder.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private record EmbedRequest(String text) {}
    private record EmbedResponse(List<Float> embedding) {}

    @Override
    public float[] embed(String text) {
        EmbedResponse response;
        try {
            response = restClient.post()
                    .uri("/embed")
                    .body(new EmbedRequest(text))
                    .retrieve()
                    .body(EmbedResponse.class);
        } catch (RestClientException e) {
            throw new EmbeddingException("Embedding service is unavailable", e);
        }

        if (response == null || response.embedding() == null) {
            throw new EmbeddingException("Embedder returned no embedding for the given text");
        }
        List<Float> values = response.embedding();
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
