FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache wget \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar
COPY --from=build --chown=app:app /workspace/src/main/resources/certs /app/certs
ENV JWT_PRIVATE_KEY_PATH=/app/certs/private_key.pem
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
