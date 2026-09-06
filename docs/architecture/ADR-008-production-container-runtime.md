# ADR-008 — Production container runtime baseline

Status: accepted for Phase 8.1

Date: 2026-09-06

## Context

The application has repeatable Maven verification but no repository-owned container contract. Deployments could therefore choose different JDKs, run as root, include build tools in production, inherit local credential defaults, or bypass an image build until after merge. Phase 8 needs a small reproducible runtime baseline before cloud topology, orchestration, rollout, and capacity work.

The runtime must remain portable across container platforms. Kubernetes manifests, a cloud vendor, a registry, TLS termination, external secret-manager integration, and production sizing have not been selected and must not be guessed in this slice.

## Decision

- Build the executable Spring Boot JAR in a multi-stage Dockerfile and copy only that artifact into the runtime stage.
- Pin both base images by immutable multi-platform digest while retaining readable version tags. Digest updates are explicit reviewed changes.
- Use the shell-less Distroless Java 21 Debian 12 `nonroot` runtime and set numeric user/group `65532:65532` explicitly.
- Run with `SPRING_PROFILES_ACTIVE=prod`; the production profile has no fallback for database credentials, JWT signing secret, CORS origin, MinIO credentials, or the OpenAI API key.
- Keep the JVM container-aware and percentage-based rather than hard-coding a heap size. Exit immediately on out-of-memory so the orchestrator can replace the instance.
- Enable graceful shutdown and bound the shutdown phase. Keep liveness independent of dependencies and use readiness for traffic admission.
- Do not add a Docker `HEALTHCHECK`: the runtime intentionally has no shell or HTTP client, and production orchestration must call Spring Boot probe endpoints directly.
- Exclude Git metadata, local environment files, build output, IDE state, documentation, and tests from the Docker build context.
- Add a CI container job that runs only after the complete Maven verification job, builds the image, and asserts the configured non-root user and production profile.

The builder may skip executing tests because the image job depends on the full `mvn verify` job. It still compiles and packages production code from the same checked-out revision.

## Runtime contract

- Application port: `8080` by default, configurable through `SERVER_PORT`.
- Liveness: `/actuator/health/liveness`.
- Readiness: `/actuator/health/readiness`.
- Required production variables are documented in the operations runbook and supplied at runtime, never baked into the image.
- The image contains no application source, Maven cache, compiler, package manager, shell, or local credentials.

## Consequences

The same immutable application image can move through later environments while configuration and secrets remain external. A later Phase 8 slice can add SBOM generation, vulnerability policy, signing/attestation, registry publication, orchestration manifests, backup drills, SLOs, and staged rollout without changing the application module boundaries.
