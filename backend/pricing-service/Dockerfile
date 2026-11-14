# Build context: backend/ (SRS 9.1, Master 8.2).
FROM eclipse-temurin:21-jdk-alpine AS build
# Required for Testcontainers when the builder uses a reachable Docker daemon.
ARG DOCKER_HOST
ARG TESTCONTAINERS_HOST_OVERRIDE
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY common-core/ common-core/
COPY common-lib/ common-lib/
COPY pricing-service/ pricing-service/
# The aggregator lists every module; copy only their POMs, never their sources.
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY tracking-service/pom.xml tracking-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY parcel-service/pom.xml parcel-service/pom.xml
RUN sh ./mvnw -B -pl pricing-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
ENV TZ=UTC
RUN apk add --no-cache wget tzdata \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/pricing-service/target/pricing-service-1.0.0.jar app.jar
USER app
EXPOSE 8083
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget --spider -q http://localhost:8083/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
