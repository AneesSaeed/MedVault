FROM nginx:1.28-bookworm

# Install openssl
RUN apt-get update && \
    apt-get install -y --no-install-recommends openssl && \
    rm -rf /var/lib/apt/lists/*

# Copy nginx.conf into the image
COPY nginx.conf /etc/nginx/nginx.conf

# Copy the cert-generation script into /docker-entrypoint.d
COPY generate-localhost-cert.sh /docker-entrypoint.d/10-generate-localhost-cert.sh
RUN chmod +x /docker-entrypoint.d/10-generate-localhost-cert.sh
