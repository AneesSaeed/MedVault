#!/usr/bin/env bash
set -euo pipefail

# Generates:
#   certs/localhost.pem
#   certs/localhost-key.pem
# for nginx:
#   ssl_certificate     /certs/localhost.pem;
#   ssl_certificate_key /certs/localhost-key.pem;

CERT_DIR="/certs"
CERT_FILE="$CERT_DIR/localhost.pem"
KEY_FILE="$CERT_DIR/localhost-key.pem"

ensure_openssl() {
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl not found in PATH. Please install it and try again."
    exit 1
  fi
}

generate_certificates() {
  mkdir -p "$CERT_DIR"

  if [[ -f "$CERT_FILE" && -f "$KEY_FILE" ]]; then
    echo "Certificates already exist:"
    echo "  $CERT_FILE"
    echo "  $KEY_FILE"
    echo "Delete them if you want to regenerate."
    return
  fi

  echo "Generating self-signed localhost certificate with openssl..."

  openssl req \
    -x509 -nodes -newkey rsa:4096 \
    -keyout "$KEY_FILE" \
    -out "$CERT_FILE" \
    -days 365 \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost"

  echo "Done. Generated:"
  echo "  $CERT_FILE"
  echo "  $KEY_FILE"
}

main() {
  ensure_openssl
  generate_certificates
}

main "$@"
