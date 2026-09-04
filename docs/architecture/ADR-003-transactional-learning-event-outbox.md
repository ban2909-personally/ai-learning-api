# ADR-003 — Transactional outbox for learning integration events

Status: Accepted

Date: 2026-09-03

## Context

Phase 6 needs durable learning events for analytics and realtime notifications. Publishing to Kafka directly from the lesson-progress request creates a dual-write failure: PostgreSQL can commit while Kafka fails, or Kafka can accept a message while the database transaction later rolls back. Coupling the learning application service to Spring events or Kafka would also weaken the hexagonal boundary.

The first externally useful transition is the first time an enrolled learner completes a lesson. Repeated progress saves must not create repeated logical completion events.

## Decision

The `learning` bounded context owns a transactional outbox and an internal `LessonCompleted` domain event.

1. The authenticated progress use case derives learner, enrollment, course, and lesson identifiers server-side.
2. When saved progress first transitions to completed, the application sends `LessonCompleted` to a learning-owned outbound port.
3. A PostgreSQL adapter inserts an immutable, versioned JSON payload into `learning_event_outbox` in the same transaction as progress persistence.
4. A unique business constraint permits only one `lesson.completed` event for an enrollment/lesson aggregate.
5. A scheduled inbound adapter asks an application dispatcher to claim a bounded batch using a database lease and `FOR UPDATE SKIP LOCKED` semantics.
6. A Kafka outbound adapter publishes the stored payload and waits for broker acknowledgement before the outbox row is marked published.
7. Failed sends are rescheduled with bounded exponential backoff. Logs and metrics contain event identifiers and low-cardinality status only, never payload contents.

The public integration contract is `LessonCompletedEventV1` in `learning.api.event`. Kafka delivery is at-least-once. Every consumer must deduplicate by `eventId`; producer idempotence does not eliminate duplicates caused by a process crash after Kafka acknowledgement and before the outbox completion update.

## Event contract

Topic: `ai-learning.learning.lesson-completed.v1`

Key: learner UUID

The JSON payload contains:

- `eventId`
- `eventType` (`lesson.completed`)
- `schemaVersion` (`1`)
- `occurredAt`
- `progressId`
- `userId`
- `enrollmentId`
- `courseId`
- `lessonId`

It deliberately contains no name, email address, access token, prompt, media URL, or other personal/content data.

## Operational semantics

- Kafka is disabled by default for local API startup; progress writes still enqueue durable outbox records.
- Enabling Kafka drains the backlog without changing application code.
- Producer durability uses `acks=all` and idempotence.
- Claim leases allow another instance to recover rows after a crashed dispatcher.
- Configuration validation requires the claim lease to exceed the Kafka producer blocking budget plus the acknowledgement timeout for the whole claimed batch.
- Pending-row lookup is backed by a partial index and each poll is batch-limited.
- Topic creation is an explicit infrastructure concern; the application does not silently create production topics.
- A growing pending count or repeated attempts is an operational alert condition.

## Consequences

Benefits:

- Progress state and event intent are atomic.
- Kafka outages do not make learner progress unavailable.
- Kafka remains replaceable behind an outbound port.
- The versioned contract is suitable for a later analytics or notification microservice.
- Multiple API instances can dispatch safely without holding database transactions during broker I/O.

Trade-offs:

- At-least-once delivery requires idempotent consumers.
- There is a short delay between the HTTP response and external event delivery.
- Outbox retention and operational replay require explicit policies.

Spring Modulith 1.4 event externalization was considered. Its publication registry improves recoverability, but the official documentation distinguishes the native asynchronous externalizer from a full outbox implementation. The project therefore keeps an explicit schema and lease model now; a migration to Spring Modulith's newer outbox integrations can be evaluated after a controlled framework upgrade.

## References

- Spring Modulith application events and externalization: https://docs.spring.io/spring-modulith/reference/events.html
- Spring Boot Kafka support: https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- Apache Kafka producer durability: https://kafka.apache.org/documentation/#producerconfigs
- Testcontainers Kafka module: https://java.testcontainers.org/modules/kafka/
