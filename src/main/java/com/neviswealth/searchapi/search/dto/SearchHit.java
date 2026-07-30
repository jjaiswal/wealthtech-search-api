package com.neviswealth.searchapi.search.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neviswealth.searchapi.client.dto.ClientResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single search result. Document hits include a cosine-similarity {@code score};
 * client hits are lexical matches and have no score (field is omitted in the JSON).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchHit(
        @Schema(description = "Result type", allowableValues = {"client", "document"})
        String type,

        @Schema(description = "Relevance (cosine) score — present for document hits only")
        Double score,

        @Schema(description = "The matched resource",
                oneOf = {ClientResponse.class, DocumentSummaryView.class})
        SearchEntity entity
) {
    public static SearchHit client(SearchEntity entity) {
        return new SearchHit("client", null, entity);
    }

    public static SearchHit document(double score, SearchEntity entity) {
        return new SearchHit("document", score, entity);
    }
}
