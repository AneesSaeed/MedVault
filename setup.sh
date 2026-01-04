#!/bin/bash

set -euo pipefail

# Check Docker
command -v docker >/dev/null 2>&1 || {
  echo "Docker is required."
  exit 1
}

# Check Docker Compose (plugin)
docker compose version >/dev/null 2>&1 || {
  echo "Docker Compose is required (docker compose)."
  exit 1
}

# Make scripts executable
chmod +x ./scripts/setup-kibana-password.sh 2>/dev/null || true
chmod +x ./scripts/import-kibana-objects.sh 2>/dev/null || true
chmod +x ./nginx/generate-localhost-cert.sh 2>/dev/null || true
chmod +x ./internal-certs/generate-internal-certs.sh 2>/dev/null || true


# Verify .env
if [ ! -f .env ]; then
  echo ".env file not found at project root."
  exit 1
fi

echo "Setup complete."
echo "Run: docker compose up -d"