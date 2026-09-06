# Production container runtime operations

## Purpose

This runbook defines the portable Phase 8.1 image contract. It does not define a cloud provider, orchestrator, ingress, registry, backup policy, or production capacity values.

## Build and inspect

```powershell
docker build --tag ai-learning-api:local .
docker image inspect --format '{{.Config.User}}' ai-learning-api:local
docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' ai-learning-api:local
docker image inspect --format '{{json .Config.Entrypoint}}' ai-learning-api:local
```

Expected invariants:

- user is `65532:65532`;
- `SPRING_PROFILES_ACTIVE=prod` is present;
- entrypoint is `["/usr/bin/java","-jar","/app/app.jar"]`;
- application source, tests, Git metadata, local environment files, and build tooling are absent.

The Dockerfile pins readable image tags to immutable multi-platform digests. Updating Java, Maven, Debian, or a base-image security fix requires reviewing and replacing those digests, rebuilding, and running every gate again.

## Required production configuration

Supply secrets through the deployment platform at runtime. Never place them in the image, Dockerfile, Git, CI logs, command history, or a checked-in environment file.

| Variable | Purpose | Requirement |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | TLS and certificate validation must be configured for the selected provider. |
| `DB_USERNAME` | PostgreSQL application user | Use least privilege for application schema operations. |
| `DB_PASSWORD` | PostgreSQL password | Secret; rotate through the deployment secret store. |
| `JWT_SECRET` | Base64 HMAC signing key | Secret; decoded length must be at least 32 bytes. |
| `JWT_ISSUER` | Access-token issuer | Stable environment-specific identifier. |
| `CORS_ALLOWED_ORIGIN` | Trusted browser origin | One exact HTTPS origin; no wildcard with credentials. |
| `REDIS_HOST` | Redis endpoint | Private authenticated endpoint. |
| `REDIS_PASSWORD` | Redis credential | Secret; do not use unauthenticated production Redis. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap list | Private authenticated/TLS endpoints when event delivery is enabled. |
| `MINIO_ENDPOINT` | S3/MinIO endpoint | HTTPS endpoint reachable by the application. |
| `MINIO_ACCESS_KEY` | Object-storage identity | Secret/credential with bucket-scoped access. |
| `MINIO_SECRET_KEY` | Object-storage secret | Secret; rotate through the deployment secret store. |
| `MINIO_MEDIA_BUCKET` | Lesson-media bucket | Existing private bucket. |
| `OPENAI_API_KEY` | AI mentor provider key | Secret; scoped and monitored for usage. |

The production profile deliberately fails placeholder resolution when required values are absent. Local defaults remain confined to the default development profile.

## Optional bounded tuning

The defaults are conservative starting points, not production capacity claims:

- `DB_POOL_MAX_SIZE=20`, `DB_POOL_MIN_IDLE=5`;
- `DB_CONNECTION_TIMEOUT_MS=5000`, `DB_VALIDATION_TIMEOUT_MS=3000`;
- `REDIS_CONNECT_TIMEOUT=3s`, `REDIS_COMMAND_TIMEOUT=3s`;
- `HTTP_CONNECTION_TIMEOUT=5s`, `HTTP_KEEP_ALIVE_TIMEOUT=20s`;
- `HTTP_MAX_CONNECTIONS=8192`, `HTTP_ACCEPT_COUNT=100`;
- `HTTP_MAX_THREADS=200`, `HTTP_MIN_SPARE_THREADS=10`;
- `SHUTDOWN_TIMEOUT=30s`;
- `SERVER_PORT=8080`.

Change these only with load evidence and database/provider limits. The JVM uses `MaxRAMPercentage=75` so native memory, threads, direct buffers, and the container runtime retain headroom.

## Probes and dependency behavior

- Liveness: `GET /actuator/health/liveness`; includes process state and ping only. Dependency outages must not cause restart loops.
- Readiness: `GET /actuator/health/readiness`; includes application readiness and PostgreSQL. A database outage removes the instance from traffic.
- Redis is intentionally not a readiness dependency: catalog cache reads fall back to PostgreSQL, while quota failures fail closed at the affected AI operation.
- Kafka dispatch/consumers are disabled unless their explicit feature flags are enabled. A deployment enabling them must also configure broker security outside this repository.

The Distroless image has no shell or HTTP client, so no Docker `HEALTHCHECK` is embedded. Configure the platform's native HTTP probes against these endpoints.

## Filesystem and process security

Run with a read-only root filesystem and an empty writable temporary mount at `/tmp`. Do not add Linux capabilities. Apply the platform's default seccomp profile and disallow privilege escalation. The application writes durable state only to PostgreSQL, Redis, Kafka, and object storage; container-local files are ephemeral.

## Shutdown and rollback

The production profile uses graceful shutdown with a bounded phase timeout. The orchestrator must stop routing traffic before sending `SIGTERM` and allow at least the configured shutdown timeout before `SIGKILL`.

Roll back by redeploying the previously verified immutable image digest. Database migrations are forward-only; inspect every Flyway migration for backward application compatibility before rollout. Automated database rollback is not claimed by this slice.
