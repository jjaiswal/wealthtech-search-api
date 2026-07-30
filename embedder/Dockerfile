FROM python:3.12-slim

WORKDIR /app

# --- Optional corporate proxy CA (local builds behind a TLS-inspecting proxy, e.g. Zscaler) ---
# Grigory / CI build normally → PIP_INDEX uses the default trust store, works out of the box.
# Behind a proxy, drop a `zscaler-root.crt` into this folder (gitignored) and build with:
#     podman build --build-arg TRUST_LOCAL_CA=1 -t nevis-embedder ./embedder
# The COPY below brings in the whole context; the RUN only trusts a cert if asked to.
COPY . /src-context/
ARG TRUST_LOCAL_CA=0
RUN if [ "$TRUST_LOCAL_CA" = "1" ] && ls /src-context/*.crt >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends ca-certificates && \
        cp /src-context/*.crt /usr/local/share/ca-certificates/ && \
        update-ca-certificates && \
        pip config set global.cert /etc/ssl/certs/ca-certificates.crt ; \
    fi

# Point Python's requests/huggingface_hub at the system CA bundle too (harmless when the
# bundle is the default; needed so the model download trusts a corporate proxy CA).
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt \
    SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt

# Install deps (own layer, cached unless requirements.txt changes).
RUN pip install --no-cache-dir -r /src-context/requirements.txt

# Bake the model in at build time → fast startup, no network needed at runtime
# (supports the offline reproducibility goal — DESIGN §1).
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('all-MiniLM-L6-v2')"

RUN cp /src-context/app.py /app/app.py

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
