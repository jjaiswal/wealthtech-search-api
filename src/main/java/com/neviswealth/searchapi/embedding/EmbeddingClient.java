package com.neviswealth.searchapi.embedding;

/**
 * Converts text into an embedding vector. The default implementation calls the local Python
 * sidecar; can be swapped for a hosted API (e.g. OpenAI) without touching the rest of the app.
 */
public interface EmbeddingClient {

    /**
     * @param text input text (document content on ingest, or a query at search time)
     * @return the embedding as a float array (length must match the DB vector dimension, 384)
     */
    float[] embed(String text);
}
