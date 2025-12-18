#!/usr/bin/env bash
set -euo pipefail

# Shared volume directory (mounted into nginx/backend/keycloak as read-only)
DIR="/certs/internal"
mkdir -p "$DIR"

# Internal root CA (private key + public cert)
CA_KEY="$DIR/ca.key"
CA_CRT="$DIR/ca.crt"

# Java truststore containing CA_CRT, used by backend JVM to trust Keycloak TLS
TRUSTSTORE="$DIR/truststore.jks"
STOREPASS="secret"

# Backend server identity (keypair + cert + PKCS12 keystore for Spring Boot)
BACKEND_KEY="$DIR/backend.key"
BACKEND_CRT="$DIR/backend.crt"
BACKEND_P12="$DIR/backend.p12"

# Keycloak server identity (keypair + cert)
KEYCLOAK_KEY="$DIR/keycloak.key"
KEYCLOAK_CRT="$DIR/keycloak.crt"


fix_perms() {
  # Make directory traversable
  chmod 755 "$DIR" || true

  # Public material: readable by everyone
  chmod 644 "$DIR"/*.crt "$DIR"/ca.crt "$DIR"/truststore.jks "$DIR"/ca.srl 2>/dev/null || true

  # Private material (not world-readable)
  chmod 640 "$DIR"/*.key "$DIR"/*.p12 "$DIR"/ca.key 2>/dev/null || true

  # Use group 0 so typical rootless container setups with GID 0 can read
  # (required because keycloak/backend may not run as root)
  chgrp 0 "$DIR"/*.key "$DIR"/*.p12 "$DIR"/ca.key 2>/dev/null || true
}


if [[ -f "$CA_CRT" && -f "$BACKEND_P12" && -f "$KEYCLOAK_CRT" && -f "$TRUSTSTORE" ]]; then
  echo "Internal certs already exist in $DIR"
  fix_perms
  exit 0
fi

echo "Generating internal CA..."
# Generate CA private key
openssl genrsa -out "$CA_KEY" 4096
# Self-sign a CA certificate (trust anchor) valid for 10 years
openssl req -x509 -new -nodes -key "$CA_KEY" -sha256 -days 3650 \
  -subj "/CN=health-internal-ca" -out "$CA_CRT"

issue() {
  local name="$1"
  local key="$2"
  local crt="$3"
  local san="$4"

  local csr="$DIR/$name.csr"
  local ext="$DIR/$name.ext"
  
  # Generate private key for the service
  openssl genrsa -out "$key" 2048
  
  # Create CSR with CN=$name
  openssl req -new -key "$key" -subj "/CN=$name" -out "$csr"

  # Add SANs so hostname verification works (nginx uses backend/keycloak DNS names)
  cat > "$ext" <<EOF
subjectAltName=$san
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF

  # Sign service cert with internal CA
  openssl x509 -req -in "$csr" -CA "$CA_CRT" -CAkey "$CA_KEY" -CAcreateserial \
    -out "$crt" -days 825 -sha256 -extfile "$ext"

  rm -f "$csr" "$ext"
}

echo "Issuing backend and keycloak certs..."
# backend cert is valid for DNS:backend (and optional alias DNS:spring_backend)
issue backend  "$BACKEND_KEY"  "$BACKEND_CRT"  "DNS:backend,DNS:spring_backend"
# keycloak cert is valid for DNS:keycloak
issue keycloak "$KEYCLOAK_KEY" "$KEYCLOAK_CRT" "DNS:keycloak"

echo "Creating backend PKCS12 keystore..."
# Spring Boot reads PKCS12 keystore for server.ssl.key-store
openssl pkcs12 -export -name backend -in "$BACKEND_CRT" -inkey "$BACKEND_KEY" \
  -certfile "$CA_CRT" -out "$BACKEND_P12" -passout pass:$STOREPASS

echo "Creating Java truststore for internal CA..."
# Backend JVM uses this truststore to validate Keycloak's TLS cert chain
rm -f "$TRUSTSTORE"
keytool -importcert -noprompt -alias health-internal-ca \
  -file "$CA_CRT" -keystore "$TRUSTSTORE" -storepass "$STOREPASS"

fix_perms
echo "Done."
