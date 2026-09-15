# ADR-013 — Isolated catalog resource diagnostics

Status: accepted for Phase 8.4b1

Date: 2026-09-15

## Context

Phase 8.4a detects correctness, error-rate, dropped-work, and latency regressions
for public catalog reads. Its result cannot explain whether a regression comes from
the JVM, HTTP process, connection pool, PostgreSQL, Redis, or runner contention.
Changing indexes, heap, threads, pools, or cache settings without that evidence
would create speculative complexity and could reduce reliability.

The existing baseline must remain comparable and must not expose operational
metrics in production. Production topology and approved capacity budgets remain
unknown, so this slice may collect resource evidence but cannot set production
resource thresholds.

## Decision

- Add an opt-in diagnostics mode to the isolated catalog orchestrator. Its default
  behavior and the existing `performance-baseline` CI job remain unchanged.
- Run diagnostics in a separate CI job against the same production image, dataset,
  arrival rate, duration, endpoint mix, correctness checks, and latency budgets.
  Observation overhead therefore cannot make the original regression signal pass
  or fail differently.
- During the opt-in run, expose Spring Boot's existing Actuator `metrics` endpoint
  only through the loopback-bound disposable application port. Keep the endpoint
  behind the existing JWT security filter; do not change application defaults or
  authorization rules.
- Create one disposable diagnostics student through the existing registration
  contract with a generated password. Keep its access token only in process memory,
  pass it to the sampler by environment variable name, never print it, and destroy
  it with the ephemeral PostgreSQL container.
- Sample continuously while k6 runs and require at least ten observations. Record
  the observed span plus maximum application, PostgreSQL, and Redis container CPU
  and memory percentages, application PID count, JVM used memory, and Hikari active
  and pending connections. Do not claim a fixed interval because Docker's
  no-stream statistics cycle and host scheduling determine the actual cadence.
- Capture final existing `catalog.cache.access` hit/miss/failure counters,
  PostgreSQL database sessions/transactions/block reads/cache hits/temp
  files/bytes/deadlocks, and Redis keyspace hits/misses/evictions/peak memory.
- Write `target/performance/catalog-resource-summary.json` with a versioned format,
  correlated workload identity, sample count, and numeric observations. Retain it
  with the corresponding k6 summary, never raw bearer tokens, passwords, response
  bodies, database contents, or unbounded logs.
- Fail closed when metrics are unauthorized, missing, non-numeric, internally
  inconsistent, insufficiently sampled, or when the cache failure counter is
  non-zero. Record other resource values without arbitrary pass/fail ceilings.
- Add no Java class, interface, dependency, production migration, API change,
  index, cache behavior, pool/thread/heap tuning, or cross-module dependency.

## Consequences

The same catalog workload gains enough correlated evidence to distinguish runner
contention, application/JVM pressure, pool queuing, database I/O/transaction
behavior, and cache effectiveness before a tuning proposal. Evidence remains small,
portable, reviewable, and tied to an immutable application image.

Actuator sampling and Docker stats add overhead, so diagnostic numbers are not
compared directly to the unobserved baseline and are not production capacity
claims. Broader authenticated writes, Kafka/WebSocket, AI-provider, media,
saturation, spike, recovery, and soak profiles remain separate Phase 8.4b slices.
