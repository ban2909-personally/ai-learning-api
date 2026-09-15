# Development report — Public catalog performance regression baseline

Date: 2026-09-15

Branch: `feature/performance-readiness-baseline`

## Outcome

Phase 8.4a adds a repeatable, fail-closed engineering regression gate for public
catalog reads. The workload exercises the immutable production image over real
HTTP with Redis, JPA, Flyway, and PostgreSQL, validates response bodies, controls
arrival rate, and records a compact machine-readable summary.

This slice does not change Java application source, `pom.xml`, API contracts,
Flyway migrations, persistence behavior, authorization rules, or module
dependencies. Its synthetic CI budgets are not production SLOs, capacity promises,
availability targets, or evidence for blind runtime tuning.

## Changed files

- `.dockerignore` — excludes the performance workload and evidence from the
  production image build context.
- `.github/workflows/ci.yml` — adds an independent performance job after the full
  correctness and production-image gates.
- `README.md` — links the performance operations entry point and its evidence
  boundary.
- `docs/architecture/ADR-012-performance-regression-baseline.md` — records the
  workload, thresholds, isolation model, and limits before implementation.
- `docs/architecture/phase-8.3-data-recovery-plan.md` — closes Phase 8.3b with its
  exact delivered main evidence.
- `docs/architecture/phase-8.4-performance-readiness-plan.md` — defines the
  controlled Phase 8.4a–8.4c migration slices and checklist.
- `docs/development-report-2026-09-14-minio-recovery.md` — records final Phase 8.3b
  feature/main delivery evidence.
- `docs/operations/catalog-performance-baseline.md` — documents operation,
  interpretation, failure triage, security, cleanup, and remaining decisions.
- `performance/catalog-seed.sql` — creates one deterministic instructor and 5,000
  published courses outside production migrations.
- `performance/catalog-read.js` — implements the mixed k6 workload, correctness
  checks, thresholds, and compact summary contract.
- `scripts/test-catalog-performance.sh` — orchestrates and cleans the isolated,
  hardened PostgreSQL, Redis, API, and k6 environment.

## Architecture and maintainability

- Performance fixtures and orchestration remain outside business modules and the
  shell-less runtime image. No speculative Spring bean, interface, utility,
  constant, adapter, or runtime dependency was introduced.
- Seed, workload, orchestration, and operations documentation have separate
  responsibilities. The workload calls only existing public catalog contracts.
- Synthetic records are seeded only after Flyway startup and never enter the
  application migration history or normal startup behavior.
- The JSON summary carries a versioned format marker and explicit workload/results
  fields. Missing evidence fails locally and CI.
- The CI job depends on the container-image job, so performance cannot bypass Java
  correctness, Modulith/ArchUnit, coverage, SBOM, runtime, vulnerability, or secret
  gates.

## Security and isolation

- Every run generates disposable PostgreSQL, Redis, JWT, and MinIO-placeholder
  credentials. Secret values are passed to Docker through environment names and
  are neither printed nor persisted as CI artifacts.
- PostgreSQL and Redis data use tmpfs. Redis configuration is created under
  `umask 077`. The application filesystem is read-only and its writable locations
  are explicit tmpfs mounts.
- Containers run non-root, drop all Linux capabilities, and disallow privilege
  escalation. External PostgreSQL, Redis, k6, and scanner images are pinned by
  immutable digest.
- A unique resource prefix, Docker label, exact cleanup targets, and an exit trap
  isolate concurrent runs and remove resources after success, failure, or
  interruption. The existing local development stack is not modified.
- CI retains only the compact non-secret summary for 14 days. Raw responses,
  database contents, request logs, and generated credentials are not uploaded.

## Workload and measured local evidence

The final pre-push run used 5,000 published courses, a 25-iterations-per-second
constant arrival rate for 30 seconds, and a 60/25/15 mix of popular page,
category-filtered page, and stable course-detail reads.

- Checks rate: `1.0` (all response status and body assertions passed).
- Achieved iterations: `751` (minimum accepted: `740`).
- Dropped iterations: `0`.
- HTTP request failure rate: `0`.
- Request duration p95: `14.216 ms` (budget: `< 750 ms`).
- Request duration p99: `21.761 ms` (budget: `< 1,500 ms`).
- End-to-end baseline duration: `59 seconds` including isolated service startup,
  Flyway, seeding, warm-up, workload, validation, and cleanup.

These numbers are local development evidence only. They must not be extrapolated
to production traffic, instance sizing, geographic latency, or availability.

## Verification evidence before feature push

- Bash syntax passed for PostgreSQL recovery, MinIO recovery, and catalog
  performance scripts.
- PostgreSQL positive and guarded negative recovery paths passed in 22 seconds.
- MinIO current-object positive and guarded negative recovery paths passed in 40
  seconds.
- The final performance run passed all correctness, achieved-work, error, dropped-
  iteration, p95, and p99 thresholds and left no labeled container or network.
- Full `mvn clean verify` passed all 199 tests with 0 failures, 0 errors, and 0
  skips; all 12 Flyway migrations, Spring Modulith, ArchUnit, and JaCoCo passed.
- Application SBOM validation passed as CycloneDX 1.6 with 138 components.
- Production image runtime contract passed for numeric user `65532:65532`, `prod`
  profile, and shell-less Java entrypoint.
- Image SBOM contains 153 components. The full report contains 60 findings without
  a current fixed HIGH/CRITICAL issue; the fixed HIGH/CRITICAL gate passed at 0.
- Source and image secret scans passed with no finding.
- Diff/check, executable mode, ignored evidence path, and exact resource-cleanup
  checks passed.

## Commits before feature CI evidence

- `a83f5ef` — define the performance ADR/checklist and close Phase 8.3b reporting.
- `5b2f093` — add deterministic seed data and the mixed k6 workload.
- `073695c` — add the isolated hardened performance orchestrator.
- `30f2f69` — guarantee k6 cleanup when a run is interrupted.
- `6f9bcec` — add the independent CI performance regression gate.
- `10c2950` — add the operations runbook and close local implementation items.

## Delivery status

Initial feature CI run `34948474138` passed `verify`, `container-image`,
`postgres-recovery`, `minio-recovery`, and `performance-baseline` for exact
implementation/report commit
`c7da9f930bbbc4deac1e6f3e1999faaf5a0f1cb2`. Its non-empty compact performance
artifact is `catalog-performance-c7da9f930bbbc4deac1e6f3e1999faaf5a0f1cb2`
(artifact `10388307349`, 471 bytes); application-SBOM and image-security artifacts
were also retained for the same SHA.

Final feature CI run `34949088389` passed all five jobs for exact evidence commit
`c926ac94e0ac93a4fffbfb1499e2bfec7bd1c803`.

No-fast-forward merge `f5eaaf9e39e584ea7bec3bcd2fe136487303919f`
repeated the public-catalog workload, PostgreSQL and MinIO recovery drills, all 199
Maven tests, 12 Flyway migrations, Modulith/ArchUnit/JaCoCo, runtime contract,
application/image SBOM, fixed HIGH/CRITICAL vulnerability, source/image secret,
cleanup, and clean-worktree gates before push. The merge workload achieved 750
iterations with 100% checks, no request failure or dropped iteration, p95 `15.531
ms`, and p99 `32.834 ms`.

Main CI run `34980286932` passed `verify`, `container-image`,
`postgres-recovery`, `minio-recovery`, and `performance-baseline` for that exact
merge SHA. Its compact performance artifact is
`catalog-performance-f5eaaf9e39e584ea7bec3bcd2fe136487303919f` (artifact
`10401696271`, 471 bytes); the application-SBOM and image-security artifacts were
also retained for the same SHA.

Phase 8.4a is complete. Phase 8.4b remains the next engineering slice for resource
diagnosis and broader subsystem-specific profiles. Phase 8.4c remains blocked on
real topology, traffic, quotas, business-approved SLIs/SLOs, capacity policy,
alerting, and ownership.
