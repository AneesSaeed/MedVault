# HealthSec - Secure Medical Records Management System

**Authors:**
- 62294 - SAEED Anees
- 63009 - EL Hichou Abderrahman
- 60287 - Ilias Abouchouar

---

## Overview

HealthSec is a secure medical records management system implementing end-to-end encryption (RSA 2048-bit + AES-256-GCM) using Spring Boot 3.5.7 backend, Angular frontend, Keycloak authentication, and ELK Stack logging with TLS.

---

## Prerequisites

- **Docker Desktop** (https://www.docker.com/products/docker-desktop)
  - Works on Ubuntu 22.04 and Windows 10+

Verify installation:
```bash
docker --version
docker-compose --version
```

---

## Installation and Building

### 1. Clone the Project

```bash
git clone <REPOSITORY-URL>
cd secu-project
```

### 2. Setup and Launch

We provide two methods to build and run the project:

#### Method A: Using Setup Script (Simple & Universal)

```bash
# Make scripts executable and verify dependencies
bash setup.sh

# Launch all services
docker-compose up -d
```

**What `setup.sh` does:**
- Verifies Docker and Docker Compose are installed
- Makes all required scripts executable (chmod +x)
- Checks that `.env` file exists
- Shows next steps

#### Method B: Using Makefile (Advanced Users)

```bash
# Setup dependencies
make setup

# Launch all services
make up
```

**Available Makefile commands:**
- `make help` - Show all available commands
- `make setup` - Make scripts executable
- `make up` - Start all Docker services
- `make down` - Stop all services
- `make logs` - View logs in real-time
- `make status` - Check service status
- `make clean` - Stop services and remove volumes

#### Manual Setup (If Needed)

If the above methods don't work, you can manually execute:

```bash
chmod +x ./scripts/setup-kibana-password.sh
chmod +x ./scripts/import-kibana-objects.sh
chmod +x ./nginx/generate-localhost-cert.sh
chmod +x ./internal-certs/generate-internal-certs.sh
docker-compose up -d
```

### 3. Wait for Services to Initialize

Wait **1-2 minutes** for all services to be ready.

View logs:
```bash
docker-compose logs -f
# OR with Makefile:
make logs
```

### 4. Verify All Services Are Running

```bash
docker-compose ps
# OR with Makefile:
make status
```

All containers should show status **Up**.

---

## How to Use

### Access the Application

| Service | URL | Credentials |
|---------|-----|-------------|
| **HealthSec App** | https://localhost | Create account in UI |
| **Kibana Logs** | https://localhost:5601 | `elastic` / (see `.env`) |
| **Database UI** | http://localhost:5050 | `app` / `app` |

### User Workflows

**For Patients:**
1. Register at https://localhost
2. Crypto keys generated automatically (RSA + AES-256)
3. Add doctors by their email
4. Upload encrypted medical records

**For Doctors:**
1. Register at https://localhost
2. Receive patient invitations
3. Access encrypted patient records

### API Examples

```bash
# Create patient account
curl -k -X POST https://localhost:8081/api/user/create-patient \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{...}'

# Get patient data
curl -k https://localhost:8081/api/patient/me \
  -H "Authorization: Bearer <JWT>"
```

---

## Troubleshooting

### Docker Issues

```bash
# Check service health
docker-compose ps
# OR: make status

# View logs for specific service
docker-compose logs spring_backend
docker-compose logs keycloak
# OR view all logs: make logs

# Clean restart (removes all data)
docker-compose down -v
docker-compose up -d
# OR: make clean && make up
```

### Port Already in Use

```bash
# Stop all services
docker-compose down
# OR: make down
```

---

## Stop the Project

```bash
# Stop all services (keeps data)
docker-compose down
# OR: make down
```

To also remove persistent data:
```bash
# Stop and remove all volumes
docker-compose down -v
# OR: make clean
```

---

## Project Structure

```
secu-project/
├── frontend/              # Angular 19 SPA (port 4200)
├── backend/               # Spring Boot 3.5.7 API (port 8081)
├── keycloak-config/       # OAuth2 realm configuration
├── nginx/                 # HTTPS reverse proxy (port 443)
├── logstash/              # Log pipeline
├── kibana/                # Dashboards
├── internal-certs/        # TLS certificate generation
├── docker-compose.yml     # Service orchestration
├── .env                   # Environment variables
├── setup.sh               # Dependency setup script
└── README.md              # This file
```

---

## Security Features

- **End-to-End Encryption**: RSA 2048-bit + AES-256-GCM
- **OAuth2 Authentication**: Keycloak JWT validation
- **TLS/HTTPS**: All internal and external communication encrypted
- **Rate Limiting**: Protection against brute-force attacks
- **Secure Logging**: ELK Stack with TLS and authentication
- **No Plaintext Storage**: Medical data encrypted at rest

---

## Configuration

All configuration is in `.env` (already provided):
- PostgreSQL credentials
- Keycloak admin account
- Elasticsearch/Kibana passwords
- Backend JDBC and Keycloak URIs

---

## Submission Requirements

-  Authors and matricules listed above
-  README with build and usage instructions
-  Setup script for dependency configuration
-  Runs on Ubuntu 22.04 and Windows 10+ with Docker

---

**Last updated: January 4, 2026** 
