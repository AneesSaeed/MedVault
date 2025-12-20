FROM debian:bookworm-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
      openssl default-jre-headless ca-certificates dos2unix && \
    rm -rf /var/lib/apt/lists/*

COPY generate-internal-certs.sh /docker-entrypoint.d/10-generate-internal-certs.sh

RUN dos2unix /docker-entrypoint.d/10-generate-internal-certs.sh && \
    chmod +x /docker-entrypoint.d/10-generate-internal-certs.sh

CMD ["bash", "-lc", "for f in /docker-entrypoint.d/*; do bash \"$f\"; done; tail -f /dev/null"]
