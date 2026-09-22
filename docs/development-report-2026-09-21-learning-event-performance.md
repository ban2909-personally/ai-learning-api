# Development report — Durable learning-event performance

Date: 2026-09-21

Branch: `feature/learning-event-performance`

## Outcome

Phase 8.4b3 now has a production-image-bound regression profile for the durable
lesson-completion pipeline: authenticated HTTP completion, transactional outbox,
Kafka dispatch, analytics projection, and durable notification projection. The
workload implementation changes no Java production source, dependency, API/event
contract, authorization rule, Flyway migration, index, cache, pool, thread, or
runtime tuning.
One independent dependency-management patch upgrades transitive Bouncy Castle from
`1.84` to `1.85` after the current vulnerability database rejected the former.

## Architecture and maintainability

- ADR-015 fixes workload, broker, drain, evidence, security, and non-goal boundaries
  before orchestration code.
- The fixture is performance-only data outside Flyway. The workload uses existing
  registration, enrollment, and progress APIs and guarantees unique completion
  transitions across forty students and eight lessons.
- The existing resource collector gained one explicit `learning-event` mode instead
  of a parallel copy. Catalog and authenticated-learning formats remain unchanged
  and were rerun after the extension.
- The harness owns exact generated resource names, explicit source/DLT topics,
  bounded database drain, bounded Kafka CLI probes, exact event-ID reconciliation,
  and three versioned summaries.
- CI runs the scenario as an independent job after the production-image gate and
  retains only compact non-secret evidence.

## Security and data lifecycle

- Kafka has no host port, disables topic auto-creation, and uses a private network.
  PostgreSQL, Redis, and Kafka state is tmpfs-backed.
- Application and k6 filesystems are read-only; capabilities are dropped; privilege
  escalation is disabled; application and broker resources are bounded.
- Metrics remain JWT-protected and an unauthenticated request must return 401.
  Generated passwords and JWTs never enter retained files or command values.
- Cleanup removes only exact generated containers/network. Raw samples, container
  state, identities, enrollment/progress data, and broker logs are never artifacts.

## Local implementation evidence

- Final learning-event run achieved `241` iterations with checks `1.0`, request
  failures/drops `0`, p95 `28.817 ms`, and p99 `35.812 ms`.
- Database and broker evidence matched exactly: `241` completed progress rows,
  outbox rows, published rows, source offsets, analytics facts, and notifications.
  Drain was already complete at the first bounded probe; pending rows, attempts,
  locks, failure codes, event-ID mismatches, consumer lag, and DLT offsets were all
  zero.
- Final application counters were `241` published, `241` analytics projected, and
  `241` notifications projected. Failure, duplicate, rejection, and DLT counters
  were zero. Resource evidence captured eighteen samples across a 70-second span.
- Catalog default mode and resource mode both passed `751` iterations with zero
  failures/drops. The retained resource run recorded p95 `18.819 ms`, p99
  `49.400 ms`, ten samples, and the unchanged catalog format.
- Authenticated read passed `600` iterations with p95 `33.627 ms`, p99 `51.805 ms`,
  and twelve samples. Write passed `300` iterations with p95 `28.898 ms`, p99
  `36.523 ms`, and thirteen samples. Both had zero failures/drops and unchanged
  authenticated resource formats.
- All successful profiles referenced production image
  `sha256:6c0bb3a222a5fff6b0fa5a6a14c24dfa0e1258250a9ef3c86adbf29294f79a09`
  and left no labeled performance container or network.

## Full local pre-delivery gates

- `mvn clean verify` passed all `199` tests with no failures, errors, or skips;
  all twelve Flyway migrations, Modulith boundaries, ArchUnit rules, and JaCoCo
  checks passed. The CycloneDX 1.6 application SBOM contains 138 components.
- The rebuilt production image runs as `65532:65532`, activates the `prod` profile,
  and has the fixed `/usr/bin/java -jar /app/app.jar` entrypoint. Its CycloneDX 1.7
  SBOM contains 153 components.
- The first current-database Trivy scan rejected transitive
  `org.bouncycastle:bcprov-jdk18on:1.84` for CVE-2026-8763 and CVE-2026-13506.
  Dependency management now selects `1.85`; MinIO integration and the full suite
  passed afterward. The final report has zero fixable HIGH/CRITICAL findings and
  both source and image secret scans passed.
- PostgreSQL recovery passed confirmation, non-empty-target, and checksum guards
  before an exact restore. MinIO recovery passed confirmation, prefix reuse,
  non-empty-target, inventory checksum/count, and missing/extra/different-object
  guards before exact object verification.

## Harness defects found and corrected

- The first complete workload exposed that Kafka CLI probes inside every database
  poll made the drain check unnecessarily slow. Database drain and final broker
  evidence are now separate, and every Kafka CLI probe has bounded retries and a
  fifteen-second process timeout.
- The second run exposed a parser error: `kafka-consumer-groups` prints `GROUP`
  before `TOPIC`, so topic matching belongs to column two. The parser was corrected
  without removing or weakening the zero-lag gate.
- The third run passed the unchanged workload and all exact invariants. Failed-run
  resources were verified and removed by exact generated names before retrying.

## Cohesive commits before delivery gates

- `a23174b` — define ADR-015 and the Phase 8.4b3 boundary.
- `e0d1105` — add the deterministic fixture and unique completion workload.
- `6784897` — add the shared diagnostics extension and fail-closed event harness.
- `e2bc524` — add the independent CI gate and three-artifact retention contract.
- `865d874` — add the operational runbook and troubleshooting workflow.
- `274c6bd` — select Bouncy Castle 1.85 after the live vulnerability gate rejected
  the vulnerable transitive release.

## Delivery status

Implementation and subsystem regression evidence are complete. The remaining gate
is the standard full local suite, exact feature CI, no-fast-forward merge, repeated
local merge suite, and exact main CI. This report and the performance-readiness plan
must be updated with immutable SHAs, run IDs, and artifact IDs after those gates pass.

No production capacity or SLO conclusion is made. Phase 8.4c remains dependent on
business-approved traffic forecasts, deployment topology, instance sizing,
downstream quotas, error budgets, and operational ownership.
