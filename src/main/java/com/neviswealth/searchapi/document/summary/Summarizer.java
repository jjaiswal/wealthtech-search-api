package com.neviswealth.searchapi.document.summary;

/**
 * Produces a short summary of document content. The implementation uses a local LLM
 * (Ollama) to generate fluent, condensed summaries at ingest time.
 */
public interface Summarizer {

    /**
     * @param content      the document text
     * @param maxSentences maximum number of sentences to return
     * @return a short summary of {@code content}
     */
    String summarize(String content, int maxSentences);
}
