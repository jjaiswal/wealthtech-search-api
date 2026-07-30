package com.neviswealth.searchapi.embedding;

/** Raised when the embedding service cannot produce an embedding. */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
