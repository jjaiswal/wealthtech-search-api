package com.neviswealth.searchapi.search;

import com.neviswealth.searchapi.search.dto.SearchHit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * @param q        search text (required; blank → 400; max 500 chars)
     * @param clientId optional filter to scope document results to one client
     */
    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam
            @NotBlank(message = "q is required")
            @Size(max = 500, message = "q must not exceed 500 characters")
            String q,
            @RequestParam(name = "client_id", required = false) UUID clientId) {
        return searchService.search(q, clientId);
    }
}
