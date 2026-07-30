package com.neviswealth.searchapi.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /clients/{id}/documents. */
public record CreateDocumentRequest(
        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "content is required")
        @Size(max = MAX_CONTENT_CHARS,
              message = "content exceeds the maximum of " + MAX_CONTENT_CHARS + " characters")
        String content
) {
    public static final int MAX_CONTENT_CHARS = 50_000;
}
