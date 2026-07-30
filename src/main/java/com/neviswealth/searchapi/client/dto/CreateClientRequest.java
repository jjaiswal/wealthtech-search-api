package com.neviswealth.searchapi.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Request body for POST /clients. */
public record CreateClientRequest(
        @NotBlank(message = "first_name is required")
        String firstName,

        @NotBlank(message = "last_name is required")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        String description,

        List<String> socialLinks
) {}
