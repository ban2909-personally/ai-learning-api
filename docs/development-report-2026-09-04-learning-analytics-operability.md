# Phase 6.3 development report — Learning analytics and event operability

Date: 2026-09-04

Branch: `feature/learning-analytics-operability`

Status: implementation checkpoint and local quality gate complete; load/capacity validation and Git delivery remain open in the Phase 6 checklist.

## Outcome

The platform now projects the existing PII-minimal `lesson.completed` v1 integration event into an independent analytics read model. Authenticated learners can retrieve a bounded per-course completion summary without the analytics module reading learning repositories, entities, or tables.

Notification and analytics consumers now stop retrying poison records indefinitely. After a bounded attempt count they publish to separate configured dead-letter topics and emit recovery metrics. The learning outbox exposes real pending-count and oldest-age gauges. A production-oriented runbook defines initial alert thresholds, topic retention, data handling, exact replay controls, rate limits, reconciliation, and safe cleanup constraints.

## Architecture and contracts

- New top-level `analytics` Spring Modulith bounded context.
- Allowed dependencies: `learning::events`, `platform::security`, and `sharedkernel::error`; actual implementation uses only the public event contract and shared error contract.
- Framework-free completion fact, application service, use case, and persistence port.
- Kafka, HTTP, Micrometer, JDBC, Spring transactions, configuration, and security annotations stay in adapters/configuration.
- REST response DTOs do not enter application code.
- No JPA entity or repository from another module is accessed.
- `GET /api/v1/me/learning-analytics?courseLimit=20` derives identity exclusively from the validated JWT subject and enforces a 1–100 bound at both web and application boundaries.

## Persistence and performance

Flyway V10 adds `learning_completion_facts` with:

- source `event_id` primary-key idempotency;
- semantic uniqueness on `(user_id, enrollment_id, lesson_id)`;
- stable identifiers and completion/projection timestamps only;
- indexes for per-user course aggregation and completion history;
- no mutable course/lesson title or personal profile data.

The aggregate query is a single bounded PostgreSQL statement. Window aggregates preserve lifetime totals while the returned course list is limited and ordered deterministically by last completion time and course id.

The outbox monitor runs only when event publication is enabled. It performs one aggregate query per configured poll interval and stores values in in-memory Micrometer gauges; metric scraping does not issue database queries.

## Kafka failure handling

- Consumers use record acknowledgement and commit projection transactions before successful return.
- Topic, key, headers, content type, event type, schema version, payload, and required identifiers are validated.
- Retry delay is at least one millisecond, attempt count is at least one, and each DLT must differ from its source topic.
- Notification and analytics use separate consumer groups and DLT topics.
- DLT publication preserves source data and must succeed before the dead-letter counter increments.
- Low-cardinality metrics expose projected, duplicate, rejected, dead-letter, dispatch success/failure, outbox pending count, and oldest pending age. Standard Kafka client metrics remain the consumer-lag source.

## Security and operations

- Learners cannot supply or query another user id.
- Integration records contain identifiers only; no JWT, email, name, course title, or lesson title is introduced.
- No replay or cleanup HTTP endpoint is exposed.
- The runbook requires peer approval, exact DLT partition/offset ranges, target allowlisting, a 500-record maximum per change, an initial 10 records/second rate, and stop conditions tied to service health.
- Replay preserves key, payload, event id and contract headers so downstream idempotency remains effective.
- Cleanup is disabled by default; pending/leased outbox rows, active analytics facts, and unread notifications are not deleted by operational SQL.

## Tests executed

Targeted gates covered domain invariants, Mockito application behavior, PostgreSQL/Flyway projection queries, JWT-scoped MockMvc responses, Kafka envelope validation, consumer outcome metrics, DLT configuration, a real Kafka poison-record-to-DLT flow, outbox gauges, Spring Modulith isolation, and ArchUnit dependency rules.

Final local gate:

```text
mvn clean verify
Tests run: 150, Failures: 0, Errors: 0, Skipped: 0
JaCoCo: 87.43% line coverage (1,794 covered / 258 missed; 196 classes)
BUILD SUCCESS
```

## Commits in this checkpoint

- `cf50a93 docs: define learning analytics operability`
- `b8e8663 feat: add learner completion analytics`
- `af059f1 feat: consume completion events into analytics`
- `9f5ceed feat: dead-letter failed event projections`
- `3f7b1dc feat: expose learning event backlog signals`

## Remaining Phase 6.3 gates

- Run an explicit bounded load/capacity scenario for partition ordering, duplicate redelivery, database pressure, and consumer backpressure.
- Complete feature CI, merge, rerun `main` gates, push, and verify `main` CI only after that capacity gate is accepted.
- A learner-facing frontend analytics view may be delivered as a separate responsive product slice; this backend checkpoint does not add an unused UI shell.
