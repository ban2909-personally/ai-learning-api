# ADR-016 — Authenticated notification WebSocket fan-out profile

Status: Accepted

Date: 2026-09-22

## Context

Phase 8.4b3 proves the durable lesson-completion pipeline while no WebSocket client
is connected. The notification module currently uses an in-process Spring simple
broker and user destinations: one committed notification publication may reach
multiple authenticated sessions belonging to the same user. Existing unit and
integration tests prove protocol security and transaction ordering, but they do not
measure connected-session fan-out, client-visible delivery completeness, or runtime
resource behavior.

Mixing an unbounded socket population with the earlier throughput profile would
hide whether a regression belongs to HTTP completion, Kafka projection, or STOMP
delivery. A separate portable CI profile is required. It remains a regression and
diagnostic control, not a production capacity or availability promise.

## Decision

### Boundary and topology

- Run one production application image with disposable PostgreSQL, Redis, and an
  internal-only Kafka broker on a private Docker network.
- Enable the existing learning outbox dispatcher and notification consumer. Keep
  analytics disabled so the measured downstream path ends at durable notification
  projection and authenticated STOMP delivery.
- Use the existing `/ws/notifications` endpoint, JWT-in-`CONNECT` contract, exact
  Origin allowlist, and `/user/queue/notifications` destination. No test-only HTTP
  endpoint, alternate authentication path, or direct application-service call is
  introduced.
- Continue treating PostgreSQL notification history as authoritative. Realtime
  delivery remains an optimization and a failed socket must not roll back a row.

### Deterministic workload

- Seed one free published course with one lesson outside Flyway.
- Register and enroll forty isolated student identities through existing HTTP APIs.
  Tokens remain in k6 memory and never enter URLs, retained files, or artifacts.
- Open two native WebSocket/STOMP sessions per identity: eighty total. Every session
  must complete the HTTP upgrade, authenticated `CONNECT`, and user-destination
  `SUBSCRIBE` before it can receive data.
- After a fixed ten-second connection window, execute forty unique lesson
  completions with eight workers paced at approximately eight requests per second.
  Each completion produces one durable notification and must fan out to both active
  sessions for that identity.
- Keep each socket open briefly after its first message so a duplicate or unexpected
  second delivery is observable rather than hidden by immediate disconnect.

### Correctness and regression gates

The profile fails unless all of the following hold:

- eighty upgrades, authenticated STOMP connections, and subscriptions succeed;
- forty completion writes succeed with no dropped work or HTTP failures;
- exactly forty distinct durable notifications and realtime send invocations exist;
- exactly eighty valid client messages arrive, two for each notification id, with
  no duplicate per session, malformed payload, unexpected id, premature disconnect,
  or socket timeout;
- completion, outbox, publication, notification, source-topic offset, event-id
  reconciliation, consumer lag, retry, failure-code, and DLT invariants agree;
- end-to-end event-to-client latency remains below a portable p95 of 2 seconds and
  p99 of 5 seconds; these are regression budgets, not production SLOs;
- at least ten resource samples bind application/JVM/Hikari, PostgreSQL, Redis,
  Kafka, socket-session, notification-processing, and realtime-delivery evidence to
  the exact production image; and
- all generated containers, networks, credentials, raw samples, and user data are
  removed even after failure.

### Observability

Expose a low-cardinality gauge for the current number of registered notification
WebSocket sessions from the existing session registry. The gauge changes no domain
or application contract and is required to correlate client evidence with server
resource pressure. Existing fixed-tag notification processing and realtime outcome
counters remain the authoritative server-side totals.

Retain only compact versioned JSON summaries for client delivery, durable pipeline,
and correlated resources. Do not retain tokens, raw frames, email addresses,
container logs, database contents, or per-user labels.

### Security and runtime controls

- Bind only the application HTTP port to random loopback; Kafka, PostgreSQL, and
  Redis have no host port.
- Require the configured exact Origin and bearer token in each STOMP `CONNECT`;
  never place JWTs in the upgrade URL or query string.
- Pin every image by digest; use read-only filesystems, dropped capabilities,
  `no-new-privileges`, tmpfs state, bounded CPU/memory/PID resources, and explicit
  Kafka source/DLT topics with auto-creation disabled where supported.
- Keep probes and drain loops bounded. A missing metric, summary, broker offset,
  consumer group, or cleanup result is a failure, never an implicit zero.

## Alternatives rejected

- A test-only push endpoint or direct bean invocation would bypass the real event,
  transaction, authentication, and user-destination boundaries.
- One session per identity would test delivery but not user-destination fan-out.
- Unauthenticated raw WebSockets would not exercise the production protocol.
- Reusing the 8.4b3 mixed result would make socket regressions indistinguishable
  from durable-pipeline regressions.
- Replacing the simple broker or tuning pools before evidence would change topology
  without a diagnosed bottleneck.

## Consequences

The repository gains a repeatable single-instance fan-out signal and the active
session evidence needed for later diagnosis. CI becomes longer and exercises eighty
short-lived sockets. The profile does not validate multi-instance routing, broker
relay behavior, internet proxies, mobile networks, reconnect storms, token-expiry
storms, slow consumers, spike/saturation/soak behavior, or production capacity.
