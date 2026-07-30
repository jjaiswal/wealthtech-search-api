package com.neviswealth.searchapi.document.dto;

import com.neviswealth.searchapi.document.Document;

import java.time.OffsetDateTime;
import java.util.UUID;

/** API representation of a document (excludes the internal embedding vector). */
public record DocumentResponse(
        UUID id,
        UUID clientId,
        String title,
        String content,
        OffsetDateTime createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(), d.getClientId(), d.getTitle(), d.getContent(), d.getCreatedAt());
    }
}
