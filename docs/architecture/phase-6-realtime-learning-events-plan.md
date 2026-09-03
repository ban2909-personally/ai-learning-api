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
- [ ] Add Kafka dependencies and bounded external configuration.
- [ ] Add domain event, versioned API event, and outbound ports.
- [ ] Refactor progress orchestration for readable first-completion detection and unit tests.
- [ ] Add Flyway V8 outbox schema with uniqueness, lease, retry, and pending indexes.
- [ ] Add PostgreSQL outbox adapter and Testcontainers integration tests.
- [ ] Add bounded dispatcher, scheduled trigger, Kafka adapter, metrics, and unit tests.
- [ ] Add Kafka Testcontainers contract test for key, headers, and JSON payload.
- [ ] Run Maven `clean verify` with zero skipped tests and passing coverage/module/architecture gates.
- [ ] Audit secrets, PII, logs, payload size, retry behavior, multi-instance claims, and diff formatting.
- [ ] Commit cohesive slices, push feature, wait for green CI, merge `main`, push, and verify main CI.
- [ ] Publish the Phase 6.1 development report.

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
