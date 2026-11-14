# Build context: backend/ (DEP-001, SRS 9.1)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY common-core/ common-core/
COPY common-lib/ common-lib/
COPY api-gateway/ api-gateway/
COPY auth-service/pom.xml auth-service/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY parcel-service/pom.xml parcel-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY tracking-service/pom.xml tracking-service/pom.xml
RUN sh ./mvnw -B -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
ENV TZ=UTC
RUN apk add --no-cache wget tzdata \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/api-gateway/target/api-gateway-1.0.0.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD wget -q --spider http://localhost:8090/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
