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
- [ ] Add the `notification` bounded context with an explicit dependency on `learning::events` only.
- [ ] Add Flyway V9 notification projection with event-id idempotency, keyset history indexes, and unread state.
- [ ] Add framework-free notification domain/application logic and PostgreSQL adapter tests.
- [ ] Add authenticated history/read HTTP contracts with MockMvc and Spring Security tests.
- [ ] Add Kafka consumer validation, database-before-ack semantics, bounded retry, and Testcontainers coverage.
- [ ] Add exact-origin STOMP endpoint, JWT `CONNECT` authentication, deny-by-default message authorization, and server-side token-expiry disconnect.
- [ ] Add user-specific realtime delivery only after the projection transaction commits.
- [ ] Add a responsive notification center with HTTP catch-up, event-id deduplication, heartbeat, token refresh, and reconnect backoff.
- [ ] Verify frontend behavior and horizontal overflow at 320 px, 768 px, and 1440 px.
- [ ] Run complete backend and frontend quality gates with no skipped tests.
- [ ] Audit cross-user isolation, token leakage, origin checks, logs, message size, pagination, retry, and diff formatting.
- [ ] Commit cohesive slices, push features, wait for both CIs, merge both mains, rerun gates, push, and verify both main CIs.
- [ ] Publish the Phase 6.2 development report.

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
