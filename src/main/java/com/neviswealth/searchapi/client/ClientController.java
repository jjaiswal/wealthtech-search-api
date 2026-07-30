package com.neviswealth.searchapi.client;

import com.neviswealth.searchapi.client.dto.ClientResponse;
import com.neviswealth.searchapi.client.dto.CreateClientRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    @Operation(summary = "Create a client")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Client created"),
            @ApiResponse(responseCode = "400", description = "Validation error (missing/invalid fields)"),
            @ApiResponse(responseCode = "409", description = "A client with that email already exists")
    })
    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody CreateClientRequest request,
                                                 UriComponentsBuilder uri) {
        Client created = service.create(request);
        URI location = uri.path("/clients/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(ClientResponse.from(created));
    }
}
