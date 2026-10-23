# SwiftDrop — Parcel Delivery & Courier Management System

A containerized parcel delivery and courier management platform designed to manage parcel creation, pricing, delivery assignments, shipment tracking, and customer notifications through a microservices-based architecture.

## 📌 Table of Contents

- [Project Overview](#-project-overview)
- [Objectives](#-objectives)
- [Key Features](#-key-features)
- [System Architecture](#-system-architecture)
- [Project Structure](#-project-structure)
- [Technology Stack](#-technology-stack)
- [Microservices](#-microservices)
- [Getting Started](#-getting-started)
- [Environment Configuration](#-environment-configuration)
- [Running the Application](#-running-the-application)
- [Frontend Development](#-frontend-development)
- [Backend Development](#-backend-development)
- [API Access](#-api-access)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Troubleshooting](#-troubleshooting)
- [Security](#-security)
- [Future Improvements](#-future-improvements)

## 🚀 Project Overview

SwiftDrop is a Parcel Delivery & Courier Management System developed to simplify and organize parcel delivery operations.

The system is designed to support customers and merchants who send parcels, dispatchers who coordinate deliveries, and couriers who handle parcel transportation. It uses independent backend microservices to separate business responsibilities and improve maintainability.

Docker Compose is used to run the application services and supporting infrastructure in a consistent development environment.

## 🎯 Objectives

- Develop a functional parcel delivery management application.
- Implement a modular microservices architecture.
- Provide secure authentication and authorization.
- Manage parcel information and delivery pricing.
- Support courier assignment and delivery status updates.
- Enable shipment tracking.
- Use asynchronous messaging for communication between services.
- Containerize application components using Docker.
- Automate code validation and builds using GitHub Actions.

## ✨ Key Features

The intended system capabilities include:

- User registration and authentication.
- Parcel creation and management.
- Parcel pricing calculation.
- Delivery assignment and status management.
- Shipment tracking and delivery progress updates.
- Notifications for relevant delivery events.
- REST API communication through an API Gateway.
- PostgreSQL database persistence.
- RabbitMQ-based asynchronous communication.
- Docker-based development and deployment.
- Automated CI checks through GitHub Actions.

*The availability of each feature depends on the implementation status of its corresponding service.*

## 🏗️ System Architecture

SwiftDrop follows a microservices architecture in which separate backend services handle different business responsibilities.

```text
                  Customers / Merchants / Staff
                              |
                              v
                     React Frontend
                              |
                              v
                        API Gateway
                              |
           +------------------+------------------+
           |                  |                  |
           v                  v                  v
      Auth Service      Parcel Service     Pricing Service
           |                  |                  |
           +------------------+------------------+
                              |
                 +------------+------------+
                 |                         |
                 v                         v
          Delivery Service          Tracking Service
                 |                         |
                 +------------+------------+
                              |
                              v
                    Notification Service

             PostgreSQL <--> Backend Services
             RabbitMQ   <--> Event-Driven Messaging

                Docker Compose
          Runs the application containers
```

The diagram represents the intended logical architecture. Actual database connections and messaging integrations depend on the implemented service configurations.

### How the system works

1. A user interacts with the React frontend.
2. The frontend sends HTTP requests to the API Gateway.
3. The gateway routes requests to the appropriate backend service.
4. Backend services execute business logic and interact with their configured data stores.
5. RabbitMQ can distribute asynchronous events between services.
6. The tracking and notification services can process relevant delivery events.
7. The frontend displays the responses to the user.

## 📁 Project Structure

```text
Swiftdrop/
│
├── frontend/                         # React + Vite application
│   ├── public/
│   ├── src/
│   │   ├── api/                      # API client configuration
│   │   ├── assets/                   # Images, icons and other assets
│   │   ├── components/               # Reusable UI components
│   │   ├── layouts/                  # Shared page layouts
│   │   ├── pages/                    # Application pages
│   │   ├── services/                 # API service functions
│   │   ├── hooks/                    # Custom React hooks
│   │   ├── context/                  # Shared React state
│   │   ├── utils/                    # Helper functions
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── index.css
│   ├── .env
│   ├── Dockerfile
│   └── package.json
│
├── backend/                          # Spring Boot microservices
│   ├── api-gateway/
│   ├── auth-service/
│   ├── parcel-service/
│   ├── pricing-service/
│   ├── delivery-service/
│   ├── tracking-service/
│   └── notification-service/
│
├── infrastructure/                   # Infrastructure configuration
│   ├── docker/
│   │   ├── postgres/
│   │   └── rabbitmq/
│   ├── nginx/
│   ├── monitoring/
│   │   ├── prometheus/
│   │   └── grafana/
│   ├── scripts/
│   ├── README.md
│   └── CI-CD.md
│
├── .github/
│   └── workflows/
│       └── ci.yml                    # GitHub Actions workflow
│
├── compose.yaml                      # Main Docker Compose configuration
├── .env                              # Local environment variables
├── .gitignore
└── README.md                         # Project documentation
```

*This is the intended logical structure. Keep the actual service and configuration paths consistent with the files in your repository.*

### Folder responsibilities

| Directory or file | Purpose |
|---|---|
| `frontend/` | User interface and API integration |
| `backend/` | Backend microservices and business logic |
| `api-gateway/` | Central entry point for API requests |
| `auth-service/` | Authentication and user access |
| `parcel-service/` | Parcel management |
| `pricing-service/` | Delivery pricing calculations |
| `delivery-service/` | Delivery coordination and assignments |
| `tracking-service/` | Tracking information and progress |
| `notification-service/` | Notification processing |
| `infrastructure/` | Supporting configuration, scripts, and monitoring |
| `.github/workflows/` | Automated CI/CD workflow definitions |
| `compose.yaml` | Defines and connects Docker containers |
| `.env` | Local configuration and secrets |
| `README.md` | Project documentation |

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| React | Frontend user interface |
| Vite | Frontend development server and build tool |
| Java 21 | Backend programming language |
| Spring Boot | Backend microservices and REST APIs |
| PostgreSQL | Relational database |
| RabbitMQ | Asynchronous messaging |
| Docker | Application containerization |
| Docker Compose | Multi-container orchestration |
| Git and GitHub | Source control and collaboration |
| GitHub Actions | Continuous integration and automated validation |
| Nginx | Optional production web server and reverse proxy |
| Prometheus and Grafana | Optional monitoring and dashboards |

## 🔧 Microservices

### 1. API Gateway

Provides a common entry point for frontend requests and routes requests to backend services.

### 2. Authentication Service

Handles user authentication and related identity operations.

### 3. Parcel Service

Manages parcel information, including parcel creation and relevant parcel details.

### 4. Pricing Service

Calculates delivery prices based on the configured pricing rules.

### 5. Delivery Service

Manages delivery assignments and delivery workflow information.

### 6. Tracking Service

Maintains and provides parcel tracking information and delivery progress.

### 7. Notification Service

Processes notifications associated with relevant system events.

## ⚙️ Getting Started

### Prerequisites

Install the following software before starting development:

- [Git](https://git-scm.com/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Node.js](https://nodejs.org/) and npm, if running the frontend directly on your computer
- [Java 21](https://adoptium.net/), if building backend services directly on your computer
- Maven or Gradle, depending on the build tool configured in each backend service

Start Docker Desktop and wait until its Docker Engine is running.

### Clone the repository

If you have not cloned the project yet:

```bash
git clone <YOUR_REPOSITORY_URL>
cd Swiftdrop
```

Replace `<YOUR_REPOSITORY_URL>` with the actual GitHub repository URL.

If the repository is already on your computer, open a terminal in its root directory instead.

## 🔐 Environment Configuration

Create the required environment files based on the configuration expected by the application.

### Root `.env`

The root `.env` file stores environment variables used by Docker Compose and backend services.

Example variable names:

```dotenv
POSTGRES_DB=swiftdrop
POSTGRES_USER=swiftdrop_user
POSTGRES_PASSWORD=replace_with_a_local_password

RABBITMQ_DEFAULT_USER=swiftdrop_user
RABBITMQ_DEFAULT_PASS=replace_with_a_local_password
```

These are illustrative examples. Check `compose.yaml` and the service configurations to determine the actual required variable names.

### Frontend `.env`

The frontend can use the following Vite environment variable when the API Gateway is available on the host:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Restart the Vite development server after changing environment variables.

**Important:** Never commit real passwords, access tokens, private keys, or production secrets to GitHub.

## 🐳 Running the Application

Run the following commands from the project root.

### 1. Validate Docker Compose

```powershell
docker compose config
```

This checks whether the Compose configuration can be resolved. It does not guarantee that every application service will start successfully.

### 2. Build and start the containers

```powershell
docker compose up -d --build
```

This builds the configured images and starts the containers in the background.

### 3. Check container status

```powershell
docker compose ps
```

Wait for application services to finish starting. A health status of `starting` is not the same as `healthy`.

### 4. View logs

```powershell
docker compose logs -f
```

To view logs for one service:

```powershell
docker compose logs -f api-gateway
```

Use `Ctrl + C` to stop following the logs. This does not stop the containers.

### 5. Stop the containers

```powershell
docker compose down
```

This stops and removes the Compose containers and network. It normally preserves named database volumes.

**Avoid `docker compose down -v` unless you intentionally want to remove the associated named volumes and their stored data.**

## 🖥️ Frontend Development

To run the frontend directly on your computer:

```powershell
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite, usually:

`http://localhost:5173`

For a production build:

```powershell
npm run build
```

The generated production files are typically placed in `frontend/dist/`.

## ☕ Backend Development

The backend consists of Spring Boot microservices. Each service should be built using the build tool and configuration already present in its directory.

### Maven example

From a service directory containing `pom.xml`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

### Gradle example

From a service directory containing `gradlew.bat`:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Use the wrapper included with each service when available. Do not assume every service uses the same build tool.

For local development, ensure the required database, RabbitMQ instance, environment variables, and dependent services are configured before starting a service.

## 🌐 API Access

When the containers are running with the configured port mappings, the following local addresses are expected:

| Component | Local address |
|---|---|
| Frontend development server | `http://localhost:5173` |
| API Gateway | `http://localhost:8080` |
| Authentication Service | `http://localhost:8081` |
| Parcel Service | `http://localhost:8082` |
| Pricing Service | `http://localhost:8083` |
| Delivery Service | `http://localhost:8084` |
| Tracking Service | `http://localhost:8085` |
| Notification Service | `http://localhost:8086` |
| RabbitMQ Management UI | `http://localhost:15672` |
| PostgreSQL | `localhost:5433` |

These addresses assume the corresponding ports are mapped as configured in the root Compose file.

Use the actual routes defined by the API Gateway and individual services when testing API endpoints. A service being available on a port does not mean every endpoint is implemented.

## 🔄 CI/CD Pipeline

SwiftDrop uses GitHub Actions to automate development checks.

Workflow location:

```text
.github/workflows/ci.yml
```

### Continuous Integration (CI)

The CI workflow can:

1. Check out the repository.
2. Set up the required Node.js and Java versions.
3. Install frontend dependencies.
4. Run frontend linting and production builds.
5. Compile backend microservices and run available tests.
6. Validate the Docker Compose configuration.
7. Report success or failure in GitHub Actions.

### Continuous Delivery / Deployment (CD)

The project can be extended to:

- Build Docker images for the frontend and backend services.
- Publish images to a container registry.
- Deploy images to a server or cloud platform.
- Run deployment checks after release.

Deployment should be enabled only after the target environment and required secrets have been configured.

### Viewing workflow results

1. Push your changes to GitHub.
2. Open the repository on GitHub.
3. Select the **Actions** tab.
4. Open the relevant workflow run.
5. Review successful jobs or inspect logs for failed jobs.

The workflow only performs the stages implemented in its YAML configuration.

## 🧪 Troubleshooting

### Docker Engine is not running

If Docker reports that it cannot connect to the Docker API, open Docker Desktop and wait until the engine is ready.

### Port already in use

Check the process using a port:

```powershell
netstat -ano | findstr :8080
```

If a port is occupied, identify the process before deciding whether to stop it or change the port mapping.

### Container is unhealthy

Inspect its status and logs:

```powershell
docker compose ps
docker compose logs --tail=100 notification-service
```

Replace the service name with the affected service when needed. Check the health-check configuration and any missing dependencies.

### Frontend cannot reach the backend

Check that:

- The API Gateway is running.
- `VITE_API_BASE_URL` points to the correct host URL.
- The frontend origin is allowed by backend CORS configuration.
- The requested API route exists.
- Browser developer tools do not show network or CORS errors.

When the frontend runs in a browser on your computer, `localhost` refers to your computer. Container-to-container communication instead uses Compose service names and internal container ports.

### Database connection failure

Check PostgreSQL health, database credentials, and service configuration.

Inside the Docker network, services should generally use the Compose service hostname and internal database port. For connections from your computer, use the configured host port.

### CI workflow fails

Open the failed GitHub Actions job and inspect its logs. Confirm the correct Java and Node.js versions, dependency lockfiles, build scripts, and service paths.

## 🔒 Security

- Keep `.env` files out of version control.
- Use GitHub Secrets or an appropriate secret manager for CI/CD credentials.
- Apply authentication and authorization to protected API endpoints.
- Restrict database and management interfaces in production.
- Avoid exposing PostgreSQL and RabbitMQ management ports publicly.
- Use HTTPS for production traffic.
- Use least-privilege permissions for CI workflows and deployment credentials.
- Review dependencies and container images regularly.

## 🚧 Future Improvements

Potential enhancements include:

- Role-based access for customers, merchants, dispatchers, and couriers.
- Real-time tracking updates.
- Improved notification delivery and retry handling.
- Automated backend and frontend integration tests.
- API documentation using OpenAPI/Swagger.
- Prometheus metrics and Grafana dashboards.
- Production Nginx configuration.
- Automated Docker image publishing.
- Automated deployment to a staging or production environment.

## 👩‍💻 Development Workflow

A recommended collaborative workflow is:

1. Create a feature branch.
2. Implement the feature.
3. Run relevant tests locally.
4. Commit changes with a descriptive message.
5. Push the feature branch to GitHub.
6. Open a pull request.
7. Review the GitHub Actions results.
8. Merge after required checks and reviews pass.

Example:

```bash
git checkout -b feature/parcel-management
git add .
git commit -m "feat: implement parcel management"
git push -u origin feature/parcel-management
```

Use the branch naming conventions agreed upon by your team.

## 📄 License

Add the license selected for this project here. If the project is for a university assignment and does not have a license yet, do not claim that it is released under an open-source license.

---

**SwiftDrop — Making parcel delivery management more organized, reliable, and efficient.**
