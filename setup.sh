#!/bin/bash

set -e

echo "================================================"
echo "HealthSec - Setup Script"
echo "================================================"
echo ""

# Check if Docker is installed
echo "Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    echo "Docker is not installed. Please install Docker Desktop from:"
    echo "   https://www.docker.com/products/docker-desktop"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose is not installed."
    exit 1
fi

echo "✅ Docker is installed: $(docker --version)"
echo "✅ Docker Compose is installed: $(docker-compose --version)"
echo ""

# Make scripts executable
echo "Making scripts executable..."
chmod +x ./scripts/setup-kibana-password.sh || echo "⚠️  Warning: Could not chmod scripts/setup-kibana-password.sh"
chmod +x ./scripts/import-kibana-objects.sh || echo "⚠️  Warning: Could not chmod scripts/import-kibana-objects.sh"
chmod +x ./nginx/generate-localhost-cert.sh || echo "⚠️  Warning: Could not chmod nginx/generate-localhost-cert.sh"
chmod +x ./internal-certs/generate-internal-certs.sh || echo "⚠️  Warning: Could not chmod internal-certs/generate-internal-certs.sh"

echo "✅ Scripts made executable"
echo ""

# Verify .env file exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo "   Make sure .env exists at the project root with required variables."
    exit 1
fi

echo "✅ .env file found"
echo ""

# Summary
echo "================================================"
echo "Setup completed successfully! 🎉"
echo "================================================"
echo ""
echo "Next steps:"
echo "  1. Run: docker-compose up -d"
echo "  2. Wait 1-2 minutes for all services to start"
echo "  3. Access the app at: https://localhost"
echo ""
