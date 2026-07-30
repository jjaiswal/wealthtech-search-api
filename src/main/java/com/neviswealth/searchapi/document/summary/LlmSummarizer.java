package com.neviswealth.searchapi.document.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * Summarizer backed by a local Ollama instance (e.g. qwen2.5:1.5b). Runs as a container
 * alongside the API — no external API key needed. Throws {@link SummarizationException}
 * on any failure (timeout, empty response, etc.).
 */
public class LlmSummarizer implements Summarizer {

    private static final Logger log = LoggerFactory.getLogger(LlmSummarizer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String model;

    public LlmSummarizer(String baseUrl, String model) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String summarize(String content, int maxSentences) {
        String prompt = buildPrompt(content, maxSentences);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "prompt", prompt,
                            "stream", false,
                            "options", Map.of("temperature", 0.1, "num_predict", 200)
                    ))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("Ollama call failed (model={}, content length={}): {}",
                    model, content.length(), e.getMessage());
            throw new SummarizationException(
                    "Summarization service is unavailable: " + e.getMessage(), e);
        }

        if (response == null) {
            log.error("Ollama returned null response (model={})", model);
            throw new SummarizationException("Summarization service returned no response");
        }

        String result = (String) response.get("response");
        if (result == null || result.isBlank()) {
            log.warn("Ollama returned empty summary (model={}, content length={})",
                    model, content.length());
            throw new SummarizationException("Summarization service returned an empty summary");
        }
        return result.strip();
    }

    private String buildPrompt(String content, int maxSentences) {
        return """
                You are a financial document summarizer for a wealth management platform.

                Rules:
                - Write at most %d sentences.
                - Cover the key facts: who, what, amounts, dates, purpose.
                - Use only information explicitly stated in the document — do not infer or invent.
                - Do not include greetings, labels, or bullet points.
                - Write in third person, present tense.

                Document:
                \"\"\"
                %s
                \"\"\"

                Summary:""".formatted(maxSentences, content);
    }
}
