#!/usr/bin/env bash
# Scale benchmark: bulk-insert synthetic clients + documents, then measure write throughput.
#
# Usage:
#   ./dev/benchmark.sh [CLIENTS] [DOCS_PER_CLIENT]
#
# Defaults: 1000 clients, 100 documents each = 100,000 documents.
#
# Note: this script tests Postgres write throughput and storage sizing only.
# Search latency is measured end-to-end via the API (see step 4).
#
# Requires: curl, python3, running stack (podman compose up).
set -e

NUM_CLIENTS="${1:-1000}"
DOCS_PER_CLIENT="${2:-100}"
TOTAL_DOCS=$((NUM_CLIENTS * DOCS_PER_CLIENT))

DB_CONTAINER="${DB_CONTAINER:-wealthtech-search-api-db-1}"
DB_NAME="${DB_NAME:-nevis}"
DB_USER="${DB_USER:-nevis}"

API_BASE="${API_BASE:-http://localhost:8080}"

PSQL="podman exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -v ON_ERROR_STOP=1"

ms() { python3 -c 'import time; print(int(time.time()*1000))'; }

echo "=== Scale Benchmark ==="
echo "Target: $NUM_CLIENTS clients × $DOCS_PER_CLIENT docs = $TOTAL_DOCS documents"
echo ""

# --- Check connectivity ---
if ! podman exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" >/dev/null 2>&1; then
  echo "ERROR: Cannot connect to Postgres container ($DB_CONTAINER)"
  echo "Make sure the database is running (podman compose up)."
  exit 1
fi

if ! curl -sf --max-time 2 "$API_BASE/search?q=test" -o /dev/null 2>&1; then
  echo "WARNING: API at $API_BASE does not appear to be running."
  echo "Step [4/4] (search latency) will be skipped."
  SKIP_SEARCH=true
fi

# --- Insert synthetic clients ---
echo -n "[1/4] Inserting $NUM_CLIENTS clients…"
START=$(ms)

$PSQL -q <<SQL
INSERT INTO clients (id, first_name, last_name, email, description, social_links, created_at)
SELECT
    gen_random_uuid(),
    'Client' || lpad(i::text, 5, '0'),
    'Bench',
    'client' || lpad(i::text, 5, '0') || '@benchmark.test',
    'Synthetic benchmark client number ' || i || ' for scale testing.',
    ARRAY['https://example.com/' || i],
    now()
FROM generate_series(1, $NUM_CLIENTS) AS i
ON CONFLICT (email) DO NOTHING;
SQL

END=$(ms)
echo " done ($(( END - START )) ms)"

# --- Insert synthetic documents with random 384-dim vectors ---
echo -n "[2/4] Inserting $TOTAL_DOCS documents (with random 384-dim vectors)…"
START=$(ms)

$PSQL -q <<SQL
INSERT INTO documents (id, client_id, title, content, summary, embedding, created_at)
SELECT
    gen_random_uuid(),
    c.id,
    'Document ' || d || ' of ' || c.first_name,
    repeat('Financial document content for benchmark testing. ', 200),
    'Benchmark summary for document ' || d || '.',
    (
        SELECT ('[' || string_agg((random() * 2 - 1)::text, ',') || ']')::vector
        FROM generate_series(1, 384)
    ),
    now()
FROM (
    SELECT id, first_name
    FROM clients
    WHERE email LIKE '%@benchmark.test'
    ORDER BY email
) c
CROSS JOIN generate_series(1, $DOCS_PER_CLIENT) AS d;
SQL

END=$(ms)
echo " done ($(( END - START )) ms)"

# --- Table and index sizes ---
echo ""
echo "[3/4] Table statistics:"
$PSQL -q --tuples-only -A <<SQL
SELECT 'Clients:    ' || count(*) || ' rows' FROM clients;
SQL
$PSQL -q --tuples-only -A <<SQL
SELECT
    'Documents:  ' || count(*) || ' rows, ' ||
    pg_size_pretty(pg_total_relation_size('documents')) || ' total (' ||
    pg_size_pretty(pg_relation_size('documents')) || ' heap + ' ||
    pg_size_pretty(pg_indexes_size('documents')) || ' indexes)'
FROM documents;
SQL

# --- Search latency via API (Elasticsearch) ---
echo ""
echo "[4/4] Search latency (end-to-end via API → Elasticsearch):"
echo ""

if [[ "${SKIP_SEARCH:-false}" == "true" ]]; then
  echo "Skipped — API not reachable."
else
  # Warm-up
  curl -sf "$API_BASE/search?q=bench" -o /dev/null

  echo "--- Client lexical search (?q=bench, 10 runs) ---"
  TOTAL=0
  for i in $(seq 1 10); do
    MS=$(curl -sf -o /dev/null -w "%{time_total}" "$API_BASE/search?q=bench")
    MS=$(python3 -c "print(int($MS * 1000))")
    TOTAL=$((TOTAL + MS))
  done
  echo "Average: $((TOTAL / 10)) ms"

  echo ""
  echo "--- Document semantic search (?q=financial+document, 10 runs) ---"
  TOTAL=0
  for i in $(seq 1 10); do
    MS=$(curl -sf -o /dev/null -w "%{time_total}" "$API_BASE/search?q=financial+document")
    MS=$(python3 -c "print(int($MS * 1000))")
    TOTAL=$((TOTAL + MS))
  done
  echo "Average: $((TOTAL / 10)) ms"
fi

echo ""

# --- Cleanup prompt ---
echo "=== Benchmark complete ==="
echo ""
read -p "Remove benchmark data? [Y/n] " -n 1 -r REPLY
echo ""
if [[ -z "$REPLY" || "$REPLY" =~ ^[Yy]$ ]]; then
  echo -n "Cleaning up…"
  $PSQL -q -c "DELETE FROM documents WHERE title LIKE 'Document % of Client%';"
  $PSQL -q -c "DELETE FROM clients WHERE email LIKE '%@benchmark.test';"
  echo " done."
else
  echo "Benchmark data retained."
  echo "Note: benchmark documents are NOT indexed in Elasticsearch (inserted directly"
  echo "into Postgres). Run curl $API_BASE/admin/reindex to sync if needed."
fi