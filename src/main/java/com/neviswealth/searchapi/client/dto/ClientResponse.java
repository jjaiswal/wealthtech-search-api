package com.neviswealth.searchapi.client.dto;

import com.neviswealth.searchapi.client.Client;
import com.neviswealth.searchapi.search.dto.SearchEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API representation of a client returned in responses and as the entity of client search hits. */
public record ClientResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String description,
        List<String> socialLinks,
        OffsetDateTime createdAt
) implements SearchEntity {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
                c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getDescription(), c.getSocialLinks(), c.getCreatedAt());
    }
}
