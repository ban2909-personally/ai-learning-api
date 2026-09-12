# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM gcr.io/distroless/java21-debian13:nonroot@sha256:bb0b3c7edc4417acdf76ea0f52bb5fae28881fe05aae6cc55af4cc4cb0200d2d

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
