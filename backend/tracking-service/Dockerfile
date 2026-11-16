# Build context: backend/ (DEP-001, SRS 9.1)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY common-core/ common-core/
COPY common-lib/ common-lib/
COPY tracking-service/ tracking-service/
# Sibling POMs required by the reactor (sources not needed)
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY parcel-service/pom.xml parcel-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
RUN sh ./mvnw -B -pl tracking-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
ENV TZ=UTC
RUN apk add --no-cache wget tzdata \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/tracking-service/target/tracking-service-1.0.0.jar app.jar
USER app
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD wget -q --spider http://localhost:8085/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
