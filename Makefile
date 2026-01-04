.PHONY: help setup up down logs status clean

help:
	@echo "Commands:"
	@echo "  make setup   - Make required scripts executable"
	@echo "  make up      - Start services"
	@echo "  make down    - Stop services"
	@echo "  make logs    - Follow logs"
	@echo "  make status  - Show running containers"
	@echo "  make clean   - Stop services + remove volumes"

setup:
	@chmod +x ./scripts/setup-kibana-password.sh
	@chmod +x ./scripts/import-kibana-objects.sh
	@chmod +x ./nginx/generate-localhost-cert.sh
	@chmod +x ./internal-certs/generate-internal-certs.sh

up:
	@docker compose up -d

down:
	@docker compose down

logs:
	@docker compose logs -f

status:
	@docker compose ps

clean:
	@docker compose down -v

.DEFAULT_GOAL := help
