# ADR-015 — Isolated learning-event throughput profile

Status: accepted for Phase 8.4b3

Date: 2026-09-17

## Context

The platform already commits lesson completion and an immutable integration-event
intent atomically, dispatches the outbox to Kafka, and projects the versioned event
into analytics and notifications. Existing integration tests prove transactional
correctness, mapping, retry behavior, and bounded duplicate handling, but they do
not exercise the complete production image and broker path under a repeatable
arrival rate.

Combining this path with connected WebSocket clients would obscure whether a
regression belongs to HTTP/JPA, the outbox dispatcher, Kafka, durable consumers,
or realtime fan-out. AI and media traffic have different provider and bandwidth
constraints and remain separate scenarios.

## Decision

Add one fail-closed, production-image-bound event-pipeline regression profile.
It runs only against disposable PostgreSQL, Redis, Kafka, application, and k6
containers on a per-run private network.

The fixture contains one published free course and eight protected lessons outside
Flyway. k6 registers forty disposable students through the public API, enrolls each
student, and sends unique `completed=true` progress transitions at eight arrivals
per second for thirty seconds. The identity/lesson mapping stays unique for at
least 320 completions, while the expected CI workload is approximately 240.

The HTTP workload must achieve at least 235 iterations with all checks passing,
zero request failures, zero dropped iterations, p95 below 1,000 ms, and p99 below
2,000 ms. These are broad regression budgets, not production latency objectives.

After traffic, the harness waits a bounded period while resource capture continues.
A run succeeds only when all of the following match the achieved iteration count:

- completed progress rows and immutable outbox rows;
- published outbox rows with zero pending, retry attempts, locks, or failure codes;
- Kafka source-topic end offsets;
- analytics completion facts and durable user notifications;
- event identifiers across outbox, analytics, and notifications.

Both analytics and notification consumer groups must finish with zero lag. Their
dead-letter topics must remain empty. Final dispatch, projection, rejection,
duplicate, and dead-letter metrics must agree with database and Kafka evidence.
Repeated HTTP completion of the same progress is already guarded by the domain
transition and existing integration tests; this profile does not manufacture a
Kafka redelivery that the actual pipeline did not produce.

## Resource evidence

Extend the existing operational collector with a meaningful `learning-event` mode
instead of copying it. Record compact maxima for application/JVM/Hikari,
PostgreSQL, Redis, and Kafka container CPU/memory/PIDs, plus maximum outbox backlog
and oldest age observed while traffic drains. Record final low-cardinality pipeline
counters and database/cache counters. Bind every summary to the exact production
image ID and require at least ten samples.

No CPU, memory, pool, Kafka, database, or latency observation becomes a production
limit. A tuning change requires a reproduced bottleneck and a separate correctness,
security, and recovery review.

## Security and isolation

- Kafka has no host-published port and plaintext exists only on the disposable
  private Docker network. Topics are created explicitly; broker auto-creation is
  disabled.
- The application port is random and loopback-only. Actuator metrics remain
  JWT-protected, and an unauthenticated request must return HTTP 401.
- Generated database, Redis, JWT, and student credentials are passed by environment
  variable name, never command-line value. JWTs stay in process memory.
- Data containers use tmpfs. Application and k6 roots are read-only, capabilities
  are dropped, privilege escalation is disabled, and explicit CPU/memory/PID
  bounds apply.
- Credentials, JWTs, raw responses, samples, broker data, and database contents are
  never artifacts. Cleanup targets only exact generated names and labels.

## Non-goals

This phase does not claim production throughput, tune the dispatcher, change topic
partitions, alter consumer concurrency, edit migrations, or add indexes. It does
not measure connected WebSocket fan-out, replay storms, broker failure recovery,
spike/saturation/soak behavior, AI-provider capacity, or media bandwidth.

## Consequences

The repository gains an end-to-end regression signal for the current durable event
architecture and correlated evidence for later diagnosis. CI becomes longer and
requires one additional isolated Kafka broker, but no production dependency, API,
event contract, database behavior, or module boundary changes.
