# ADR-012 — Public catalog performance regression baseline

Status: accepted for Phase 8.4a

Date: 2026-09-15

## Context

The production image has bounded HTTP and database-pool defaults, and catalog reads
already exercise PostgreSQL plus an optional Redis cache. The repository does not
yet contain a repeatable load workload, representative catalog volume, or machine-
readable latency/error evidence. A unit or integration test can prove correctness
but cannot detect a severe throughput or query-path regression.

Production traffic shape, deployment region, instance sizing, autoscaling,
downstream service tiers, business availability target, and SLO/error-budget policy
are not selected. A CI runner therefore cannot provide a production capacity claim.
This slice establishes a conservative engineering regression gate while keeping
production SLOs and capacity decisions explicit.

## Decision

- Exercise the immutable production image from outside the JVM with Grafana k6.
  Use k6 `2.2.0`, pinned to multi-platform OCI digest
  `sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6`.
- Create isolated PostgreSQL, Redis, application, and load-generator containers on a
  unique network. Use tmpfs for service data and remove all resources through an
  exit trap. Never target a shared or production environment from CI.
- Seed one instructor and 5,000 deterministic published courses across the existing
  categories after Flyway completes. Do not add a production migration or synthetic
  records to application startup.
- Warm the popular page once, then generate a mixed public-read workload at 25
  iterations per second for 30 seconds: popular first-page reads, category-filtered
  reads, and stable course-detail reads. This covers Redis hit and PostgreSQL paths
  while avoiding unrelated payment, AI-provider, media-transfer, and authenticated
  write semantics.
- Require correct HTTP/JSON responses, zero dropped iterations, request failure rate
  below 1%, p95 request duration below 750 ms, and p99 below 1,500 ms. These are CI
  regression budgets chosen to catch major degradation, not promised production
  SLOs.
- Save a compact non-secret JSON summary under `target/performance/` locally and in
  CI. Do not upload response bodies, database contents, credentials, or raw request
  logs.
- Run service and load containers as non-root, drop capabilities where supported,
  prevent privilege escalation, and pass generated credentials by environment
  variable name rather than command-line value.
- Do not tune indexes, connection pools, thread counts, caches, or heap from a single
  green/bad CI run. A change requires repeatable evidence plus query/resource
  diagnosis.
- Keep application modules, HTTP contracts, persistence mappings, and runtime
  dependencies unchanged in this slice.

## Consequences

The project gains a reproducible smoke-capacity signal over real HTTP, Redis, JPA,
Flyway, and PostgreSQL behavior. The workload can detect broken cache/database paths,
large latency regressions, saturation that drops scheduled work, and malformed
responses before merge.

Results remain specific to the runner and synthetic dataset. They do not cover login
hashing, authenticated writes, Kafka throughput, notification WebSockets, lesson
media bandwidth, AI-provider latency/cost, geographic latency, failover, soak,
spikes, or production peak concurrency. Phase 8.4b must add resource observations
and broader critical-journey profiles; production SLOs require measured real traffic
and business approval.
