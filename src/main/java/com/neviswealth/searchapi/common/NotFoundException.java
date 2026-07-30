package com.neviswealth.searchapi.common;

/** Thrown when a referenced resource does not exist (e.g. client for a document) → HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
