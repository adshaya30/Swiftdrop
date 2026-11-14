# SwiftDrop Deployment Architecture

This document describes the multi-container Docker Compose deployment architecture for SwiftDrop (SRS Section 3.1, 9.1, DEP-001..016).

---

## 1. Container Topology & Network Overview

All services run within a single bridge network (`swiftdrop-net`). Only `api-gateway` publishes port `8080` externally to public clients and end users. All internal backend and infrastructure ports are strictly bound to `127.0.0.1` for security and local inspection (DEP-007).

```mermaid
flowchart TD
    subgraph Public["External / Public Access"]
        ClientBrowser["Web Browser (Users / Admin / Courier)"]
        ExternalAPI["External API Clients (Merchants)"]
    end

    subgraph HostGateway["Public Ingress"]
        GW["api-gateway:8080\n(Spring Cloud Gateway Reactive)\nHealth: 8090 (internal only)"]
    end

    subgraph InternalNetwork["Docker Network: swiftdrop-net (bridge)"]
        FE["frontend:8080\n(Nginx Unprivileged Alpine)\nExposed to Host: 127.0.0.1:5173"]

        AUTH["auth-service:8081\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8081"]
        PARCEL["parcel-service:8082\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8082"]
        PRICING["pricing-service:8083\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8083"]
        DELIVERY["delivery-service:8084\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8084"]
        TRACKING["tracking-service:8085\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8085"]
        NOTIF["notification-service:8086\n(Spring Boot 3.5.16)\nExposed: 127.0.0.1:8086"]

        MOCK_MCH["mock-merchant:9000\n(Profile: demo)\nExposed: 127.0.0.1:9090"]

        subgraph DataTier["Data & Message Backing Services"]
            PG[("PostgreSQL 16 Alpine\n(6 isolated DBs)\nExposed: 127.0.0.1:5433")]
            RMQ[("RabbitMQ 4.x Management\nExposed: 127.0.0.1:5672 (AMQP)\n127.0.0.1:15672 (UI)")]
            MAIL[("Mailpit SMTP\nExposed: 127.0.0.1:1025 (SMTP)\n127.0.0.1:8025 (UI)")]
        end
    end

    ClientBrowser -->|HTTP 8080| GW
    ExternalAPI -->|HTTP 8080| GW

    GW -->|Static assets & SPA fallback| FE
    GW -->|/api/v1/auth/**| AUTH
    GW -->|/api/v1/parcels/**| PARCEL
    GW -->|/api/v1/customer/**| PARCEL
    GW -->|/api/v1/merchant/orders/**| PARCEL
    GW -->|/api/v1/quotes, /public/districts| PRICING
    GW -->|/api/v1/courier/**, /admin/deliveries/**| DELIVERY
    GW -->|/api/v1/public/tracking/**| TRACKING
    GW -->|/api/v1/merchant/webhook/**| NOTIF

    AUTH --> PG
    PARCEL --> PG
    PRICING --> PG
    DELIVERY --> PG
    TRACKING --> PG
    NOTIF --> PG

    AUTH --> RMQ
    PARCEL --> RMQ
    PRICING --> RMQ
    DELIVERY --> RMQ
    TRACKING --> RMQ
    NOTIF --> RMQ

    NOTIF --> MAIL
    NOTIF -->|Webhooks| MOCK_MCH
```

---

## 2. Key Mounts and Security Boundaries (DEP-013)

Cryptographic keys (RS256 JWT) are generated before starting the stack and mounted read-only into containers at `/run/keys/`:
- **Private Key (`swiftdrop_private.pem`)**: Mounted **EXCLUSIVELY** into `auth-service`. No other container or service has access to the private signing key.
- **Public Key (`swiftdrop_public.pem`)**: Mounted read-only into all resource servers (`auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`) for JWT signature verification.

```mermaid
flowchart LR
    subgraph HostStorage["Host: infrastructure/keys/"]
        PrivKey["swiftdrop_private.pem\n(RSA 2048 Private Key)"]
        PubKey["swiftdrop_public.pem\n(RSA 2048 Public Key)"]
    end

    subgraph ContainerMounts["Container Key Mounts (/run/keys/ :ro)"]
        AUTH_CONT["auth-service\n(/run/keys/swiftdrop_private.pem:ro)\n(/run/keys/swiftdrop_public.pem:ro)"]
        PARCEL_CONT["parcel-service\n(/run/keys/swiftdrop_public.pem:ro)"]
        PRICING_CONT["pricing-service\n(/run/keys/swiftdrop_public.pem:ro)"]
        DELIVERY_CONT["delivery-service\n(/run/keys/swiftdrop_public.pem:ro)"]
        TRACKING_CONT["tracking-service\n(/run/keys/swiftdrop_public.pem:ro)"]
        NOTIF_CONT["notification-service\n(/run/keys/swiftdrop_public.pem:ro)"]
    end

    PrivKey -->|Mount ONLY here| AUTH_CONT
    PubKey --> AUTH_CONT
    PubKey --> PARCEL_CONT
    PubKey --> PRICING_CONT
    PubKey --> DELIVERY_CONT
    PubKey --> TRACKING_CONT
    PubKey --> NOTIF_CONT
```

---

## 3. Persistent Volumes (DEP-006, NFR-REL-005)

Two named Docker volumes manage persistent state across container lifecycles:
1. `pgdata`: Backs `/var/lib/postgresql/data` for PostgreSQL 16. Holds the six isolated databases:
   - `swiftdrop_auth`
   - `swiftdrop_parcel`
   - `swiftdrop_pricing`
   - `swiftdrop_delivery`
   - `swiftdrop_tracking`
   - `swiftdrop_notification`
2. `rabbitmqdata`: Backs `/var/lib/rabbitmq` for RabbitMQ 4.x broker state, queues, and message storage.

Restarting containers with `docker compose down` and `docker compose up -d` preserves all database and queue state. Full reset requires `docker compose down -v`.

---

## 4. Startup Order & Healthchecks (DEP-005)

Healthchecks use lightweight readiness probes (database connectivity for Spring services via `wget` to `/actuator/health/readiness`, and management port `8090` for `api-gateway`):

```mermaid
sequenceDiagram
    autonumber
    participant DBMsg as PostgreSQL & RabbitMQ
    participant Mail as Mailpit
    participant Backends as Backend Services (6 Java Services)
    participant FE as Frontend (Nginx)
    participant GW as API Gateway (Port 8080)

    DBMsg->>DBMsg: Healthcheck OK (pg_isready, rabbitmq ping)
    Note over DBMsg,Mail: Layer 1: Infrastructure
    Mail->>Mail: Started
    Note over Mail,Backends: Layer 2: Mail Services
    Backends->>Backends: Connect DB & Broker, Flyway migrations run
    Backends->>Backends: Healthcheck OK (/actuator/health/readiness)
    Note over Backends,FE: Layer 3: Microservice APIs
    FE->>FE: Healthcheck OK (/healthz)
    Note over FE,GW: Layer 4: Static Assets
    GW->>GW: Routes mapped, Healthcheck OK (Port 8090)
    Note over GW: Ready for User Traffic on 8080
```
