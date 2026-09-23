# Authenticated notification WebSocket performance operations

## Purpose and boundary

This profile detects regressions across the existing single-instance notification
path: authenticated STOMP sessions, lesson completion, transactional outbox, Kafka
dispatch, durable notification projection, user-destination routing, and
client-visible fan-out. It complements the durable learning-event profile; it does
not replace durable catch-up verification.

The application still uses Spring's in-process simple broker. Results therefore
apply only to one application instance and the synthetic fixture. They are not a
production concurrency claim, SLO, sizing recommendation, multi-instance routing
proof, broker-relay qualification, or internet/mobile-network simulation.

## Run the isolated profile

Build the production image, then validate and run the harness:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/test-notification-websocket-performance.sh
bash scripts/test-notification-websocket-performance.sh
```

Set `PERFORMANCE_APP_IMAGE` only to verify another image that already exists on the
local Docker daemon. The harness requires Docker, curl, OpenSSL, GNU `timeout`, and
the other commands checked before resource allocation. It creates a private network
and disposable PostgreSQL, Redis, Kafka, application, and k6 containers. Never
modify it to target a shared or production service.

## Workload contract

The fixture lives outside Flyway and contains one published free course with one
protected lesson. k6 registers and enrolls forty students through the real HTTP
contracts. It opens two native WebSocket/STOMP sessions per student, for eighty
sessions total, using the configured exact Origin and a bearer token in each STOMP
`CONNECT` frame.

After a fixed ten-second connection window, eight workers issue forty unique lesson
completion transitions. Each resulting durable notification must be delivered to
both sessions for its student. A STOMP receipt confirms a subscription when the
broker emits one; the first correctly routed message is also conclusive acceptance
evidence for the built-in simple broker. Each socket remains open for two seconds
after its first message so an unexpected duplicate is observable.

The client gate requires exactly eighty successful upgrades, authenticated
connections, confirmed subscriptions, and valid messages; exactly forty successful
completion writes; no dropped work, duplicate, malformed payload, premature close,
timeout, socket error, or protocol error; p95 below 2,000 ms; and p99 below 5,000
ms. These latency values are portable regression budgets, not production targets.

The bounded drain additionally requires exact agreement between forty completed
progress rows, outbox rows, published events, durable notifications, source-topic
offsets, and realtime send invocations. Pending rows, attempts, leases, failure
codes, analytics facts, event-ID mismatches, notification consumer lag, and DLT
records must be zero.

## Evidence

Successful runs create three compact JSON files:

```text
target/performance/notification-websocket-client-summary.json
target/performance/notification-websocket-pipeline-summary.json
target/performance/notification-websocket-resource-summary.json
```

Their formats are `ai-learning-notification-websocket-v1`,
`ai-learning-notification-websocket-pipeline-v1`, and
`ai-learning-notification-websocket-resource-v1`. Pipeline and resource evidence
contains the exact production image ID. CI retains only these summaries for
fourteen days.

Resource evidence requires at least ten correlated samples. It records application,
JVM, Hikari, PostgreSQL, Redis, and Kafka maxima; outbox backlog; active WebSocket
sessions; and final fixed-tag dispatch, projection, and realtime-delivery counters.
CPU may exceed 100% when a container uses more than one core. No measured maximum
is a production limit.

## Security and isolation

- Only the application HTTP port is bound to a random loopback port. PostgreSQL,
  Redis, and Kafka remain reachable only on the per-run private network.
- The untrusted-Origin probe must return HTTP 403. The unauthenticated actuator
  probe must return HTTP 401 before the diagnostics identity is created.
- PostgreSQL, Redis, and Kafka state is tmpfs-backed. Application and k6 roots are
  read-only. Containers drop capabilities, disallow privilege escalation, and use
  bounded CPU, memory, or PID resources.
- Topics are explicit and auto-creation is disabled. Analytics consumption is off
  so the measured downstream boundary is notification delivery.
- Database, cache, JWT, diagnostics, and student credentials are generated per run.
  Tokens remain in k6 memory or temporary process state and never enter URLs,
  summaries, raw samples, or retained logs.
- Cleanup uses only the exact generated names. After an interrupted run, inspect
  resources labeled `ai-learning.performance=notification-websocket` and remove
  only those exact resources; never use broad Docker cleanup.

## Diagnosis and change discipline

Treat any missing field or summary, wrong format/image, insufficient samples,
inexact count, non-zero retry/lag/DLT/duplicate/rejection/failure, Origin or metric
exposure, latency breach, timeout, or leftover resource as a failed run. Do not
weaken a gate to make CI green.

Compare results only when profile version, topology, fixture, workload, image, and
runner conditions are comparable. Diagnose with client, durable-pipeline, and
resource evidence together. Any broker topology, queueing, pool, thread, timeout,
retry, security, or API change then needs separate correctness, security, recovery,
and performance review.
