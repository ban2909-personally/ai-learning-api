# Development report — Container supply-chain security

Date: 2026-09-12

Branch: `feature/container-supply-chain-security`

## Outcome

Phase 8.2 adds reproducible application/image inventories and a fail-closed container security gate without changing an HTTP contract, database migration, or business module boundary. Delivery to the remote repository remains pending until the remediated image passes the complete local gate.

## Changes

- Maven `verify` creates one CycloneDX 1.6 JSON application SBOM, excludes test scope, and neither creates XML nor attaches an extra artifact.
- GitHub Actions and the Dockerfile frontend use immutable revisions; checkout does not persist credentials.
- CI retains commit-scoped application/image SBOMs and a complete vulnerability report for 14 days.
- A digest-pinned Trivy 0.74.0 container scans the exported production image rather than receiving the Docker socket.
- Source, image content, and image metadata are scanned for secrets. Raw secret results are never uploaded.
- Fixed HIGH/CRITICAL findings block the build; unfixed findings remain in the full report for triage.
- The scanner script supports Linux/WSL and Git Bash path semantics.

## Vulnerability remediation

The first real scan correctly failed with 38 fixed HIGH/CRITICAL findings: 2 Debian packages and 36 Java findings. The gate was not weakened and no exception was added.

Remediation upgrades Spring Boot from 3.5.7 to 3.5.16 and resolves Jackson 2.21.4, Micrometer 1.15.12, Spring Data 3.5.13, Spring Framework 6.2.19, Spring Kafka 3.3.16, Spring Security 6.5.11, Kafka clients 3.9.2, Netty 4.1.137.Final, PostgreSQL 42.7.12, and Tomcat 10.1.59. Kafka's upgrade also replaces the affected `org.lz4:lz4-java` dependency with `at.yawk.lz4:lz4-java` 1.10.1. The runtime base moves from digest-pinned distroless Debian 12 to digest-pinned distroless Debian 13.

The narrow version overrides exist only where Spring Boot 3.5.16's managed version is below the fixed version identified by the scanner. They must be removed once a future Spring Boot BOM manages an equal or newer version.

## Verification evidence

- Pre-remediation full gate: 199 tests, 0 failures/errors/skips; all 12 Flyway migrations, Spring Modulith, ArchUnit, and JaCoCo 70% gate passed.
- Initial production image: non-root user `65532:65532`, production profile, and fixed Java entrypoint verified.
- Initial Trivy run: application/image SBOM generation and secret scans completed; the vulnerability policy failed as designed on the 38 remediable findings.
- Remediated dependency tree: all named Java libraries resolve to the intended patched versions.
- Remediated focused gate: 127 unit/architecture tests, 0 failures/errors/skips.
- Remediated application SBOM: CycloneDX 1.6 JSON with 138 components; no XML output.

## Pending delivery gates

Docker Desktop 4.44.2 currently crashes while removing the stale Windows AF_UNIX socket `dockerInference` (`Error 1920`). The failed Maven attempt skipped 66 Docker-backed tests and consequently failed coverage at 48%; it is intentionally not accepted as verification evidence.

After Docker is restored, the required sequence is: clean Maven verification with no skipped tests, production image rebuild, runtime assertions, source/image secret scans, final HIGH/CRITICAL scan, SBOM validation, diff/secret audit, cohesive commits, feature push and exact CI success, no-fast-forward merge, repeat local gates on `main`, push `main`, and exact main CI success.

No commit containing the implementation is pushed or merged while these gates are pending.
