# Phase 6 — Realtime learning events migration plan

## Outcome

Phase 6 creates a reliable event backbone for learning analytics and user-specific realtime notifications while preserving current HTTP contracts and keeping Kafka/WebSocket infrastructure outside domain and application logic.

## Delivery slices

### Slice 6.1 — Durable lesson-completed publication

- Add a versioned, PII-minimal `lesson.completed` integration contract.
- Detect only the first incomplete-to-complete transition.
- Persist event intent transactionally with lesson progress.
- Dispatch claimed outbox records to Kafka with acknowledgement, retry, lease recovery, and metrics.
- Keep Kafka optional at startup and externally provision the topic.

### Slice 6.2 — Idempotent realtime notification projection

- Consume completion events with event-id deduplication.
- Store a notification projection before acknowledging Kafka.
- Expose authenticated notification history over HTTP.
- Deliver new notifications to a user-specific STOMP destination.
- Authenticate STOMP `CONNECT` using the existing JWT decoder and deny client writes to broker destinations.
- Add reconnect/catch-up behavior so WebSocket delivery is an optimization, not the source of truth.

### Slice 6.3 — Analytics projection and operability

- Add course/lesson completion projections driven by the same versioned event.
- Define consumer lag, outbox backlog, retry, and dead-letter alert thresholds.
- Add controlled replay and retention procedures.
- Validate multi-instance ordering and backpressure under load.

## Slice 6.1 checklist

- [x] Confirm Phase 5 feature/main CI success and close its report.
- [x] Survey progress transactions, persistence, module rules, and existing infrastructure.
- [x] Record the outbox and at-least-once delivery decision before implementation.
- [x] Add Kafka dependencies and bounded external configuration.
- [x] Add domain event, versioned API event, and outbound ports.
- [x] Refactor progress orchestration for readable first-completion detection and unit tests.
- [x] Add Flyway V8 outbox schema with uniqueness, lease, retry, and pending indexes.
- [x] Add PostgreSQL outbox adapter and Testcontainers integration tests.
- [x] Add bounded dispatcher, scheduled trigger, Kafka adapter, metrics, and unit tests.
- [x] Add Kafka Testcontainers contract test for key, headers, and JSON payload.
- [x] Run Maven `clean verify` with zero skipped tests and passing coverage/module/architecture gates.
- [x] Audit secrets, PII, logs, payload size, retry behavior, multi-instance claims, and diff formatting.
- [x] Commit cohesive slices, push feature, wait for green CI, merge `main`, push, and verify main CI.
- [x] Publish the Phase 6.1 development report.

Feature CI: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33853673527

Main CI: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33854119240

Merge commit: `70c85c8 merge: deliver durable learning events`

## Slice 6.2 checklist

- [x] Confirm slice 6.1 feature/main CI success before starting new branches.
- [x] Survey JWT, CORS, module boundaries, Kafka consumer seams, HTTP patterns, and responsive frontend layout.
- [x] Verify STOMP token authentication, authorization, user destinations, and client reconnect behavior against official documentation.
- [x] Record notification projection, catch-up, and WebSocket security decisions before implementation.
- [x] Add the `notification` bounded context with `learning::events` as its only business-module dependency.
- [x] Add Flyway V9 notification projection with event-id idempotency, keyset history indexes, and unread state.
- [x] Add framework-free notification domain/application logic and PostgreSQL adapter tests.
- [x] Add authenticated history/read HTTP contracts with MockMvc and Spring Security tests.
- [x] Add Kafka consumer validation, database-before-ack semantics, bounded retry, and Testcontainers coverage.
- [x] Add exact-origin STOMP endpoint, JWT `CONNECT` authentication, deny-by-default message authorization, and server-side token-expiry disconnect.
- [x] Add user-specific realtime delivery only after the projection transaction commits.
- [x] Add a responsive notification center with HTTP catch-up, event-id deduplication, heartbeat, token refresh, and reconnect backoff.
- [x] Verify frontend behavior and horizontal overflow at 320 px, 768 px, and 1440 px.
- [x] Run complete backend and frontend quality gates with no skipped tests.
- [x] Audit cross-user isolation, token leakage, origin checks, logs, message size, pagination, retry, and diff formatting.
- [x] Commit cohesive slices, push features, wait for both CIs, merge both mains, rerun gates, push, and verify both main CIs.
- [x] Publish the Phase 6.2 development report.

Feature CI:

- Backend: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33886469597
- Frontend: https://github.com/ban2909-personally/ai-learning-web/actions/runs/33886466096

Main CI:

- Backend: https://github.com/ban2909-personally/ai-learning-api/actions/runs/33887264475
- Frontend: https://github.com/ban2909-personally/ai-learning-web/actions/runs/33887253613

Merge commits:

- Backend: `e4985aa merge: deliver realtime notifications`
- Frontend: `ff9915c merge: deliver realtime notifications`

## Slice 6.3 checklist

- [x] Confirm slice 6.2 local gates, feature CI, merge, main gates, and main CI in both repositories.
- [x] Survey existing event contract, outbox, notification consumer, retry, metrics, and module seams.
- [x] Record analytics ownership, idempotency, failure classification, replay, retention, and observability decisions before implementation.
- [ ] Add an `analytics` bounded context depending only on `learning::events`, `platform::security`, and `sharedkernel::error`.
- [ ] Add an additive completion-fact projection schema with event-id idempotency and bounded aggregate-query indexes.
- [ ] Add framework-free analytics domain/application boundaries and PostgreSQL adapter coverage.
- [ ] Add a validating Kafka consumer with database-before-ack semantics and duplicate/rejection metrics.
- [ ] Add authenticated learner completion insights without accepting caller-supplied identity.
- [ ] Add finite retry, durable dead-letter persistence, controlled replay, and bounded retention cleanup.
- [ ] Expose low-cardinality backlog, lag/retry, dead-letter, replay, and cleanup signals with documented thresholds.
- [ ] Validate redelivery, ordering, concurrency, and bounded backpressure with unit/integration/load-oriented tests.
- [ ] Run complete quality gates, security/diff audit, feature CI, merge/main gates, and main CI.
- [ ] Publish the Phase 6.3 development report.

## Compatibility constraints

- Existing lesson progress request and response JSON remain unchanged.
- Existing progress remains monotonic: once completed, later saves cannot revert it.
- Existing migrations remain immutable; V8 is additive.
- Kafka downtime cannot roll back or reject a valid progress update.
- No browser or caller may supply event identity, learner identity, topic, key, or schema version.

## Explicitly deferred from slice 6.1

- WebSocket/STOMP and frontend notification UI belong to slice 6.2.
- Consumer-side analytics belongs to slice 6.3.
- Schema Registry/Avro is deferred until multiple independently deployed consumers justify it; JSON v1 remains explicit and tested.
- Permanent dead-letter administration and manual replay UI are deferred, but retry metadata is retained for operations.
