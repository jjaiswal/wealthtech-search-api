package com.neviswealth.searchapi.config;

import com.neviswealth.searchapi.document.summary.LlmSummarizer;
import com.neviswealth.searchapi.document.summary.Summarizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SummarizerConfig {

    @Bean
    public Summarizer summarizer(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:qwen2.5:1.5b}") String model) {
        return new LlmSummarizer(baseUrl, model);
    }
}
