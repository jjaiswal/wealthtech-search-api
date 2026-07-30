package com.neviswealth.searchapi.document;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A document row from a semantic search query, with its cosine distance to the query vector. */
public record DocumentMatch(
        UUID id,
        UUID clientId,
        String title,
        String summary,
        OffsetDateTime createdAt,
        double distance
) {}
