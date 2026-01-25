# MedVault – Secure Medical Records Management System

Academic project
Security course (5SEC1A) – Academic year 2025–2026

---

## Overview

MedVault is a secure client–server web application for managing sensitive medical records.
The system allows **patients** and **doctors** to create accounts, share medical records, and
access them under strict security constraints.

The project is designed under a **non-trusted server assumption**, meaning the server must
never have access to sensitive data in plaintext. Security is the primary evaluation criterion.

The application is fully containerized and runs using **Docker Compose**.

---

## Key Security Principles

- End-to-end confidentiality of medical records
- No plaintext storage of sensitive data on the server
- Hardened authentication and access control
- Explicit patient approval for delegated actions
- Secure handling of credentials and cryptographic material
- Monitoring and logging for security analysis

---

## Prerequisites

- Docker
- Docker Compose

---

## Installation and Run

### 1. Clone the Project

```bash
git clone git@github.com:AneesSaeed/MedVault.git
cd MedVault
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
| **Database UI** | http://localhost:5050 | system `PostgreSQL` / `app` / `app` / db `mydb`|

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
