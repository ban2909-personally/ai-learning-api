# Phase 8.1 development report — Production container hardening

Date: 2026-09-06

Status: implementation and all local verification complete; remote delivery gates in progress.

## Delivered behavior

- Added a repository-owned multi-stage Docker build for the executable Spring Boot application.
- Added a shell-less Distroless Java 21 runtime that explicitly runs as numeric UID/GID `65532:65532`.
- Added the `prod` Spring profile with external-only credentials, secure refresh cookies, graceful shutdown, bounded connection/pool/thread settings, response compression, and explicit health groups.
- Added CI ordering so complete Maven verification succeeds before the production image is built and its user, profile, and entrypoint are asserted.
- Added an operator runbook covering configuration, probes, read-only filesystem, Linux capabilities, memory headroom, shutdown, and rollback constraints.
- Updated README architecture inventory and production-image entry point.

## Supply chain and image boundary

- Maven 3.9.11 with Eclipse Temurin 21 and Distroless Java 21 Debian 12 are pinned to immutable multi-platform index digests.
- Only `pom.xml` and `src/main` enter the builder; only the executable JAR enters the runtime.
- `.dockerignore` excludes Git metadata, local environment files, IDE state, target output, documentation, and tests.
- The runtime contains no application source, Maven cache, compiler, package manager, or `/bin/sh`.
- The local runtime image size is 168,918,168 bytes; sizing reduction is not claimed without dependency analysis.

## Security and runtime behavior

- Image metadata and a live process both report user `65532`; no root execution is required.
- The smoke deployment ran with a read-only root filesystem, a bounded `/tmp` tmpfs, all Linux capabilities dropped, and `no-new-privileges` enabled.
- Missing production configuration exits non-zero instead of falling back to checked-in local database/credential defaults.
- Secrets are runtime inputs and are neither added to image layers nor written to Git.
- Liveness is independent of downstream services; readiness includes PostgreSQL so traffic is removed during database outage.
- JVM heap sizing uses 75% of the container memory and leaves headroom for threads, direct buffers, and native memory.

## Verification completed so far

- Production-profile Spring/Testcontainers binding test passed after verifying Flyway V1–V12 and Hibernate schema validation.
- The binding test caught and corrected invalid duration syntax for Hikari millisecond settings before commit.
- Optimized Docker build passed and packaged all 300 production source files.
- Metadata checks passed for UID/GID, `prod` profile, absolute Java entrypoint, and immutable base digests.
- Shell absence check returned exit code 127 as expected.
- Missing-configuration startup returned exit code 1 as expected.
- Hardened live smoke test returned HTTP 200 for both liveness and readiness against an ephemeral PostgreSQL database.
- SIGTERM completed Spring/Tomcat graceful shutdown before the 35-second limit.
- All temporary smoke-test containers and the isolated Docker network were removed.
- Full `mvn verify` passed: 199 tests, zero failures/errors/skips, executable JAR produced, 236 classes analyzed, and the JaCoCo coverage gate met.
- The final pre-push image rebuild passed with a 56.41 kB build context; runtime assertions reconfirmed UID/GID `65532:65532`, the `prod` profile, the absolute entrypoint, 168,918,168-byte image size, and absence of `/bin/sh`.
- Repository diff checks passed; `pom.xml` is unchanged and the credential audit found only external environment-variable names and operator guidance.

Feature CI including its container job, merge-time gates, and exact main CI evidence remain pending. Any failed gate blocks merge.

## Cohesive commits

- `841f44c` — define the Phase 8.1 ADR/checklist and close Phase 7.3a evidence.
- `202abcc` — add the digest-pinned non-root production image.
- `e7453f0` — add the fail-closed production profile and binding test.
- `aef3ac0` — add the dependent CI image gate, runbook, and README updates.

## Explicitly deferred

- registry publication, SBOM, vulnerability policy, signing, and provenance;
- orchestrator/cloud manifests, TLS/ingress, secrets-manager adapter, and autoscaling;
- backup/restore drills, SLO targets, load capacity, dashboards, and alert routing;
- payment and invitation delivery providers.
