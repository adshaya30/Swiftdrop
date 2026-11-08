FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY common-core/pom.xml common-core/pom.xml
COPY common-lib/pom.xml common-lib/pom.xml
COPY delivery-service/pom.xml delivery-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY parcel-service/pom.xml parcel-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY tracking-service/pom.xml tracking-service/pom.xml
COPY common-core/src/ common-core/src/
COPY common-lib/src/ common-lib/src/
COPY delivery-service/src/ delivery-service/src/
RUN mvn -B -ntp -pl delivery-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
ENV TZ=UTC
RUN apk add --no-cache tzdata wget \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/delivery-service/target/delivery-service-1.0.0.jar app.jar
USER app
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8084/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
