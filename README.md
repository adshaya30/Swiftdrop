# SwiftDrop

SwiftDrop is a full-featured parcel delivery and courier management platform designed for courier logistics in Sri Lanka. It connects customers, merchants, couriers, and dispatchers across responsive web interfaces and RESTful APIs, backed by a modular, event-driven Spring Boot microservices architecture.

---

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Quick Start](#3-quick-start)
   - [3.1 Environment & Key Setup](#31-environment--key-setup)
   - [3.2 Running the Full Stack with Docker Compose](#32-running-the-full-stack-with-docker-compose)
   - [3.3 Stopping and Resetting](#33-stopping-and-resetting)
4. [Service Ports & URLs](#4-service-ports--urls)
5. [Demo Support & Accounts](#5-demo-support--accounts)
6. [Demo Script (SRS Appendix A)](#6-demo-script-srs-appendix-a)
7. [Running Tests & Coverage Reports](#7-running-tests--coverage-reports)
8. [Database Backup & Restore (NFR-REL-005)](#8-database-backup--restore-nfr-rel-005)
9. [Smoke Test Across the Stack](#9-smoke-test-across-the-stack)
10. [Troubleshooting](#10-troubleshooting)
11. [Kubernetes (Bonus Scope)](#11-kubernetes-bonus-scope)
12. [Project Reference Documentation](#12-project-reference-documentation)

---

## 1. Architecture Overview

SwiftDrop operates as nine decoupled services fronted by a single reactive API Gateway (SRS 3.1):

```
                                  [ Browser / API Client ]
                                             │
                                     (HTTP Port 8080)
                                             ▼
                                  ┌─────────────────────┐
                                  │     api-gateway     │ (Spring Cloud Gateway Reactive)
                                  └──────────┬──────────┘
             ┌───────────────────────────────┼───────────────────────────────┐
             │                               │                               │
             ▼                               ▼                               ▼
    ┌─────────────────┐             ┌─────────────────┐             ┌─────────────────┐
    │    frontend     │             │  auth-service   │             │ parcel-service  │
    │  (React 18 SPA) │             │ (Port 8081)     │             │ (Port 8082)     │
    └─────────────────┘             └────────┬────────┘             └────────┬────────┘
                                             │                               │
             ┌───────────────────────────────┼───────────────────────────────┤
             │                               │                               │
             ▼                               ▼                               ▼
    ┌─────────────────┐             ┌─────────────────┐             ┌─────────────────┐
    │ pricing-service │             │delivery-service │             │tracking-service │
    │ (Port 8083)     │             │ (Port 8084)     │             │ (Port 8085)     │
    └────────┬────────┘             └────────┬────────┘             └────────┬────────┘
             │                               │                               │
             └───────────────────────────────┼───────────────────────────────┘
                                             │
                                             ▼
                                    ┌─────────────────┐
                                    │notification-svc │
                                    │ (Port 8086)     │
                                    └────────┬────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
            ┌──────────────────────┐                    ┌──────────────────────┐
            │ PostgreSQL 16 (6 DBs)│                    │ RabbitMQ 4.x Broker  │
            └──────────────────────┘                    └──────────────────────┘
```

- **api-gateway** (Port 8080, Management 8090): Spring Cloud Gateway; single ingress for external API and UI traffic, rate limiting, security headers.
- **auth-service** (Port 8081): RS256 JWT issuer (holds private key), user registration, token rotation, Google OAuth2, merchant onboarding.
- **parcel-service** (Port 8082): Parcel creation, lifecycle state transitions, merchant orders, OpenPDF delivery receipts.
- **pricing-service** (Port 8083): Pricing quotation, Sri Lankan district lookups, weight fee calculations, Appendix B seed data.
- **delivery-service** (Port 8084): Dispatcher assignments, courier workload management, mobile proof of delivery (photo upload & resizing).
- **tracking-service** (Port 8085): Public and customer parcel tracking, SSE real-time tracking stream, audit timeline projection.
- **notification-service** (Port 8086): Asynchronous email (SMTP/Mailpit) & SMS notification delivery, signed merchant webhooks with retry schedule.
- **frontend** (Vite Dev 5173, Production Nginx 8080): React 18 SPA with Material UI, React Hook Form with Zod, and TanStack Query.
- **common-core & common-lib**: Shared pure-Java contracts, transactional outbox engine (`outbox_events`), security filters, and exception handling.

For complete architectural diagrams and design patterns, see [`docs/design/DESIGN.md`](file:///docs/design/DESIGN.md) and [`docs/design/deployment-diagram.md`](file:///docs/design/deployment-diagram.md).

---

## 2. Prerequisites

Ensure the following tools are installed on your host machine:
- **Docker & Docker Compose v2** (Docker Desktop on Windows/macOS or Docker Engine on Linux)
- **Java 21 LTS** (Eclipse Temurin 21 recommended)
- **Node.js 22 LTS** & npm
- **Git** (configured with LF line endings)
- **OpenSSL** (optional; scripts fallback to Docker `alpine/openssl`)
- Recommended Hardware: Minimum 4 CPU cores, 8 GB RAM allocated to Docker.

---

## 3. Quick Start

### 3.1 Environment & Key Setup

Before starting any containers, you must generate the local `.env` configuration file and the cryptographic RSA-2048 JWT key pair (DEP-013).

#### On Linux / macOS (Bash)
```bash
# 1. Initialize .env from .env.example with secure random secrets
./scripts/init-env.sh

# 2. Generate RSA-2048 key pair in infrastructure/keys/
./scripts/generate-keys.sh
```

#### On Windows (PowerShell)
```powershell
# 1. Initialize .env from .env.example with secure random secrets
.\scripts\init-env.ps1

# 2. Generate RSA-2048 key pair without UTF-16 BOM issues
.\scripts\generate-keys.ps1
```

> [!NOTE]
> `swiftdrop_private.pem` is mounted **strictly read-only** into `auth-service`. All other backend services receive only `swiftdrop_public.pem` for token signature validation.

#### Google OAuth Setup (Optional)
To enable Google Login (FR-AUTH-009):
1. Create OAuth 2.0 Web Client credentials in the Google Cloud Console.
2. Set the Authorized Redirect URI to: `http://localhost:8080/api/v1/auth/google/callback`
3. Add `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` into `.env`.
4. If left empty, the Google Login button is automatically hidden from the login screen without breaking local authentication.

---

### 3.2 Running the Full Stack with Docker Compose

To build all container images and launch the complete stack (infrastructure, 6 backend services, frontend, and API gateway):

```bash
docker compose up -d --build
```

To run with the demo webhook mock receiver (`mock-merchant` on port 9090):
```bash
docker compose --profile demo up -d --build
```

- **First build**: Downloads dependencies and compiles layered jars; tolerates up to 15 minutes (DEP-004).
- **Subsequent starts**: Starts pre-built images in under 3 minutes.
- **Startup Order**: PostgreSQL + RabbitMQ (healthy) ➔ Mailpit ➔ 6 Java Backends (healthy) ➔ Frontend (healthy) ➔ API Gateway.

#### Daily Development (Infrastructure Only)
To run only PostgreSQL, RabbitMQ, and Mailpit while running Java services from your IDE:
```bash
docker compose -f compose.infra.yaml up -d
```

---

### 3.3 Stopping and Resetting

- **Stop containers (preserve data):**
  ```bash
  docker compose down
  ```
  *(Data in `pgdata` and `rabbitmqdata` named volumes is preserved)*

- **Full reset (destroys all database records and queues):**
  ```bash
  docker compose down -v
  ```

---

## 4. Service Ports & URLs

Only the **API Gateway** is published on `0.0.0.0` for external user traffic. All other backend and backing ports are strictly bound to `127.0.0.1` (DEP-007):

| Service / Interface | URL / Host Binding | Description & Credentials |
| :--- | :--- | :--- |
| **SwiftDrop Web App** | [http://localhost:8080](http://localhost:8080) | Public tracking, Customer, Courier & Admin portals |
| **API Gateway Ingress** | [http://localhost:8080/api/v1](http://localhost:8080/api/v1) | Gateway entrypoint for external REST API calls |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | `swiftdrop_user` / (password generated in `.env`) |
| **Mailpit Web UI** | [http://localhost:8025](http://localhost:8025) | Local SMTP mailbox for viewing notification emails |
| **PostgreSQL 16** | `127.0.0.1:5433` | Host mapped port to container 5432 (`swiftdrop_admin`) |
| **Mock Merchant Receiver** | [http://localhost:9090](http://localhost:9090) | Demo webhook listener (profile `demo`, container port 9000) |
| **Auth Service (Direct)** | `127.0.0.1:8081` | Direct service port (local debugging) |
| **Parcel Service (Direct)** | `127.0.0.1:8082` | Direct service port (local debugging) |
| **Pricing Service (Direct)** | `127.0.0.1:8083` | Direct service port (local debugging) |
| **Delivery Service (Direct)** | `127.0.0.1:8084` | Direct service port (local debugging) |
| **Tracking Service (Direct)**| `127.0.0.1:8085` | Direct service port (local debugging) |
| **Notification Service** | `127.0.0.1:8086` | Direct service port (local debugging) |
| **Swagger UI (When Enabled)**| `http://localhost:<service-port>/swagger-ui.html` | Enabled when `SWAGGER_ENABLED=true` |

> [!WARNING]
> The Gateway management port `8090` is **never published to the host** (DEP-007). Internal endpoints (`/internal/**`) and Spring Actuator (`/actuator/**`) always return `404 NOT_FOUND` when queried through gateway port `8080`.

---

## 5. Demo Support & Accounts

When `DEMO_SEED_ENABLED=true` in `.env`, the demo seed users defined in FR-AUTH-025 are provisioned on startup:

| Role | Email | Password |
| :--- | :--- | :--- |
| **System Admin** | `admin@swiftdrop.lk` | Value of `BOOTSTRAP_ADMIN_PASSWORD` in `.env` |
| **Dispatcher** | `dispatcher@swiftdrop.lk` | Value of `DEMO_USER_PASSWORD` in `.env` |
| **Courier** | `courier@swiftdrop.lk` | Value of `DEMO_USER_PASSWORD` in `.env` |
| **Customer** | `customer@example.com` | Value of `DEMO_USER_PASSWORD` in `.env` |
| **Merchant** | `merchant@store.lk` | Value of `DEMO_USER_PASSWORD` in `.env` |

### Webhook Demo Delays
For fast demonstrations of webhook retry schedules, set in `.env`:
```env
WEBHOOK_RETRY_DELAYS=PT5S,PT10S,PT15S,PT20S,PT30S
```

---

## 6. Demo Script (SRS Appendix A)

Follow the acceptance scenarios in demo order:

1. **Customer Scenarios (AT-01 to AT-11)**:
   - **AT-01**: Register visitor with valid data; weak password triggers validation errors; duplicate email returns `EMAIL_ALREADY_REGISTERED`.
   - **AT-02**: 5 failed logins lock account; 6th attempt returns `429 ACCOUNT_LOCKED`.
   - **AT-03 & AT-04**: Google login creates customer account without password; existing email rejects merge.
   - **AT-05 & AT-06**: Price quote Colombo to Kandy (2 kg MEDIUM = LKR 800.00; SMALL = 650.00). Colombo to Gampaha (same zone: 1.0 kg = 350.00, 1.01 kg = 450.00).
   - **AT-07**: Merchant with 10% discount: Colombo to Jaffna, 5 kg LARGE = LKR 1,620.00.
   - **AT-08**: Customer quote preview; admin changes price; confirmation returns `FEE_CHANGED`.
   - **AT-09**: Double-clicking Confirm or replaying `Idempotency-Key` creates exactly one parcel.
   - **AT-10 & AT-11**: Public tracking shows limited view (no contact info); courier status update reflects via SSE within 3 s.
2. **Merchant Scenarios (AT-12 to AT-16)**:
   - **AT-12**: Merchant registration; admin approves; approval email in Mailpit; merchant logs in.
   - **AT-13**: Merchant creates API credentials (secret displayed once); retrieves token (`typ=client`).
   - **AT-14**: API order creation via Postman (`tools/postman/`); idempotency replay and validation.
   - **AT-15 & AT-16**: Configure webhook URL to `http://mock-merchant:9000/webhook`; test ping verifies HMAC-SHA256 signature; courier delivery triggers signed event.
3. **Dispatcher & Courier Scenarios (AT-17 to AT-20)**:
   - **AT-17**: Dispatcher assigns parcel to courier; courier sees item in "Today's Deliveries" list.
   - **AT-18**: Courier progresses parcel: Picked Up ➔ In Transit ➔ Out for Delivery ➔ Delivered (with recipient name, note, and proof photo).
   - **AT-19**: Courier reports failure with valid stage-specific reason (e.g., `RECIPIENT_UNAVAILABLE`).
   - **AT-20**: Invalid lifecycle skips (Created ➔ Delivered) rejected with `409 INVALID_STATUS_TRANSITION`.
4. **Security, Resilience & Audit (AT-21 to AT-27, AT-38, AT-39)**:
   - **AT-21**: Cross-courier and cross-customer access returns `404 NOT_FOUND`; client token on user endpoint returns `403`.
   - **AT-24**: Stop RabbitMQ (`docker compose stop rabbitmq`), perform courier transition (succeeds via transactional outbox), restart RabbitMQ: events fan out without loss.
   - **AT-30**: Delivered parcel receipt PDF downloads with valid pricing and VAT breakdown.
   - **AT-39**: Admin audit log displays complete actor history with sensitive contact data masked.

---

## 7. Running Tests & Coverage Reports

### Backend Modules (Java / Maven)
To run unit and integration tests across all modules (including Testcontainers PostgreSQL/RabbitMQ and JaCoCo 70% line coverage enforcement):

```bash
cd backend
./mvnw clean verify
```

To run verification on a specific service:
```bash
./mvnw -pl delivery-service -am verify
```

JaCoCo HTML coverage reports are generated at:
```
backend/<service-name>/target/site/jacoco/index.html
```

### Frontend (React / Vitest)
To run unit and component tests with code coverage (60% threshold):

```bash
cd frontend
npm install
npm test -- --coverage
```

Coverage report generated at `frontend/coverage/index.html`.

---

## 8. Database Backup & Restore (NFR-REL-005)

PostgreSQL data is partitioned across six isolated databases owned by dedicated service users. Backups can be captured and restored individually using standard `pg_dump` and `psql` / `pg_restore`:

### Databases
- `swiftdrop_auth`
- `swiftdrop_parcel`
- `swiftdrop_pricing`
- `swiftdrop_delivery`
- `swiftdrop_tracking`
- `swiftdrop_notification`

### 8.1 Backing Up a Database
To create a custom-format compressed backup:
```bash
# Example for parcel-service:
docker compose exec -T postgres pg_dump -U swiftdrop_admin -d swiftdrop_parcel -F c > swiftdrop_parcel.dump

# Plain SQL backup:
docker compose exec -T postgres pg_dump -U swiftdrop_admin -d swiftdrop_auth > swiftdrop_auth.sql
```

### 8.2 Restoring a Database
To restore a database from a backup file:
```bash
# Restore custom-format dump:
docker compose exec -T postgres pg_restore -U swiftdrop_admin -d swiftdrop_parcel --clean --if-exists < swiftdrop_parcel.dump

# Restore plain SQL dump:
docker compose exec -T postgres psql -U swiftdrop_admin -d swiftdrop_auth < swiftdrop_auth.sql
```

---

## 9. Smoke Test Across the Stack

A comprehensive automated smoke test script validates the complete live stack end-to-end:
- Checks route isolation (404 on `/actuator` and `/internal`)
- Registers and authenticates a customer
- Requests a pricing quote
- Creates an idempotent parcel
- Authenticates as admin and assigns to courier
- Executes courier lifecycle transitions to `DELIVERED`
- Queries public tracking
- Verifies Mailpit email receipt

#### Run on Linux / macOS (Bash)
```bash
./infrastructure/scripts/smoke-test.sh
```

#### Run on Windows (PowerShell)
```powershell
.\infrastructure\scripts\smoke-test.ps1
```

---

## 10. Troubleshooting

- **Port Conflicts**:
  - `8080`: Ensure no other web server (IIS, Apache, local dev server) is occupying port 8080.
  - `5433`: PostgreSQL host binding is `5433` (not `5432`) to avoid collisions with local Postgres installs.
- **Line Endings (Windows CRLF vs Linux LF)**:
  - Shell scripts (`*.sh`) must use LF line endings. Git repository is configured with `.gitattributes` (`* text=auto eol=lf`). If scripts fail with `\r: command not found`, run: `dos2unix scripts/*.sh infrastructure/scripts/*.sh`.
- **Docker Resource Limits**:
  - Running all 9 microservices simultaneously requires at least **4 CPU cores** and **8 GB RAM** in Docker Desktop settings. If services crash on startup, increase Docker RAM.
- **Healthcheck Failures**:
  - Container healthchecks rely on `wget` (built into Alpine). Do not replace healthchecks with `curl`.
- **Database Connection Retries**:
  - Backend services retry database connections at startup; allow up to 40 seconds start period for initial Flyway migrations.

---

## 11. Kubernetes (Bonus Scope)

*Not implemented.* Per group leader decision CR-001 and Master Prompt Section 0, Kubernetes deployment, Helm charts, and Prometheus/Grafana monitoring are out of scope for the core platform deliverables.

---

## 12. Project Reference Documentation

- Detailed System Requirements: [`docs/SRS.md`](file:///docs/SRS.md)
- Work Division & Ownership: [`docs/WORK_DIVISION.md`](file:///docs/WORK_DIVISION.md)
- UI Design System & Style Tokens: [`docs/UI_DESIGN_SYSTEM.md`](file:///docs/UI_DESIGN_SYSTEM.md)
- Master Prompt Rules & Precedence: [`docs/SWIFTDROP_MASTER_PROMPT.md`](file:///docs/SWIFTDROP_MASTER_PROMPT.md)
- Deployment Architecture Diagram: [`docs/design/deployment-diagram.md`](file:///docs/design/deployment-diagram.md)
- Infrastructure Acceptance Report (AT-32 to AT-37): [`docs/test-reports/acceptance-infra.md`](file:///docs/test-reports/acceptance-infra.md)
- Postman Collection: [`tools/postman/`](file:///tools/postman/)
