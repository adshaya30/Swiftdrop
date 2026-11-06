FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
RUN apk add --no-cache maven
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
ENV TZ=UTC

RUN apk add --no-cache wget tzdata \
    && cp /usr/share/zoneinfo/UTC /etc/localtime \
    && echo "UTC" > /etc/timezone \
    && addgroup -S app \
    && adduser -S app -G app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar
USER app

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
  CMD wget --spider -q http://localhost:8081/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
