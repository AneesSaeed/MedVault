FROM debian:bookworm-slim

# Tools needed:
# - openssl: generate CA/certs
# - default-jre-headless + keytool: create Java truststore (JKS)
# - ca-certificates: baseline cert bundle
RUN apt-get update && \
    apt-get install -y --no-install-recommends openssl default-jre-headless ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# Entry script generates certs into the shared Docker volume.
COPY generate-internal-certs.sh /docker-entrypoint.d/10-generate-internal-certs.sh
RUN chmod +x /docker-entrypoint.d/10-generate-internal-certs.sh

# Run scripts once, then keep container alive so the volume stays available.
CMD ["bash", "-lc", "for f in /docker-entrypoint.d/*; do bash \"$f\"; done; tail -f /dev/null"]
