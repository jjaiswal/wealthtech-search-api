package com.neviswealth.searchapi.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles the pgvector operations that JPA/Hibernate can't map (the {@code vector} type).
 * Uses {@link JdbcTemplate} with a string-cast pattern ({@code ?::vector}) — no
 * pgvector-specific JDBC dependency needed.
 */
@Repository
public class DocumentVectorRepository {

    private final JdbcTemplate jdbc;

    public DocumentVectorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a document with its embedding in a single statement, returning the
     * DB-generated {@code id}, {@code summary}, and {@code created_at}.
     */
    public Document insert(UUID clientId, String title, String content, String summary,
                           float[] embedding) {
        return jdbc.queryForObject(
                """
                INSERT INTO documents (id, client_id, title, content, summary, embedding)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::vector)
                RETURNING id, client_id, title, content, summary, created_at
                """,
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                    String returnedSummary = rs.getString("summary");
                    return Document.persisted(id, clientId, title, content, returnedSummary, createdAt);
                },
                clientId, title, content, summary, VectorLiteral.toPgVector(embedding));
    }

    /**
     * Returns the top-N documents nearest to the query vector (cosine distance, HNSW-indexed).
     *
     * @param clientId if non-null, restrict results to that client; null = search all.
     */
    public List<DocumentMatch> searchByEmbedding(float[] queryEmbedding, UUID clientId, int topN) {
        String q = VectorLiteral.toPgVector(queryEmbedding);

        StringBuilder sql = new StringBuilder("""
                SELECT id, client_id, title, summary, created_at,
                       (embedding <=> ?::vector) AS distance
                FROM documents
                WHERE embedding IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(q);
        if (clientId != null) {
            sql.append("  AND client_id = ?\n");
            args.add(clientId);
        }
        sql.append("ORDER BY distance\nLIMIT ?");
        args.add(topN);

        return jdbc.query(sql.toString(), DOCUMENT_MATCH_MAPPER, args.toArray());
    }

    public List<DocumentForReindex> findAllForReindex() {
        return jdbc.query(
                """
                SELECT id, client_id, title, content, summary, created_at,
                       embedding::text AS embedding_text
                FROM documents
                WHERE embedding IS NOT NULL
                """,
                (rs, rowNum) -> new DocumentForReindex(
                        rs.getObject("id", UUID.class),
                        rs.getObject("client_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("summary"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        VectorLiteral.fromPgVector(rs.getString("embedding_text"))
                )
        );
    }

    private static final RowMapper<DocumentMatch> DOCUMENT_MATCH_MAPPER = (rs, rowNum) ->
            new DocumentMatch(
                    rs.getObject("id", UUID.class),
                    rs.getObject("client_id", UUID.class),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getDouble("distance"));
}