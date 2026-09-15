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

- [ ] Record a resource-diagnostics ADR and keep the existing regression workload
  unchanged by making observation opt-in and independently gated.
- [ ] Sample application, PostgreSQL, and Redis container CPU/memory plus application
  PID, JVM-memory, and Hikari active/pending evidence during the catalog workload.
- [ ] Capture final application cache counters, PostgreSQL database counters, and
  Redis keyspace/memory counters in a versioned compact non-secret summary.
- [ ] Keep temporary Actuator metrics JWT-protected and reachable only through the
  isolated loopback-bound test runtime; never alter production exposure defaults.
- [ ] Validate diagnostic evidence, failure behavior, cleanup, and an independent CI
  job without inventing CPU, JVM, pool, database, or cache limits from one runner.
- [ ] Document diagnosis and comparison workflow; pass full local, feature CI,
  no-fast-forward merge, repeated merge gates, and exact main CI delivery.
- [ ] Add authenticated read/write profiles only with isolated identities and safe
  deterministic cleanup.
- [ ] Cover event throughput, WebSocket fan-out, AI concurrency/cost bounds, and
  media bandwidth with subsystem-specific scenarios rather than one mixed number.
- [ ] Capture CPU, memory, JVM, connection-pool, database, and cache evidence so
  tuning changes have a diagnosed bottleneck.
- [ ] Add controlled spike, saturation, recovery, and soak exercises outside normal
  per-push CI.

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
routing, and named operational owners. Phase 8.4a proceeds only as a portable
regression baseline.

## Phase 8.4a delivery evidence

- Final feature CI run `34949088389` passed all five jobs for exact evidence SHA
  `c926ac94e0ac93a4fffbfb1499e2bfec7bd1c803`.
- No-fast-forward merge `f5eaaf9e39e584ea7bec3bcd2fe136487303919f`
  passed the repeated local performance, PostgreSQL/MinIO recovery, Maven,
  production-image runtime, SBOM, vulnerability, secret, cleanup, and diff gates.
- Main CI run `34980286932` passed all five jobs for that exact merge SHA. Its
  compact performance artifact `10401696271` is non-empty at 471 bytes.
