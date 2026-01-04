#!/bin/bash
# Script to set kibana_system password in Elasticsearch
#
# SECURITY NOTE: Default passwords ("changeme") are for DEVELOPMENT ONLY.

set -e

ELASTIC_HOST="${ELASTICSEARCH_HOST:-elasticsearch:9200}"
ELASTIC_USER="${ELASTIC_USER:-elastic}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-changeme}"
KIBANA_PASSWORD="${KIBANA_PASSWORD:-changeme}"
CA_CERT="${CA_CERT:-/certs/internal/ca.crt}"

echo "Waiting for Elasticsearch to be ready..."
until curl -s -k --cacert "$CA_CERT" -u "$ELASTIC_USER:$ELASTIC_PASSWORD" "https://$ELASTIC_HOST/_cluster/health" > /dev/null 2>&1; do
  echo "Elasticsearch is not ready yet, waiting..."
  sleep 2
done

echo "Elasticsearch is ready. Setting kibana_system password..."

# Set the kibana_system password
HTTP_CODE=$(curl -s -k -w "%{http_code}" -o /tmp/kibana_password_response.json \
  --cacert "$CA_CERT" \
  -X POST \
  -u "$ELASTIC_USER:$ELASTIC_PASSWORD" \
  "https://$ELASTIC_HOST/_security/user/kibana_system/_password" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"$KIBANA_PASSWORD\"}")

if [ "$HTTP_CODE" -eq 200 ]; then
  echo ""
  echo "kibana_system password has been set successfully!"
else
  echo ""
  echo "ERROR: Failed to set kibana_system password (HTTP $HTTP_CODE)"
  cat /tmp/kibana_password_response.json 2>/dev/null || true
  exit 1
fi

