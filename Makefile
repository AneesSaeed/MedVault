.PHONY: help install setup up down logs status clean

help:
	@echo "HealthSec - Project Management"
	@echo ""
	@echo "Available commands:"
	@echo "  make install   - Setup dependencies (make scripts executable)"
	@echo "  make setup     - Same as install"
	@echo "  make up        - Start all Docker services"
	@echo "  make down      - Stop all Docker services"
	@echo "  make logs      - View logs from all services"
	@echo "  make status    - Check status of running services"
	@echo "  make clean     - Stop services and remove volumes"
	@echo "  make help      - Show this help message"
	@echo ""

install:
	@echo "Making scripts executable..."
	@chmod +x ./scripts/setup-kibana-password.sh
	@chmod +x ./scripts/import-kibana-objects.sh
	@chmod +x ./nginx/generate-localhost-cert.sh
	@chmod +x ./internal-certs/generate-internal-certs.sh
	@echo "✅ Scripts are now executable"

setup: install

up:
	@echo "Starting all services..."
	@docker-compose up -d
	@echo "✅ Services starting. Wait 1-2 minutes for full initialization."
	@echo "Access the app at: https://localhost"

down:
	@echo "Stopping all services..."
	@docker-compose down
	@echo "✅ Services stopped"

logs:
	@docker-compose logs -f

status:
	@docker-compose ps

clean:
	@echo "Removing all services and volumes..."
	@docker-compose down -v
	@echo "✅ All services and data removed"

.DEFAULT_GOAL := help
