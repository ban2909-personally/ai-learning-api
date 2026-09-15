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
- [ ] Record exact feature and main CI performance results for their delivered SHAs.
- [ ] Pass full local gates, cohesive commits, exact feature CI, no-fast-forward
  merge, repeated merge gates, exact main CI, and delivery reporting.

### Phase 8.4b — Broader capacity and resource diagnosis

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
