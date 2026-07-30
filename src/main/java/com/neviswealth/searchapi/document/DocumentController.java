package com.neviswealth.searchapi.document;

import com.neviswealth.searchapi.document.dto.CreateDocumentRequest;
import com.neviswealth.searchapi.document.dto.DocumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/clients/{clientId}/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@PathVariable UUID clientId,
                                                   @Valid @RequestBody CreateDocumentRequest request,
                                                   UriComponentsBuilder uri) {
        Document created = service.create(clientId, request);
        URI location = uri.path("/documents/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(DocumentResponse.from(created));
    }
}
