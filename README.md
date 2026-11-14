# SwiftDrop

Parcel delivery and courier management platform for a courier company in Sri Lanka. SwiftDrop connects customers, merchants, couriers, and dispatchers across web and API interfaces backed by a modular Spring Boot microservices architecture.

---

## 1. Architecture Overview

SwiftDrop is structured as nine microservices deployed behind a single API Gateway:
- **api-gateway** (Port 8080, Management 8090): Spring Cloud Gateway (reactive); routes external traffic, security headers, rate limiting.
- **auth-service** (Port 8081): Authentication, user registration, JWT (RS256) signing & token rotation, Google OAuth, merchants.
- **parcel-service** (Port 8082): Parcel creation, lifecycle state transitions, merchant order ingestion, PDF delivery receipts.
- **pricing-service** (Port 8083): Pricing rules, district lookups, weight fee calculations, Appendix B seed data.
- **delivery-service** (Port 8084): Courier assignments, proof of delivery uploads, courier delivery execution.
- **tracking-service** (Port 8085): Public and customer parcel tracking, audit timeline events.
- **notification-service** (Port 8086): Email (Mailpit/SMTP), SMS (mock), webhook delivery engine.
- **frontend** (Vite Dev 5173, Nginx 8080): React 18 SPA with Material UI and TanStack Query.
- **common-core & common-lib**: Shared pure-Java contracts, security filters, outbox event infrastructure.

---

## 2. Prerequisites

- **Java 21** (JDK 21 LTS)
- **Node.js 22** & npm
- **Docker & Docker Compose v2** (Docker Desktop or Linux Docker Engine)
- **OpenSSL** (optional; scripts fallback to Docker alpine/openssl)
- **Git**

---

## 3. Quick Start (Local Development)

### 3.1 Initialize Environment & Generate RSA Keys
Before starting the infrastructure or services, generate the local environment file and cryptographic JWT keys:

**On Linux/macOS:**
```bash
./scripts/init-env.sh
./scripts/generate-keys.sh
```

**On Windows (PowerShell):**
```powershell
.\scripts\init-env.ps1
.\scripts\generate-keys.ps1
```

`init-env` copies `.env.example` to `.env` and fills random passwords and encryption keys.
`generate-keys` creates an RSA 2048 key pair in `infrastructure/keys/` (`swiftdrop_private.pem` and `swiftdrop_public.pem`).

### 3.2 Start Infrastructure Services
Use `compose.infra.yaml` for day-to-day development (PostgreSQL, RabbitMQ, and Mailpit):

```bash
docker compose -f compose.infra.yaml up -d
```

### 3.3 Build Backend Services
Build all services and run verification using the centralized Maven wrapper:

```bash
cd backend
./mvnw clean verify
```

To build a specific service with its dependencies:
```bash
./mvnw -pl auth-service -am verify
```

### 3.4 Start Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 4. Full-Stack Docker Deployment

To launch the complete application stack (infrastructure, all backend services, frontend, and gateway):

```bash
docker compose up -d --build
```

To include the optional `mock-merchant` webhook receiver for demo flows:

```bash
docker compose --profile demo up -d --build
```

Stop stack:
```bash
docker compose down
```

Reset all data (destroys database and message volumes):
```bash
docker compose down -v
```

---

## 5. Service URLs and Ports

| Service / Tool | URL | Credentials / Notes |
| :--- | :--- | :--- |
| **SwiftDrop Web App** | [http://localhost:8080](http://localhost:8080) | Public tracking, Customer & Admin Portal |
| **API Gateway** | [http://localhost:8080/api/v1](http://localhost:8080/api/v1) | Single entry point for APIs |
| **RabbitMQ Management** | [http://localhost:15672](http://localhost:15672) | `swiftdrop_user` / (from `.env`) |
| **Mailpit Web UI** | [http://localhost:8025](http://localhost:8025) | View outgoing emails locally |
| **Mock Merchant Receiver** | [http://localhost:9090](http://localhost:9090) | Demo webhook listener (profile: `demo`) |
| **PostgreSQL** | `127.0.0.1:5433` | Host port mapped to container port 5432 |
| **Swagger UI (Dev)** | `http://localhost:<service-port>/swagger-ui.html` | When `SWAGGER_ENABLED=true` |

---

## 6. Demo Accounts (When `DEMO_SEED_ENABLED=true`)

| Role | Email | Password |
| :--- | :--- | :--- |
| **System Admin** | `admin@swiftdrop.lk` | Defined in `BOOTSTRAP_ADMIN_PASSWORD` |
| **Dispatcher** | `dispatcher@swiftdrop.lk` | Defined in `DEMO_USER_PASSWORD` |
| **Courier** | `courier@swiftdrop.lk` | Defined in `DEMO_USER_PASSWORD` |
| **Customer** | `customer@example.com` | Defined in `DEMO_USER_PASSWORD` |
| **Merchant** | `merchant@store.lk` | Defined in `DEMO_USER_PASSWORD` |

---

## 7. Documentation & Reference Links

- Core requirements: [`docs/SRS.md`](file:///docs/SRS.md)
- Team allocations & slice boundaries: [`docs/WORK_DIVISION.md`](file:///docs/WORK_DIVISION.md)
- UI Design System tokens & layout: [`docs/UI_DESIGN_SYSTEM.md`](file:///docs/UI_DESIGN_SYSTEM.md)
- Coding standards & conventions: [`docs/SWIFTDROP_MASTER_PROMPT.md`](file:///docs/SWIFTDROP_MASTER_PROMPT.md)
- Audit log: [`docs/audit/M8-audit.md`](file:///docs/audit/M8-audit.md)
