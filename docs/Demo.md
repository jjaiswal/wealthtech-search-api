# WealthTech Search API — Interview Demo

## 🚀 Quick Context

- **Task:** WealthTech Search API — search over clients & documents
- **Stack:** Java 21 · Spring Boot 3.3.2 · PostgreSQL (write store) · Elasticsearch (search layer) · Python embedder (`all-MiniLM-L6-v2`, 384-dim)
- **Architecture:** Postgres = source of truth · ES = search only · sync on every write

---

## 1. Start the Stack

```bash
podman compose up --build
```

Services that come up:

| Service | Description |
|---|---|
| `wealthtech-search-api-db-1` | Postgres (user: `nevis`, db: `nevis`) |
| `wealthtech-search-api-elasticsearch-1` | Elasticsearch on `:9200` |
| `wealthtech-search-api-api-1` | Spring Boot API on `:8080` |
| embedder | Python sidecar on `:8000` |

> ⚠️ ES takes ~30s to be ready — the API retries on startup automatically.

---

## 2. Create a Client

```bash
curl -s -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{"first_name":"John","last_name":"Doe","email":"john.doe@example.com"}' | jq
```

Expected response (`201 Created`):

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "first_name": "John",
  "last_name": "Doe",
  "email": "john.doe@example.com",
  "created_at": "2026-07-30T15:00:00Z"
}
```

> Save the `id` UUID — you'll need it for the next steps.

```bash
CLIENT_ID="<id from above>"
```

---

## 3. Add Documents to the Client

```bash
curl -s -X POST http://localhost:8080/clients/$CLIENT_ID/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"Passport","content":"Passport issued 2021. Document number AB123456."}' | jq
```

```bash
curl -s -X POST http://localhost:8080/clients/$CLIENT_ID/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"HSBC Bank Statement","content":"HSBC bank statement March 2024. Account number ending 4321."}' | jq
```

```bash
curl -s -X POST http://localhost:8080/clients/$CLIENT_ID/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"Electricity Bill","content":"Electricity bill British Gas April 2024. Service address 123 Main Street."}' | jq
```

Expected response (`201 Created`):

```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "client_id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Electricity Bill",
  "content": "Electricity bill British Gas April 2024...",
  "created_at": "2026-07-30T15:01:00Z"
}
```

---

## 4. Search — Lexical (Client Name)

```bash
curl -s "http://localhost:8080/search?q=NevisWealth" | jq
```

> Returns a `SearchHit` with `type: "client"` and the full client entity (`first_name`, `last_name`, `email`)

---

## 5. Search — Semantic (Document Content)

```bash
curl -s "http://localhost:8080/search?q=address+proof" | jq
```

Returns `SearchHit` array ranked by **hybrid BM25 + KNN** score:

| Document | Score |
|---|---|
| Electricity Bill | ~4.59 |
| Bank Statement | ~2.29 |
| Passport | ~0.59 |

```bash
curl -s "http://localhost:8080/search?q=identity+document" | jq
```

> Passport surfaces via **semantic similarity** even though "identity" isn't in the content — KNN matches on vector proximity

Search scoped to one client (optional):

```bash
curl -s "http://localhost:8080/search?q=passport&client_id=$CLIENT_ID" | jq
```

---

## 6. Score Filtering

- `search.min-score: 0.58` set in `application.yml`
- Hits below threshold are dropped — keeps results relevant, avoids noise

---

## 7. Run the Test Suite

```bash
./mvnw clean test
```

> ✅ 21/21 tests passing · BUILD SUCCESS  
> Tests hit real ES on `localhost:9200` — index is dropped + recreated in `@BeforeEach`

---

## 8. Benchmark

```bash
bash dev/benchmark.sh
```

Inserts 100k documents directly into Postgres, then measures live API latency:

| Query type | Latency |
|---|---|
| Lexical (client name) | ~19ms |
| Semantic (doc content) | ~53ms |

---

## 🧠 Architecture Talking Points

### Why Elasticsearch over plain Postgres?
- Postgres `pg_vector` cosine search requires a full table scan at scale (no HNSW without extra config)
- ES gives **native KNN + BM25 hybrid** out of the box — one query, one score
- ES index is a **projection** — Postgres stays the write authority; ES can be rebuilt at any time

### Hybrid Scoring
- **BM25** handles exact keyword matches (client names, document titles)
- **KNN** handles semantic similarity (synonyms, paraphrases — e.g. "utility bill" → electricity bill)
- Scores combined via ES `bool` query with `should` clauses

### Sync Strategy
- Write to Postgres first (transactional) → then index into ES synchronously within the same request
- Trade-off: slight write latency; benefit: no eventual-consistency lag for reads
- Could evolve to **Debezium CDC → Kafka → ES** for high-throughput production use

### Embedder
- Sidecar Python service (`all-MiniLM-L6-v2` via `sentence-transformers`)
- Called on every document create to produce the 384-dim vector
- Zscaler TLS cert mounted into the embedder container at build time

### Data Flow

```
POST /clients/{id}/documents
        │
        ▼
  Postgres (write)
        │
        ▼
  Embedder (384-dim vector)
        │
        ▼
  Elasticsearch (index)
        │
GET /search?q=...
        │
        ▼
  ES hybrid query (BM25 + KNN)
        │
        ▼
  Filter by min-score (0.58)
        │
        ▼
  Enrich from Postgres (client metadata)
        │
        ▼
  JSON response
```

---

## ⚠️ Common Gotchas

- `platform: linux/amd64` required for ES image on Apple Silicon (M1/M2/M3)
- Embedder cert: `./embedder/zscaler-root.crt` mounted for Zscaler proxy environments
- `search_text` generated column was **removed** from Postgres schema — ES handles all search
- `dev/setup.sh` deleted — `podman compose up --build` is the only run command needed

---

## 9. Error Scenarios

### 400 — Missing required fields (client)

```bash
curl -s -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{}' | jq
```

> Returns `400 Bad Request` — `first_name`, `last_name`, `email` are all required

### 409 — Duplicate email

```bash
curl -s -X POST http://localhost:8080/clients \
  -H "Content-Type: application/json" \
  -d '{"first_name":"John","last_name":"Doe","email":"john.doe@example.com"}' | jq
```

> Returns `409 Conflict` — a client with that email already exists

### 400 — Missing required fields (document)

```bash
curl -s -X POST http://localhost:8080/clients/$CLIENT_ID/documents \
  -H "Content-Type: application/json" \
  -d '{}' | jq
```

> Returns `400 Bad Request` — `title` and `content` are required

### 404 — Client not found

```bash
curl -s -X POST http://localhost:8080/clients/00000000-0000-0000-0000-000000000000/documents \
  -H "Content-Type: application/json" \
  -d '{"title":"Passport","content":"Passport issued 2021"}' | jq
```

> Returns `404 Not Found` — no client with that UUID

### 400 — Blank search query

```bash
curl -s "http://localhost:8080/search?q=" | jq
```

> Returns `400 Bad Request` — query param `q` must not be blank

### 503 — Elasticsearch unavailable _(mention verbally)_

> If Elasticsearch is down, the API returns `503 Service Unavailable` rather than a generic `500` — because we know exactly which downstream failed. Shows deliberate error handling design.

---

## ✅ Submission Checklist

- [ ] `podman compose up --build` starts cleanly
- [ ] `./mvnw clean test` → 21/21 passing
- [ ] `curl /search?q=John` returns John Doe
- [ ] `curl /search?q=electricity+bill` returns ranked docs with scores
- [ ] `docs/openapi.json` committed and correct
- [ ] `docs/DEMO.md` committed
- [ ] `git status` is clean