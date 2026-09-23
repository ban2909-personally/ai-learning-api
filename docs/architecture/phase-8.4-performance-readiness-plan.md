# Phase 8.4 — Performance readiness

## Goal

Build repeatable, evidence-based performance controls without confusing CI smoke
capacity with production promises or tuning the system without diagnosis.

## Phase 8.3b delivery prerequisite

Phase 8.3b completed at no-fast-forward merge
`039e1a07f043019b0a1687b2351efaf1b55a54cf`. Main CI run `34944318649`
passed `verify`, `container-image`, `postgres-recovery`, and `minio-recovery` for that
exact SHA after all local merge gates passed.

## Controlled slices

### Phase 8.4a — Public catalog regression baseline

- [x] Reconfirm local/remote main synchronization and exact Phase 8.3b main CI.
- [x] Inspect public endpoints, query/index path, Redis behavior, production runtime
  bounds, and security exposure before choosing a workload.
- [x] Record workload, dataset, thresholds, evidence limits, and container controls
  before implementation.
- [x] Add deterministic representative seed data outside application migrations.
- [x] Add a mixed k6 workload and fail-closed Docker orchestration script.
- [x] Validate response correctness, achieved iterations, errors, latency quantiles,
  resource cleanup, and summary content locally.
- [x] Add an independent CI job after correctness and production-image gates.
- [x] Document operation, interpretation, tuning discipline, security, and measured
  local results.
- [x] Record exact feature and main CI performance results for their delivered SHAs.
- [x] Pass full local gates, cohesive commits, exact feature CI, no-fast-forward
  merge, repeated merge gates, exact main CI, and delivery reporting.

### Phase 8.4b — Broader capacity and resource diagnosis

- [x] Record a resource-diagnostics ADR and keep the existing regression workload
  unchanged by making observation opt-in and independently gated.
- [x] Sample application, PostgreSQL, and Redis container CPU/memory plus application
  PID, JVM-memory, and Hikari active/pending evidence during the catalog workload.
- [x] Capture final application cache counters, PostgreSQL database counters, and
  Redis keyspace/memory counters in a versioned compact non-secret summary.
- [x] Keep temporary Actuator metrics JWT-protected and reachable only through the
  isolated loopback-bound test runtime; never alter production exposure defaults.
- [x] Validate local diagnostic evidence, failure behavior, cleanup, default-mode
  isolation, and add an independent CI job without inventing resource limits.
- [x] Pass the diagnostics job and retain both compact summaries for exact feature
  and main delivery SHAs.
- [x] Document the diagnosis, comparison, security, and cleanup workflow.
- [x] Pass full local gates, exact feature CI, no-fast-forward merge, repeated merge
  gates, exact main CI, and final delivery reporting.
- [x] Add authenticated read/write profiles only with isolated identities and safe
  deterministic cleanup. Tracked as Phase 8.4b2 below.
- [ ] Cover event throughput, WebSocket fan-out, AI concurrency/cost bounds, and
  media bandwidth with subsystem-specific scenarios rather than one mixed number.
- [ ] Capture CPU, memory, JVM, connection-pool, database, and cache evidence so
  tuning changes have a diagnosed bottleneck.
- [ ] Add controlled spike, saturation, recovery, and soak exercises outside normal
  per-push CI.

### Phase 8.4b2 — Authenticated learning read/write profiles

- [x] Record the profile boundaries, identity isolation, workload rates, response
  assertions, evidence limits, and non-goals before implementation.
- [x] Add a deterministic free-course/lesson fixture outside Flyway and validate it
  before traffic begins.
- [x] Register and enroll isolated identities through the existing HTTP contracts;
  keep generated credentials and JWTs out of commands, logs, files, and artifacts.
- [x] Add separate authenticated read and progress-write k6 profiles with exact
  correctness, achieved-work, failure, dropped-work, and latency gates.
- [x] Capture compact production-image-bound application/JVM/Hikari, PostgreSQL,
  Redis, and container evidence for each profile without speculative resource limits.
- [x] Add fail-closed orchestration, exact cleanup checks, CI isolation, artifact
  retention, and an operations runbook.
- [x] Pass full local gates, exact feature CI, no-fast-forward merge, repeated merge
  gates, exact main CI, and final delivery reporting.

### Phase 8.4b3 — Durable learning-event throughput

- [x] Record the production-image boundary, unique completion workload, regression
  budgets, drain semantics, exact invariants, evidence limits, and non-goals before
  implementation.
- [x] Add a deterministic free-course/eight-lesson fixture outside Flyway and
  validate it before traffic.
- [x] Start an internal-only disposable Kafka broker with explicit source and DLT
  topics; enable the existing outbox, analytics, and notification adapters only for
  the isolated profile.
- [x] Drive unique authenticated completion transitions at a fixed arrival rate and
  gate correctness, achieved work, failures, dropped work, and HTTP latency.
- [x] Prove bounded end-to-end drain with exact outbox/progress/projection/event-id
  counts, source offsets, zero consumer lag, zero DLT records, and zero retries.
- [x] Extend the shared collector with application/JVM/Hikari, PostgreSQL, Redis,
  Kafka, backlog, and final pipeline-counter evidence without speculative limits.
- [x] Add fail-closed cleanup, compact artifact schemas, CI isolation/retention, and
  an operations runbook.
- [x] Pass full local gates, exact feature CI, no-fast-forward merge, repeated merge
  gates, exact main CI, and final delivery reporting.

### Phase 8.4b4 — Authenticated notification WebSocket fan-out

- [x] Record the single-instance topology, authenticated fan-out workload,
  regression budgets, durable catch-up boundary, evidence limits, and non-goals
  before implementation.
- [x] Add low-cardinality active-session observability to the existing WebSocket
  registry with focused lifecycle tests and no new business abstraction.
- [x] Add a deterministic free-course/lesson fixture outside Flyway and an isolated
  k6 STOMP workload for forty identities with two sessions per identity.
- [x] Exercise unique completions only after the connection window and gate exact
  upgrade, CONNECT, subscription, completion, per-session delivery, duplicate,
  payload, disconnect, timeout, and end-to-end latency evidence.
- [x] Prove exact progress/outbox/notification/event-id counts, source offset, zero
  consumer lag, zero retries/failure codes, and zero notification DLT records.
- [x] Correlate application/JVM/Hikari, PostgreSQL, Redis, Kafka, active-session,
  processing, delivery, and container evidence with the exact production image.
- [x] Add fail-closed orchestration, exact cleanup, compact artifact schemas, CI
  isolation/retention, and an operations runbook.
- [x] Pass full local gates, exact feature CI, no-fast-forward merge, repeated merge
  gates, exact main CI, and final delivery reporting.

### Phase 8.4c — Production SLO and capacity policy

- [ ] Obtain business-approved SLIs/SLOs, traffic forecasts, peak concurrency,
  dataset growth, deployment topology, instance sizes, and downstream quotas.
- [ ] Define error budgets, dashboards, alert routing, capacity headroom, autoscaling,
  release criteria, and rollback ownership for the selected environment.

## Guardrails

- Never run automated load against production or a shared developer database.
- Never use a green CI workload as proof of production capacity or availability.
- Never weaken correctness, authorization, encryption, logging, or recovery controls
  to improve a benchmark.
- Never add an index, cache, pool/thread change, or runtime dependency without
  repeatable bottleneck evidence and regression verification.
- Keep load fixtures, credentials, raw results, and runtime resources isolated and
  disposable; retain only compact non-secret summaries.

## External decisions still required

Production SLO/capacity policy remains blocked on hosting topology, regions, instance
sizing, traffic forecasts, business criticality, provider quotas, budget, alert
routing, and named operational owners. Phase 8.4a remains only a portable
regression baseline, and Phase 8.4b1 adds diagnosis rather than production limits.

## Phase 8.4a delivery evidence

- Final feature CI run `34949088389` passed all five jobs for exact evidence SHA
  `c926ac94e0ac93a4fffbfb1499e2bfec7bd1c803`.
- No-fast-forward merge `f5eaaf9e39e584ea7bec3bcd2fe136487303919f`
  passed the repeated local performance, PostgreSQL/MinIO recovery, Maven,
  production-image runtime, SBOM, vulnerability, secret, cleanup, and diff gates.
- Main CI run `34980286932` passed all five jobs for that exact merge SHA. Its
  compact performance artifact `10401696271` is non-empty at 471 bytes.

## Phase 8.4b1 delivery evidence

- Final feature CI run `34987304035` passed all six jobs for exact evidence SHA
  `f785cb1a60d21599303ee37678a2172a81ac6a0f`. Its four non-empty artifacts include
  catalog resource artifact `10403737916` at 1,157 bytes.
- No-fast-forward merge `09d01b0b3b693e332dcf7f1dc2e3ddc4508e0b04`
  passed repeated local Maven (`199` tests), production-image runtime/security,
  PostgreSQL/MinIO recovery, default baseline, diagnostics, evidence-integrity,
  cleanup, and working-tree gates.
- Main CI run `34989730512` passed all six jobs for that exact merge SHA. Its
  non-empty application SBOM, image security, catalog performance, and catalog
  resource artifacts are `10404603354`, `10405281440`, `10405845460`, and
  `10405885413`, respectively.

## Phase 8.4b2 delivery evidence

- Final feature CI run `35215933533` passed all seven jobs for exact evidence SHA
  `d495654e28e992c8685b891800ddadf3e1754a6b`. Its five non-expired artifacts
  include authenticated learning artifact `10495641374` at 2,369 bytes.
- No-fast-forward merge `4c0af0ec63434c0c5040ab78b190f1e2b3a64f91`
  passed repeated local Maven (`199` tests), production-image runtime/security,
  PostgreSQL/MinIO recovery, catalog baseline/diagnostics, authenticated read/write,
  evidence-integrity, cleanup, and working-tree gates.
- Main CI run `35218974231` passed all seven jobs for that exact merge SHA. Its
  non-expired application SBOM, image security, catalog performance, catalog
  resource, and authenticated learning artifacts are `10496272540`, `10496532306`,
  `10495878228`, `10496865265`, and `10496348154`, respectively.

## Phase 8.4b3 delivery evidence

- Final feature CI run `35743955023` passed all eight jobs for exact evidence SHA
  `23c2424f7eabbe9ef8af7a21245d0336ae441fa8`. Its six non-empty, unexpired
  artifacts include learning-event artifact `10703056184` at 1,823 bytes.
- No-fast-forward merge `7d715dc590e9524e3d5fa8f89ef3d09062fd87bc`
  passed repeated local Maven (`199` tests), production-image runtime/security,
  PostgreSQL/MinIO recovery, catalog baseline/diagnostics, authenticated read/write,
  learning-event throughput, evidence-integrity, cleanup, and working-tree gates.
- Main CI run `35747575950` passed all eight jobs for that exact merge SHA. Its
  non-empty, unexpired application SBOM, image security, catalog performance,
  catalog resource, authenticated learning, and learning-event artifacts are
  `10703532097`, `10704066953`, `10703967438`, `10703312879`, `10703542902`, and
  `10704047578`, respectively.

## Phase 8.4b4 delivery evidence

- Final feature CI run `35829387545` passed all nine jobs for exact evidence SHA
  `7675ff8c4d8812dd0791e03fa475663acae2d080`. Its seven non-empty, unexpired
  artifacts include notification WebSocket artifact `10736604301` at 2,041 bytes.
- No-fast-forward merge `5b70957f024f12abc849481e1345158f13cf2492`
  passed repeated local Maven (`201` tests), production-image runtime/security,
  PostgreSQL/MinIO recovery, catalog baseline/diagnostics, authenticated read/write,
  learning-event throughput, notification WebSocket fan-out, evidence-integrity,
  cleanup, and working-tree gates.
- Main CI run `35869448624` passed all nine jobs for that exact merge SHA. Its
  non-empty, unexpired application SBOM, image security, catalog performance,
  catalog resource, authenticated learning, learning-event, and notification
  WebSocket artifacts are `10754547153`, `10754417757`, `10754905703`,
  `10755075649`, `10754651034`, `10754083616`, and `10754123610`, respectively.
