package com.neviswealth.searchapi.client;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A client record. Maps to the {@code clients} table.
 * here and never exposed via the API.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "social_links", columnDefinition = "text[]")
    private List<String> socialLinks;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Client() {
        // for JPA
    }

    public Client(String firstName, String lastName, String email,
                  String description, List<String> socialLinks) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.description = description;
        this.socialLinks = socialLinks;
    }

    public UUID getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getDescription() { return description; }
    public List<String> getSocialLinks() { return socialLinks; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
