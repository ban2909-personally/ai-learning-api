# Development report — Phase 6.1 durable learning events

Date: 2026-09-04

API branch: `feature/realtime-learning-events`

Frontend: unchanged in this slice; authenticated realtime notifications are intentionally scoped to slice 6.2.

## Outcome

Slice 6.1 establishes a durable event backbone for later notification and analytics projections. The first transition of a learner's lesson progress from incomplete to complete now stores a versioned `lesson.completed` event in PostgreSQL in the same transaction. An optional background dispatcher claims pending records safely across instances and publishes them to Kafka with acknowledgement, bounded retry, and low-cardinality metrics.

Existing HTTP request/response contracts and lesson-progress database behavior remain compatible. Kafka downtime cannot reject an otherwise valid progress write.

## Architecture and module boundaries

- The framework-free `LessonCompleted` domain event records a completed learning transition.
- The learning-owned `LessonCompletedEventV1` named Modulith interface is the versioned integration contract.
- `LessonProgressService` detects only the first incomplete-to-complete transition and appends through the `LearningEventOutbox` application port.
- PostgreSQL, Kafka, scheduling, and Micrometer remain in inbound/outbound adapters.
- `LearningEventDispatcher` depends only on application ports and Java time; it has no Spring, JDBC, or Kafka dependency.
- No module reads another module's repositories, entities, or internal infrastructure.
- ADR-003 records the transaction, at-least-once delivery, extraction seam, and alternatives considered.

## Transactional and database behavior

- Additive Flyway V8 creates `learning_event_outbox`; no existing migration was modified.
- Progress and outbox insertion share one transaction. A forced outbox constraint failure proves that the progress write rolls back.
- A logical completion has a unique aggregate/event constraint, making repeated completion saves idempotent at the outbox boundary.
- The outbox stores immutable JSON plus event identity, schema version, aggregate identity, message key, occurrence time, retry state, and lease state.
- JSON must be an object and is bounded to 16 KiB.
- Pending lookup and published retention queries have dedicated partial indexes.

## Kafka delivery and multi-instance safety

- Topic: `ai-learning.learning.lesson-completed.v1`.
- Key: server-derived learner UUID, preserving per-learner partition ordering.
- Headers: `event_id`, `event_type`, `schema_version`, and `content_type`.
- Producer durability uses `acks=all` and Kafka idempotence.
- Publication waits for broker acknowledgement before marking the row published.
- Claims use an atomic PostgreSQL CTE with `FOR UPDATE SKIP LOCKED`, owner identity, and expiry lease.
- Broker failures release the row with capped exponential backoff and do not stop other rows in the batch.
- A crash after Kafka acknowledgement but before the database update may duplicate delivery; consumers must deduplicate by `eventId`.
- Configuration validation requires the claim lease to exceed the producer blocking budget plus acknowledgement timeout for the entire batch.

## Security and privacy audit

- Event identity, learner identity, course/enrollment/lesson identity, topic, key, and schema version are derived server-side.
- Payload contains UUIDs, event metadata, completion timestamp, and progress position/duration only.
- Payload deliberately excludes name, email, token, prompt, media URL, and lesson content.
- No payload or credential is written to application logs.
- Kafka is disabled by default; credentials and broker security settings remain deployment concerns and are not committed.
- The repository diff contains no production secret. Password strings are limited to local examples and isolated Testcontainers fixtures.

## Runtime configuration

The dispatcher is opt-in:

```text
LEARNING_EVENTS_KAFKA_ENABLED=false
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
LESSON_COMPLETED_TOPIC=ai-learning.learning.lesson-completed.v1
LEARNING_EVENTS_BATCH_SIZE=10
LEARNING_EVENTS_POLL_DELAY=PT1S
LEARNING_EVENTS_CLAIM_LEASE=PT5M
LEARNING_EVENTS_SEND_TIMEOUT=PT10S
LEARNING_EVENTS_KAFKA_MAX_BLOCK_MS=10000
LEARNING_EVENTS_RETRY_INITIAL=PT2S
LEARNING_EVENTS_RETRY_MAX=PT5M
```

The topic is provisioned by infrastructure. Production must configure Kafka authentication/encryption outside source control. Changes to batch size or publication timeouts must preserve the validated claim-lease budget.

## Observability and operability

- `learning.events.dispatch{outcome=published}` counts acknowledged publications.
- `learning.events.dispatch{outcome=failed}` counts broker failures scheduled for retry.
- Metric tags are bounded and contain no user/event identifiers.
- Outbox attempts, availability time, lock owner/expiry, published time, and a bounded failure code remain queryable for operations.
- Backlog/lag alert thresholds, retention, replay, and dead-letter runbooks remain explicit slice 6.3 work.

## Verification evidence

Targeted dispatcher/Kafka command:

```text
mvn -Dmaven.repo.local=E:\ai-learning-api\.m2\repository -Dtest=LearningEventDispatcherTest,LearningEventsPropertiesTest,KafkaLearningEventBrokerTest,KafkaLearningEventBrokerIntegrationTest test
```

Result: 10 tests run; 0 failures; 0 errors; 0 skipped.

Full backend gate:

```text
mvn -Dmaven.repo.local=E:\ai-learning-api\.m2\repository clean verify
```

Result:

- 98 tests across 38 suites; 0 failures; 0 errors; 0 skipped.
- Spring Modulith verification passed, including the isolated learning module test.
- ArchUnit hexagonal dependency rules passed.
- Testcontainers PostgreSQL, Redis, MinIO, and Kafka tests passed.
- Flyway V1 through V8 ran against clean PostgreSQL containers.
- Kafka contract test verified key, value, and all versioning/content headers against a real broker.
- Concurrent outbox test proved that two workers cannot claim the same pending event.
- JaCoCo analyzed 152 classes and all configured coverage checks passed.
- `git diff --check` passed and the secrets/PII/logging audit found no production credential or sensitive payload field.

## Cohesive commits before report

- `1554991 docs: define durable learning event delivery`
- `2d0ee85 feat: persist lesson completion events atomically`
- `3e72f15 feat: dispatch learning events through Kafka`

Feature/main GitHub Actions links and merge commit will be appended after both remote workflows complete successfully.

## Changed files

The slice changes 38 files including this report and README:

- Architecture and reports: ADR-003, Phase 6 plan, Phase 5 closure, this development report, and README.
- Runtime/build: `.env.example`, `pom.xml`, and `application.yml`.
- Domain/API/application: completion event, V1 integration event, dispatcher use case, outbox/broker/monitor ports, and progress/dispatcher services.
- Adapters/config: PostgreSQL outbox, Kafka publisher, scheduled trigger, Micrometer monitor, validated properties, and module wiring.
- Database: additive Flyway V8 outbox migration.
- Tests: service/config/unit tests; PostgreSQL atomicity, concurrency, lease and retry integration tests; Kafka contract test; learning Modulith fixture updates.

## Sources used for the design

- Spring Modulith event publication: https://docs.spring.io/spring-modulith/reference/events.html
- Spring Boot Kafka support: https://docs.spring.io/spring-boot/reference/messaging/kafka.html
- Apache Kafka producer configuration: https://kafka.apache.org/documentation/#producerconfigs
- Testcontainers Kafka module: https://java.testcontainers.org/modules/kafka/

## Deferred intentionally

- Notification consumer/projection, authenticated history endpoint, JWT-authenticated STOMP delivery, reconnect catch-up, and responsive frontend notification UI belong to slice 6.2.
- Analytics projections, dead-letter/replay procedures, retention, lag/backlog alerts, and load/backpressure validation belong to slice 6.3.
- Schema Registry/Avro remains deferred until independent consumers make its operational cost worthwhile; JSON V1 is explicit and contract-tested now.
