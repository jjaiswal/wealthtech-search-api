package com.neviswealth.searchapi.client;

import com.neviswealth.searchapi.client.dto.CreateClientRequest;
import com.neviswealth.searchapi.common.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.neviswealth.searchapi.elasticsearch.ElasticsearchSyncService;

import java.util.Map;

import static org.hibernate.internal.util.collections.CollectionHelper.toMap;

@Service
public class ClientService {

    private final ClientRepository clients;
    private final ElasticsearchSyncService esSyncService;

    public ClientService(ClientRepository clients, ElasticsearchSyncService esSyncService) {

        this.clients = clients;
        this.esSyncService = esSyncService;
    }

    @Transactional
    public Client create(CreateClientRequest req) {
        // Early duplicate check; the DB unique constraint is the final safeguard for races.
        if (clients.existsByEmail(req.email())) {
            throw new ConflictException("A client with email '" + req.email() + "' already exists");
        }
        Client client = new Client(
                req.firstName(), req.lastName(), req.email(),
                req.description(), req.socialLinks());
        try {
            Client saved = clients.save(client);
            esSyncService.indexClient(toMap(saved));   // ← sync to ES after save
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A client with email '" + req.email() + "' already exists");
        }
    }
    private Map<String, Object> toMap(Client c) {
        return Map.of(
                "id",           c.getId().toString(),
                "first_name",   c.getFirstName(),
                "last_name",    c.getLastName(),
                "email",        c.getEmail(),
                "description",  c.getDescription() != null ? c.getDescription() : "",
                "social_links", c.getSocialLinks() != null ? c.getSocialLinks() : java.util.List.of()
        );
    }
}
