# SwiftDrop CI/CD Pipeline

SwiftDrop's initial GitHub Actions workflow is stored at `.github/workflows/ci.yml`. It runs continuous integration checks only. It does not publish container images, deploy services, start the application stack, or access the developer's local machine.

## Triggers

The workflow runs on pushes to `main` and `develop`, and on pull requests targeting either branch. The current checkout does not contain Git repository metadata, so the existing branch names could not be inspected here. GitHub will run the workflow only for configured branches that exist in the hosted repository.

## Jobs

- **Frontend lint and build** — Uses Node.js 24 and `npm ci` with `frontend/package-lock.json`, then runs the existing `npm run lint` and `npm run build` scripts.
- **Backend matrix** — Builds and runs `verify` for each of the seven existing Maven projects: the API gateway and six microservices. It uses Java 21, each service's Maven wrapper, and Maven dependency caching. A temporary PostgreSQL 17 service with trust authentication supports the current Spring Boot context smoke tests; it is isolated to the runner and contains no project or production data. Matrix fail-fast is disabled so results are reported for every service.
- **Compose validation** — Runs `docker compose config --quiet` against the root `compose.yaml`. Compose interpolation values are generated in the runner environment for this syntax check only. This job does not start containers or write a `.env` file.

The workflow grants only `contents: read`. It contains no application credentials and has no publishing or deployment steps.

## Viewing results

After the workflow is pushed to GitHub, open the repository's **Actions** tab, select **SwiftDrop CI**, and open a run to see each job and matrix service result. Pull request checks also appear in the pull request's **Checks** section. A failed frontend command or Maven build/test fails its job and is shown in that step's logs.

## Future publishing and deployment

Docker image publishing can be added later as a separate job, for example on a version tag. That job would build images from the existing Dockerfiles, authenticate to a selected registry using GitHub Actions secrets or an approved identity-based flow, and request only the additional package permissions it needs. Deployment can then be added as a separate, environment-protected workflow with environment-specific credentials and approval rules. Neither capability is enabled in this initial pipeline.
