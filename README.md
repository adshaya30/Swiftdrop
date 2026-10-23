# SwiftDrop Environment Setup

## Local environment variables

Docker Compose reads the root `.env` file for PostgreSQL and RabbitMQ credentials. Start by copying the safe template if you do not already have a local `.env` file:

```powershell
if (Test-Path .env) {
    Write-Host ".env already exists. Back it up or edit it carefully; this command will not overwrite it."
} else {
    Copy-Item .env.example .env
}
```

Replace the placeholder values in `.env` with credentials for local development. Keep `.env` private and never commit it. Commit `.env.example` so teammates can see which variables the project requires; it contains placeholders, not credentials.

Compose passes database and messaging connection settings to the backend services. Containers connect to PostgreSQL and RabbitMQ using the Compose hostnames `postgres:5432` and `rabbitmq:5672`. The frontend API URL is configured separately in Compose and is not a root `.env` setting.
