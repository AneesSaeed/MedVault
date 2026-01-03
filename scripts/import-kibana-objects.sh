#!/bin/bash
# Script to import Kibana saved objects from NDJSON file
# This script should be run after Kibana is ready
#
# SECURITY NOTE: Default passwords ("changeme") are for DEVELOPMENT ONLY.
# In production, always set ELASTIC_PASSWORD and KIBANA_PASSWORD via .env file.

set -e

KIBANA_HOST="${KIBANA_HOST:-kibana:5601}"
ELASTIC_USER="${ELASTIC_USER:-elastic}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-changeme}"  # DEV ONLY - change in production
IMPORT_FILE="${IMPORT_FILE:-/kibana/healthsec-full-import.ndjson}"
CA_CERT="${CA_CERT:-/certs/internal/ca.crt}"

echo "Waiting for Kibana to be ready..."
MAX_RETRIES=60
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  if curl -s -k --cacert "$CA_CERT" -u "$ELASTIC_USER:$ELASTIC_PASSWORD" "https://$KIBANA_HOST/api/status" > /dev/null 2>&1; then
    echo "Kibana is ready!"
    break
  fi
  echo "Kibana is not ready yet, waiting... (attempt $((RETRY_COUNT + 1))/$MAX_RETRIES)"
  sleep 2
  RETRY_COUNT=$((RETRY_COUNT + 1))
done

if [ $RETRY_COUNT -eq $MAX_RETRIES ]; then
  echo "ERROR: Kibana did not become ready after $MAX_RETRIES attempts"
  exit 1
fi

# Wait a bit more for Kibana to fully initialize
echo "Waiting for Kibana to fully initialize..."
sleep 5

if [ ! -f "$IMPORT_FILE" ]; then
  echo "ERROR: Import file not found: $IMPORT_FILE"
  exit 1
fi

echo "Importing Kibana saved objects from $IMPORT_FILE..."

# Import saved objects using Kibana API
# Note: We use elastic user credentials to authenticate
HTTP_CODE=$(curl -s -k -w "%{http_code}" -o /tmp/kibana_import_response.json \
  --cacert "$CA_CERT" \
  -X POST \
  -u "$ELASTIC_USER:$ELASTIC_PASSWORD" \
  "https://$KIBANA_HOST/api/saved_objects/_import?overwrite=true" \
  -H "kbn-xsrf: true" \
  --form "file=@${IMPORT_FILE}")

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 201 ]; then
  echo ""
  echo "SUCCESS: Kibana saved objects imported successfully!"
  echo "Response:"
  cat /tmp/kibana_import_response.json 2>/dev/null | head -20 || true
else
  echo ""
  echo "WARNING: Import completed with HTTP code: $HTTP_CODE"
  echo "Response:"
  cat /tmp/kibana_import_response.json 2>/dev/null || true
  
  # Check if objects already exist (which is fine)
  if grep -q "already exists" /tmp/kibana_import_response.json 2>/dev/null; then
    echo ""
    echo "INFO: Some objects already exist (this is normal if re-running the script)"
    exit 0
  fi
  
  # If it's not a success code and not an "already exists" error, exit with error
  if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
    exit 1
  fi
fi

