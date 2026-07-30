package com.neviswealth.searchapi.document.summary;

/** Raised when the summarization service cannot produce a summary (timeout, empty response, etc.). */
public class SummarizationException extends RuntimeException {
    public SummarizationException(String message) {
        super(message);
    }

    public SummarizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
