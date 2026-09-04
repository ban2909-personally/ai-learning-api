# ADR-005 — Idempotent learning analytics and controlled event recovery

Status: Accepted

Date: 2026-09-04

## Context

The learning module publishes the versioned `lesson.completed` contract at least once. Notifications already consume that contract, but the platform still lacks a queryable completion projection and an operational boundary for poison records, backlog visibility, retention, and replay.

Analytics is a separate business capability: it has a read-optimized lifecycle, can be rebuilt from retained integration events, and is a likely extraction candidate. It must not read learning repositories or persistence entities directly. Recovery operations are infrastructure concerns and must not leak Kafka or JDBC types into domain/application code.

## Decision

Create a top-level `analytics` Spring Modulith bounded context. Its only business-module dependency is `learning::events`; authenticated self-service queries may also depend on `platform::security` and shared transport errors.

### Completion projection

1. A Kafka inbound adapter validates the topic, record key, event headers, JSON contract, event type, schema version, and all identifiers before invoking the application boundary.
2. The application projects one immutable completion fact per source `eventId`.
3. PostgreSQL owns the idempotency boundary through the fact primary key. Redelivery reports a duplicate and performs no second insert.
4. A database transaction commits the fact before the Kafka record is acknowledged.
5. The fact stores only stable identifiers and timestamps already present in the PII-minimal integration event. Titles, email addresses, JWT claims, and mutable catalog content are not copied.
6. Learner-facing queries are always scoped from the authenticated JWT subject. They expose bounded per-course aggregates and never accept a caller-supplied user id.

The analytics module keeps framework-free domain/application types, transport DTOs in the web adapter, Kafka mapping in the inbound messaging adapter, and SQL in the persistence adapter.

### Failure classification and dead letters

- Transient processing failures receive a finite, configurable retry policy with backoff.
- After retries are exhausted, the consumer stores a durable dead-letter record containing the original topic/partition/offset, safe headers, payload, failure code, timestamps, and replay status.
- Raw exception messages and stack traces are logged/observed but are not persisted as an unbounded or potentially secret-bearing business field.
- Uniqueness on source topic/partition/offset prevents recovery callbacks from creating duplicate dead letters.
- A recovered poison record is acknowledged only after the dead-letter transaction commits.

### Controlled replay and retention

- Replay is an explicit administrative use case, not a public Kafka endpoint. It validates a dead-letter id, replayable status, target allowlist, retry budget, and optimistic state transition before republishing.
- Replay preserves the original event id and key so downstream projections remain idempotent. It adds bounded replay metadata rather than rewriting the payload.
- Concurrent replay requests cannot publish the same dead letter twice.
- Published outbox rows, completed dead letters, and completion facts have separate configurable retention windows. Cleanup uses bounded batches and scheduled adapters; it never deletes pending/claimed outbox work or unresolved dead letters.
- The first implementation documents the operational API/runbook before enabling destructive cleanup or replay in production.

### Observability and capacity

Expose low-cardinality metrics for projection outcomes, dead-letter outcomes, replay outcomes, pending outbox count, oldest pending outbox age, unresolved dead-letter count, and cleanup counts. Kafka client metrics remain the source for consumer lag.

Configuration defines reviewable warning/critical thresholds for backlog age, consumer lag, retry exhaustion, and unresolved dead letters. Deployment alert rules remain environment-specific, but the application supplies the metrics and documented threshold contract.

Ordering is required per learner key, not globally. Kafka partitions by the user UUID key. Projection correctness does not depend on arrival order because completion facts are immutable and aggregates use event timestamps. Load tests must demonstrate bounded database/Kafka pressure and no duplicate facts under redelivery.

## Consequences

Benefits:

- Analytics can evolve or move to a service without coupling to learning tables.
- At-least-once delivery remains safe and measurable.
- Poison records no longer block a partition indefinitely.
- Replay and retention become explicit, auditable operations instead of ad-hoc SQL or Kafka commands.

Trade-offs:

- Analytics is eventually consistent with progress writes.
- Dead-letter and replay state add schema and operational complexity.
- Application metrics define signals and thresholds; production alert routing still belongs to deployment infrastructure.
- Historical rebuild beyond retained events requires a separate backfill source and is not implied by ordinary replay.

## Compatibility

- Existing progress, notification, authentication, REST, and database behavior remain unchanged.
- The `lesson.completed` v1 producer contract remains immutable.
- Kafka consumers remain disabled by default for local/startup compatibility.
- New migrations are additive; existing migrations are not edited.
