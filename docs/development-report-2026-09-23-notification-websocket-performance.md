# Development report — Notification WebSocket fan-out performance

Date: 2026-09-23

Branch: feature/notification-websocket-performance

## Outcome

Phase 8.4b4 adds a production-image-bound regression profile for the complete
notification path: authenticated lesson completion, transactional outbox, Kafka
dispatch, durable notification projection, and STOMP delivery to two simultaneous
sessions for each user. The profile proves exact fan-out and reconciliation rather
than treating a successful WebSocket upgrade as delivery evidence.

The implementation preserves the REST, STOMP, event, and database contracts. It
adds one operational metric, corrects the inbound STOMP security interceptor order,
and otherwise limits production changes to the defect exposed by the workload.

## Architecture and maintainability

- ADR-016 fixes the concurrency model, exact counts, latency thresholds, evidence,
  isolation, and non-goals before the harness implementation.
- Forty identities each establish two native WebSocket/STOMP sessions. Forty unique
  lesson completion transitions are issued by eight bounded workers, and exactly
  eighty user-destination messages must be observed.
- Subscription readiness is proven by a STOMP receipt when the broker emits one, or
  by the first correctly routed message for Spring's simple broker. Connections
  remain open after expected delivery to expose duplicate messages.
- The existing resource collector gained one explicit notification-websocket mode.
  It records active sessions, outbox pressure, Kafka resources, database counters,
  and application delivery counters without creating a parallel collector.
- The collector now completes its declared minimum sample count before producing a
  summary. This removed a timing-dependent nine-versus-ten-sample failure without
  weakening the evidence requirement.
- CI runs the profile as an independent job after the immutable production-image
  gate and retains only the three versioned, non-secret summaries.

## Security defect found and corrected

The first native STOMP workload completed every HTTP upgrade but Spring Security
rejected all subscriptions with AuthenticationCredentialsNotFoundException.
The JWT interceptor correctly assigned simpUser, but the inbound channel did not
publish that authentication to SecurityContextHolder before authorization.

SecurityContextChannelInterceptor now runs between the custom JWT authentication
interceptor and AuthorizationChannelInterceptor. The notification module test
asserts that exact subsequence. The workload also proves that unauthenticated
metrics return 401 and a disallowed WebSocket origin returns 403.

## Security and data lifecycle

- PostgreSQL, Redis, and Kafka have no public host ports. The application uses one
  random loopback port only for readiness, security probes, and protected metrics.
- Application and k6 filesystems are read-only; capabilities are dropped; privilege
  escalation is disabled; application and broker resources are bounded; state is
  tmpfs-backed.
- Generated passwords and JWTs remain process-local. Raw samples, tokens, database
  contents, broker logs, and session state are not retained as artifacts.
- Cleanup targets only exact generated resource names. Successful final validation
  left no labeled performance container or network.

## Local implementation evidence

- The final WebSocket workload achieved checks 1.0, upgrades 80/80, connected
  sessions 80/80, ready subscriptions 80/80, completion requests 40/40, and valid
  messages 80/80.
- Delivery latency was p95 1139.60 ms and p99 1198.21 ms, below the fixed two-second
  and five-second thresholds. Unexpected messages, premature disconnects, socket
  timeouts/errors, protocol errors, failed completions, and dropped iterations were
  all zero.
- Database evidence matched exactly: forty completed progress rows, forty outbox
  rows, forty published rows, and forty durable notifications. Pending rows,
  attempts, locks, failure codes, and analytics facts were zero.
- Outbox and notification event IDs reconciled with zero mismatch. Kafka source
  offset was forty, notification consumer lag and DLT offset were zero. Client
  messages were eighty while server send invocations were forty, proving two-session
  fan-out without duplicate server dispatch.
- Resource evidence captured twelve samples over 49 seconds and observed all eighty
  active WebSocket sessions. Application counters recorded forty dispatches, forty
  notification projections, and forty realtime sends; every failure, duplicate,
  rejection, and DLT counter was zero.
- The exact candidate image was
  sha256:f5646fd545e4ae3b8ac0052f6b35a1ad6100811d919c1df42d1cfef5a88e129b.

## Regression and operational gates

- Maven clean verify passed all 201 tests with zero failures, errors, or skips. All
  twelve Flyway migrations, Spring Modulith boundaries, ArchUnit rules, JaCoCo
  checks, and the CycloneDX 1.6 application SBOM with 138 components passed.
- Module integration tests use one deterministic fixed Clock instead of resettable
  mocks. This prevents the cached notification scheduler from receiving a null
  instant at an hour boundary.
- The production image runs as 65532:65532, activates prod, and has the fixed
  /usr/bin/java -jar /app/app.jar entrypoint. Its CycloneDX 1.7 SBOM contains 153
  components. Source/image secret scans were clean; the complete vulnerability
  report contains 67 findings, including seven unfixed HIGH findings and zero
  fixable HIGH/CRITICAL findings.
- PostgreSQL recovery passed confirmation, non-empty-target, and checksum guards
  before exact restore in 22 seconds. MinIO recovery passed confirmation, prefix
  reuse, non-empty-target, inventory checksum/count, and missing/extra/different
  object guards before exact verification in 41 seconds.
- Catalog baseline passed 750 iterations with no failure/drop and p95 20.498 ms.
  Catalog diagnostics repeated 750 iterations with p95 19.416 ms and ten samples.
- Authenticated read passed 601 iterations with p95 18.479 ms and fourteen samples.
  Write passed 301 iterations with p95 32.642 ms and thirteen samples. Both had no
  request failure or dropped iteration.
- Learning-event throughput passed 241 iterations with p95 39.318 ms, seventeen
  samples, exact database/topic/projection counts, zero lag, zero DLT, and zero
  event-ID mismatch.

## Cohesive commits before delivery gates

- 71f1828 — close the prior learning-event performance phase.
- 59cfb6f — define ADR-016 and the Phase 8.4b4 boundary.
- 8c1ca57 — expose active notification WebSocket sessions.
- c92c736 — propagate STOMP authentication before channel authorization.
- ae05807 — add the deterministic native WebSocket/STOMP fan-out workload.
- 2364c99 — orchestrate fail-closed pipeline and resource diagnostics.
- e1f885a — add the independent CI job and artifact contract.
- e2669c2 — replace resettable module-test clocks with a fixed test clock.
- a4aaa8d — make the shared minimum-sample contract deterministic.

## Delivery status

All implementation and local pre-push gates are complete. Exact feature CI,
no-fast-forward merge verification, and exact main CI evidence remain to be
recorded before Phase 8.4b4 is closed.

No production capacity or SLO conclusion is made. Phase 8.4c still depends on
business-approved traffic forecasts, deployment topology, instance sizing,
downstream quotas, error budgets, and operational ownership.
