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
- 5292658 — record complete local implementation and gate evidence.
- 7675ff8 — record final feature CI evidence.

## Final feature CI evidence

- Final feature SHA 7675ff8c4d8812dd0791e03fa475663acae2d080 passed all nine
  jobs in CI run 35829387545: Maven verification, production image/security,
  PostgreSQL recovery, MinIO recovery, catalog baseline, catalog diagnostics,
  authenticated learning, learning-event throughput, and notification WebSocket
  fan-out.
- All seven retained artifacts are non-empty and unexpired: application SBOM
  10736543723 (75,092 bytes), image security 10736781571 (71,283 bytes), catalog
  performance 10736437132 (463 bytes), catalog resource 10736932076 (1,146 bytes),
  authenticated learning 10735824584 (2,351 bytes), learning event 10735964455
  (1,830 bytes), and notification WebSocket 10736604301 (2,041 bytes).

## Merge and main delivery evidence

- No-fast-forward merge 5b70957f024f12abc849481e1345158f13cf2492 repeated the
  full local gate suite on the exact merge result. Maven passed 201 tests with all
  twelve Flyway migrations, Modulith and ArchUnit rules, JaCoCo, and the application
  SBOM. The production image was
  sha256:19edb29953c0f8469ed51081fe82a4fe550c44b0760c8038778d2ee4b3a38ffc.
- The merge image security gate retained 67 known findings, including seven
  currently unfixed HIGH findings, with zero fixable HIGH/CRITICAL findings and no
  source or image secrets. PostgreSQL recovery passed in 20 seconds and MinIO
  recovery passed in 40 seconds.
- Merge performance gates passed with zero request failures or dropped iterations:
  catalog baseline 750 iterations at p95 25.907 ms; catalog diagnostics 751 at p95
  44.659 ms with ten samples; authenticated read 600 at p95 24.699 ms and write 301
  at p95 28.725 ms, each with fourteen samples; learning event 241 at p95 38.632 ms
  with eighteen samples and exact DB/outbox/Kafka reconciliation; notification
  WebSocket 80 connected/subscribed sessions, 40 completions, exactly 80 valid
  messages, p95 963.3 ms, and twelve samples.
- Main CI run 35869448624 passed all nine jobs for the exact merge SHA. Its seven
  non-empty, unexpired artifacts are application SBOM 10754547153 (75,092 bytes),
  image security 10754417757 (71,488 bytes), catalog performance 10754905703
  (463 bytes), catalog resource 10755075649 (1,152 bytes), authenticated learning
  10754651034 (2,353 bytes), learning event 10754083616 (1,825 bytes), and
  notification WebSocket 10754123610 (2,038 bytes).

## Delivery status

Phase 8.4b4 is complete. Implementation, local pre-push gates, final exact feature
CI, no-fast-forward merge verification, exact main CI, retained artifacts, and
delivery reporting all passed without weakening the documented contracts.

No production capacity or SLO conclusion is made. Phase 8.4c still depends on
business-approved traffic forecasts, deployment topology, instance sizing,
downstream quotas, error budgets, and operational ownership.
