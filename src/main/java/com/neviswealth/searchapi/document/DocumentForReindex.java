package com.neviswealth.searchapi.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentForReindex(
        UUID id,
        UUID clientId,
        String title,
        String content,
        String summary,
        OffsetDateTime createdAt,
        float[] embedding
) {}
