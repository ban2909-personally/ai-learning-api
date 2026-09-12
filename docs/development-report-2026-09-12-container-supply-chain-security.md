# Development report — Container supply-chain security

Date: 2026-09-12

Branch: `feature/container-supply-chain-security`

## Outcome

Phase 8.2 adds reproducible application/image inventories and a fail-closed container security gate without changing an HTTP contract, database migration, or business module boundary. The remediated image now passes the complete local gate; remote branch and main delivery remain pending.

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
- Remediated full gate: Maven `clean verify` passed all 199 tests with 0 failures/errors/skips, all 12 Flyway migrations, Spring Modulith, ArchUnit, and the JaCoCo 70% gate.
- Remediated application SBOM: CycloneDX 1.6 JSON with 138 components; no XML output. The image SBOM is CycloneDX 1.7 with 153 components.
- The complete vulnerability artifact retains 60 findings for triage, including 6 HIGH/CRITICAL findings without a fix. The blocking policy found 0 fixed HIGH/CRITICAL vulnerabilities in both Debian 13.6 and the application JAR.
- Source and image/config secret scans completed without a detected secret.
- The production image passed a real startup smoke test as user `65532:65532` with read-only root filesystem, all capabilities dropped, and `no-new-privileges`. Readiness returned `UP` after all 12 Flyway migrations ran against an isolated PostgreSQL 17 instance on tmpfs.

## Pending delivery gates

The local implementation is split into cohesive documentation, build/dependency, CI/scanner, and verification-report commits. None has been pushed or merged yet.

The remaining required sequence is: final diff/secret audit, feature push and exact CI success, no-fast-forward merge, repeat local Maven/container/security gates on `main`, push `main`, and exact main CI success.
