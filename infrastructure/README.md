# SwiftDrop Infrastructure

This directory holds infrastructure resources that support the SwiftDrop Parcel Delivery & Courier Management System. SwiftDrop uses a React and Vite frontend, Spring Boot backend microservices, PostgreSQL for persistent data, RabbitMQ for asynchronous messaging, and Docker Compose to run the services together.

The root-level `compose.yaml` remains the main Docker Compose configuration. The folders below are reserved for service configuration, operational scripts, and monitoring resources as the project grows. No infrastructure configuration files are defined here yet.

## Directory guide

- **`docker/postgres/`** — Reserved for PostgreSQL container support files, such as initialization scripts or database-specific Docker resources. PostgreSQL is the system's relational data store.
- **`docker/rabbitmq/`** — Reserved for RabbitMQ container support files, such as broker definitions or initialization resources. RabbitMQ supports asynchronous communication between backend services.
- **`nginx/`** — Reserved for Nginx configuration if it is introduced as a reverse proxy or static frontend server. It can route browser traffic toward the frontend and API gateway.
- **`monitoring/prometheus/`** — Reserved for Prometheus scrape and monitoring configuration for collecting application and infrastructure metrics.
- **`monitoring/grafana/`** — Reserved for Grafana dashboards and provisioning resources used to visualize monitoring data.
- **`scripts/`** — Reserved for helper scripts for local development and infrastructure operations, such as setup or maintenance tasks.

## How it fits together

Docker Compose at the project root coordinates the frontend, backend microservices, PostgreSQL, and RabbitMQ. The backend services use PostgreSQL for persistent application data and RabbitMQ for message-based workflows. Nginx may be added later for edge routing, while Prometheus and Grafana may provide observability. This folder organizes supporting resources without replacing or duplicating the root Compose configuration.
