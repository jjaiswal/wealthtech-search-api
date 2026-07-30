# WealthTech Search API

A search API over **clients** and **documents** for a WealthTech advisor platform. It supports two kinds of search, deliberately using the right technique for each:  


1. **Client search** — find clients by matches in their name, email, or description. Case-insensitive, fuzzy, and prefix matching (e.g. `NevisWealth` → `john.doe@neviswealth.com`).
2. **Document search** — find documents by *semantically similar* content, even with no shared words (e.g. `address proof` → a `utility bill` document). Embedding + vector search.
3. **Document summary** — each document in the search results includes a short **LLM-generated summary** of its content (produced at ingest by a local Ollama model — no external API key).

  


> **Design rationale, tradeoffs, and architecture: DESIGN.md.** **A guided, copy-pasteable demo of every endpoint: docs/DEMO.md.**

---

## Tech stack

- **API:** Java 21, Spring Boot 3.3, Maven
- **Database:** PostgreSQL 16 + `pgvector` (stores document embeddings; write store and source of truth)
- **Search:** Elasticsearch 8.14 — client lexical search (`multi_match` + `matchPhrasePrefix` + `wildcard`) and document hybrid search (BM25 + KNN)
- **Embeddings:** `all-MiniLM-L6-v2` (384-dim) served by a small Python sidecar — no API keys, runs fully offline
- **Summarization:** local LLM ([Ollama](https://ollama.com/) + `qwen2.5:1.5b`) generates fluent document summaries at ingest — no external API key
- **API docs:** OpenAPI / Swagger UI (springdoc)
- **Tests:** JUnit 5, real Postgres + real Elasticsearch (no Testcontainers); embedder + summarizer mocked

Everything runs as five containers via Docker Compose (or Podman Compose).  


---

## Running it

**Prerequisites:** Docker with Docker Compose, or Podman with `podman compose`.  


```
# Docker
docker compose up --build

# Podman
podman compose up --build
```

This starts five services and waits for all dependencies to be healthy before the API starts:  



|                 |       |                                      |
| --------------- | ----- | ------------------------------------ |
| Service         | Port  | Role                                 |
| `api`           | 8080  | the REST API                         |
| `elasticsearch` | 9200  | search layer (lexical + semantic)    |
| `embedder`      | 8000  | embedding model sidecar              |
| `ollama`        | 11434 | local LLM for document summarization |
| `db`            | 5432  | PostgreSQL + pgvector                |


On first startup the API **seeds a few sample clients and documents** (only if the database is empty), so search works immediately — including the two examples below.  


- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs (also exported to `docs/openapi.json`)

---

## Example queries and responses

### 1. Client search — `NevisWealth` finds the client

```
curl "http://localhost:8080/search?q=NevisWealth"
```

```
[
  {
    "type": "client",
    "entity": {
      "id": "c0ab9789-70bc-49db-9c1e-24518fc68781",
      "first_name": "John",
      "last_name": "Doe",
      "email": "john.doe@neviswealth.com",
      "description": "Long-standing advisory client; retirement and estate planning.",
      "social_links": [],
      "created_at": "2026-07-30T13:21:14.314027Z"
    }
  }
]
```

`NevisWealth` matches inside the email `john.doe@neviswealth.com` via Elasticsearch wildcard search — case-insensitive, no exact word match needed.  


### 2. Document search — `address proof` finds the utility bill

```
curl "http://localhost:8080/search?q=address%20proof"
```

```
[
  {
    "type": "document",
    "score": 4.59,
    "entity": {
      "id": "68d79c2d-d9c4-4af1-b6d4-481c1bc4c708",
      "client_id": "c0ab9789-70bc-49db-9c1e-24518fc68781",
      "title": "Electricity utility bill — March 2026",
      "summary": "John Doe's monthly electricity utility bill totals $84.20, with the service located at 123 Main Street in Springfield. The amount is valid for use as proof of residency and address verification.",
      "created_at": "2026-07-30T13:21:17.299215Z"
    }
  },
  {
    "type": "document",
    "score": 2.29,
    "entity": {
      "title": "Council tax bill",
      "summary": "The annual council tax bill confirms the residence address for the tax year and serves as a reminder to pay the amount due.",
      "...": "..."
    }
  },
  {
    "type": "document",
    "score": 0.59,
    "entity": { "title": "Bank statement — February 2026", "...": "..." }
  },
  {
    "type": "document",
    "score": 0.59,
    "entity": { "title": "Passport copy", "...": "..." }
  }
]
```

`address proof` shares no words with `utility bill`, yet both genuine proof-of-address documents (utility bill, council tax) rank at the top — semantic ranking, not keyword matching. Each document hit carries an inline LLM-generated `summary` (computed at ingest). Scores are Elasticsearch hybrid scores (BM25 + KNN) — they rank documents relative to each other, not on an absolute 0–1 scale.  


### 3. Create a client

```
curl -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{
    "first_name": "Jane",
    "last_name": "Smith",
    "email": "jane.smith@example.com",
    "description": "New client onboarding",
    "social_links": []
  }'
```

Returns `201 Created` with the new client (including a generated `id` and `created_at`).  


### 4. Add a document (embedded on ingest)

```
curl -X POST "http://localhost:8080/clients/{clientId}/documents" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Electricity utility bill",
    "content": "Monthly electricity bill. Service address 123 Main Street. Proof of residence."
  }'
```

Returns `201`. The content is embedded at ingest so search stays fast at read time.  


### 5. Scope document search to one client *(optional)*

```
curl "http://localhost:8080/search?q=address%20proof&client_id={clientId}"
```

`client_id` is optional. Omitted → global search (the default). Present → document search is restricted to that client. See DESIGN.md §7.  


---

## API summary


|        |                           |                                                                                                     |               |
| ------ | ------------------------- | --------------------------------------------------------------------------------------------------- | ------------- |
| Method | Path                      | Description                                                                                         | Codes         |
| POST   | `/clients`                | Create a client                                                                                     | 201, 400, 409 |
| POST   | `/clients/{id}/documents` | Add a document to a client (embedded on ingest)                                                     | 201, 400, 404 |
| GET    | `/search?q=&client_id=`   | Search clients and documents (flat array of typed results; document hits include an inline summary) | 200, 400      |


Full, interactive contract: **Swagger UI** (above). `/search` returns a **flat array** of typed items (`{ type, score, entity }`) — clients first, then documents by score. See DESIGN.md §D6 for the ranking rationale.  


---

## Testing

```
./mvnw test
```

Integration tests run against a **real Postgres + real Elasticsearch** instance (no Testcontainers); the embedder and summarizer are mocked for speed and determinism. Tests cover the two spec examples end-to-end, HTTP status codes, edge cases (blank/missing query, invalid email, duplicate email, missing client), and the semantic relevance score floor. See DESIGN.md §6.  


---

## What I'd do differently / next steps

A fuller list is in DESIGN.md §7. Highlights:  


- **Embedding model:** swap the local model for a hosted API (e.g. OpenAI) behind the existing `EmbeddingClient` interface — a one-line change, key injected via env.
- **Scale:** benchmarked at 100k documents — client lexical search **19 ms**, document semantic search **53 ms** end-to-end. Beyond this scale: batch/async ingestion, result pagination, HNSW parameter tuning.
- **Search enhancements:** per-field boost tuning on real query logs, phonetic analysis, cross-encoder reranking.
- **Ops:** authentication, rate limiting, metrics, schema migrations (Flyway).

---

## Running behind a corporate proxy

If image builds fail with certificate errors behind a TLS-inspecting proxy (e.g. Zscaler), place the proxy's root CA as a `*.crt` file in the project root (and in `embedder/`), then:  


```
TRUST_LOCAL_CA=1 docker compose up --build
# or
TRUST_LOCAL_CA=1 podman compose up --build
```

Without the flag (the default) builds use the standard trust store. The `*.crt` files are git-ignored and never committed.  


---

## Scale benchmark

Bulk-inserts synthetic data directly into Postgres and measures end-to-end search latency via the API:  


```
./dev/benchmark.sh              # default: 1,000 clients × 100 docs = 100k documents
./dev/benchmark.sh 5000 200     # custom:  5,000 clients × 200 docs = 1M documents
```

At 100k documents: **19 ms** average for client lexical search, **53 ms** for document semantic search (including embedder round-trip) — both measured against the live Elasticsearch-backed API.