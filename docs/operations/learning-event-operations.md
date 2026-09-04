# Learning event operations runbook

## Scope and ownership

This runbook covers the `lesson.completed` v1 source topic, notification and analytics consumer groups, their dead-letter topics, and the PostgreSQL transactional outbox. Production topics are provisioned outside the application; auto topic creation must be disabled.

The application never enables a consumer, performs replay, or deletes retained records by default. An operator must use an approved change record, production role, bounded time window, and peer review.

## Required topics

| Purpose | Default name | Partition rule |
| --- | --- | --- |
| Source | `ai-learning.learning.lesson-completed.v1` | Keyed by user UUID |
| Notification DLT | `ai-learning.notifications.lesson-completed.dlt.v1` | Same partition number as source |
| Analytics DLT | `ai-learning.analytics.lesson-completed.dlt.v1` | Same partition number as source |

DLT partition counts must be greater than or equal to the source partition count. Source retention must cover the maximum detection and replay window. Recommended initial policy: source 14 days, DLT 30 days, replication factor 3 in production. These are broker policies, not application defaults.

## Signals and initial thresholds

| Signal | Warning | Critical | Response |
| --- | --- | --- | --- |
| `learning.events.outbox.oldest.age.seconds` | > 60 s for 5 min | > 300 s for 5 min | Check dispatcher, broker reachability, producer timeout and DB leases. |
| `learning.events.outbox.pending` | > 1,000 for 5 min | > 10,000 for 5 min | Stop capacity-heavy replay; inspect publish failure rate and scale only after bottleneck confirmation. |
| Kafka consumer records-lag-max per application group | > 1,000 for 5 min | > 10,000 for 5 min | Inspect processing latency, partition skew and DB saturation. |
| `notifications.kafka.dead.letter` or `analytics.kafka.dead.letter` increase | any increase | > 10 in 5 min | Quarantine replay, identify contract or dependency failure, and preserve samples. |
| `*.kafka.processing{outcome="rejected"}` / total | > 1% for 5 min | > 5% for 5 min | Compare event headers/schema and deployment versions; do not blind-replay. |

Thresholds are starting points. Recalibrate them from measured throughput and SLOs without adding user, course, event, exception, or topic values as metric labels.

## Dead-letter triage

1. Record the DLT topic, partition, offset, source topic/partition/offset headers, timestamp, key, event id, event type and schema version.
2. Classify the cause as contract-invalid, unsupported version, authorization/configuration, transient dependency, or code defect.
3. Never paste full payloads into tickets or chat. The current contract is PII-minimal, but payload handling still follows production data controls.
4. Fix and deploy the cause first. Verify normal consumer lag and outbox age are below warning thresholds.
5. Obtain peer approval for an exact inclusive DLT offset range and maximum record count. Default maximum is 500 records per replay change.

## Controlled replay

Replay copies records from one approved DLT topic back to its original allowlisted source topic. It must preserve the original key, payload, `event_id`, `event_type`, `schema_version`, and `content_type`. Add operational replay headers containing only the change id, original DLT coordinates, and replay timestamp.

Before execution:

- verify the target equals `ai-learning.learning.lesson-completed.v1`;
- verify every record is `lesson.completed` schema v1 and the key matches payload `userId`;
- use a new, uniquely named replay consumer group;
- set a bounded record count and explicit partition/offset range;
- start at no more than 10 records/second and stop if DB latency, error rate, lag, or outbox age reaches warning level.

After execution, compare replayed count with downstream `projected + duplicate + dead-lettered` outcomes. Idempotent event ids mean an already projected record must report duplicate rather than create a second fact or notification. Keep the DLT record until the 30-day retention window expires; do not delete it manually as proof of replay.

## Retention and cleanup

- Pending, retrying, or leased outbox rows are never eligible for cleanup.
- Published outbox rows may be deleted only after source retention and reconciliation, using batches of at most 1,000 rows per transaction. Initial minimum age: 30 days.
- Completion analytics facts are the active read model and have no automatic deletion policy in Phase 6.3. Privacy/account deletion requires a separately reviewed user-erasure use case.
- Notification retention remains a product decision; no operational SQL should delete unread user notifications.
- DLT retention is broker-managed at 30 days. Increasing retention is safe during an incident; reducing it requires incident-owner approval.

All cleanup remains disabled until its environment-specific scheduled job, backup validation, rollback plan, and query-duration budget are reviewed. Never run an unbounded `DELETE` in production.
