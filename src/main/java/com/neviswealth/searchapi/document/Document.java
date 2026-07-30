package com.neviswealth.searchapi.document;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A document belonging to a client. Maps to the {@code documents} table.
 *
 * <p>The {@code embedding vector(384)} column is not mapped here — pgvector has no standard
 * JDBC type, so embeddings are handled via {@link com.neviswealth.searchapi.document.DocumentVectorRepository}.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String content;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column                                          // ← add this
    private String summary;

    protected Document() {
        // for JPA
    }

    public Document(UUID clientId, String title, String content) {
        this.clientId = clientId;
        this.title = title;
        this.content = content;
    }

    /** Reconstruct an entity from a JDBC-inserted row (used by DocumentVectorRepository). */
    public static Document persisted(UUID id, UUID clientId, String title, String content,
                                     String summary, OffsetDateTime createdAt) {
        Document d = new Document(clientId, title, content);
        d.id = id;
        d.summary = summary;
        d.createdAt = createdAt;
        return d;
    }

    public UUID getId() { return id; }
    public UUID getClientId() { return clientId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
