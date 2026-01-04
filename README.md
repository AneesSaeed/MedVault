# HealthSec - Secure Medical Records Management System

**Authors:**
- 62294 - SAEED Anees
- 63009 - EL Hichou Abderrahman
- 60287 - Ilias Abouchouar

---

## Overview

HealthSec is a web application for managing medical records securely.
It allows **patients** and **doctors** to create accounts, share medical records, and access them.

The project is fully containerized and runs with Docker Compose.

---

## Prerequisites

- Docker
- Docker Compose

---

## Installation and Run

### 1. Clone the Project

```bash
git clone git@git.esi-bru.be:62294/secu-project.git
cd secu-project
```

### 2. Start the Application

```bash
docker compose up
```
This command is sufficient in normal conditions.
All required scripts are executed inside the containers.
First startup may take a few minutes.

---

### Alternative Setup (If Needed)

If execution permissions are missing on some systems, use one of the following methods.

#### Method A: Setup Script

```bash
bash setup.sh
docker-compose up -d
```

**What `setup.sh` does:**
- checks Docker and Docker Compose
- sets execution permissions on required scripts
- verifies the .env file

#### Method B: Makefile

```bash
make setup
make up
```

**Available commands:**
- `make setup` — make required scripts executable
- `make up` — start services
- `make down` — stop services
- `make logs` — view logs
- `make status` — service status
- `make clean` — stop services and remove volumes

#### Manual Setup (Last Resort)

If the above methods don't work, you can manually execute:

```bash
chmod +x ./scripts/setup-kibana-password.sh
chmod +x ./scripts/import-kibana-objects.sh
chmod +x ./nginx/generate-localhost-cert.sh
chmod +x ./internal-certs/generate-internal-certs.sh
docker-compose up -d
```
---
### Access the Application

| Service | URL | Credentials |
|---------|-----|-------------|
| **HealthSec App** | https://localhost | Create account |
| **Kibana Logs** | https://localhost:5601 | username = `elastic` /  password = `supersecret` |
| **Database UI** | http://localhost:5050 | `PostgreSQL` / `app` / `app` |

---
## Usage

**Patients:**
1. Register
2. Add doctors
3. Upload medical records

**For Doctors:**
1. Register
2. Be added by a patient
3. Access patient records
4. Propose medical files to patients

---
## Stop the Project

```bash
docker-compose down
# OR: make down
```

To remove all data:
```bash
docker-compose down -v
# OR: make clean
```

---

## Project Structure

```
secu-project/
├── frontend/              # Angular frontend
├── backend/               # Spring Boot backend
├── keycloak-config/       # realm configuration
├── nginx/                 # reverse proxy
├── logstash/              # Logging pipeline
├── kibana/                # Log dashboards
├── internal-certs/
├── docker-compose.yml
├── .env
├── setup.sh
└── README.md
```
