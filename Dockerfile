# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d

LABEL org.opencontainers.image.title="AI Learning Platform API" \
      org.opencontainers.image.description="Modular monolith backend for the AI Learning Platform" \
      org.opencontainers.image.source="https://github.com/ban2909-personally/ai-learning-api"

WORKDIR /app

COPY --from=build --chown=65532:65532 /workspace/target/ai-learning-api-*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

USER 65532:65532

ENTRYPOINT ["/usr/bin/java", "-jar", "/app/app.jar"]
