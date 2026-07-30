package com.neviswealth.searchapi.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Persistence layer for {@link Client} entities.
 *
 * <p>Text search is handled by Elasticsearch ({@code ElasticsearchSyncService#searchClients}),
 * This repository is used only for CRUD operations and existence checks.
 */
public interface ClientRepository extends JpaRepository<Client, UUID> {

    boolean existsByEmail(String email);
}