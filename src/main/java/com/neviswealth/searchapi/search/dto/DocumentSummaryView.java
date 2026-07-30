package com.neviswealth.searchapi.search.dto;

import com.neviswealth.searchapi.document.DocumentMatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Document fields returned in search results — includes the precomputed summary, omits full content. */
public record DocumentSummaryView(
        UUID id,
        UUID clientId,
        String title,
        String summary,
        OffsetDateTime createdAt
) implements SearchEntity {
    public static DocumentSummaryView from(DocumentMatch m) {
        return new DocumentSummaryView(m.id(), m.clientId(), m.title(), m.summary(), m.createdAt());
    }
}
