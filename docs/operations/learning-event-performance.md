# Durable learning-event performance operations

## Purpose and boundary

This profile detects regressions across the existing authenticated lesson-completion
path: HTTP/JPA transaction, transactional outbox, Kafka dispatch, analytics
projection, and durable notification projection. It deliberately excludes connected
WebSocket fan-out so realtime delivery cannot hide or distort durable-pipeline
behavior.

The result is synthetic evidence from disposable local or CI containers. It is not
a production throughput claim, SLO, broker-sizing recommendation, autoscaling
policy, or concurrency forecast. AI-provider traffic, media bandwidth, replay,
broker failure, spike, saturation, recovery, and soak exercises remain separate.

## Run the isolated profile

Build the production image, then validate and run the harness:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/test-learning-event-performance.sh
bash scripts/test-learning-event-performance.sh
```

The harness requires Docker, curl, OpenSSL, GNU `timeout`, and the other commands it
checks before allocating resources. It creates one private network and disposable
PostgreSQL, Redis, Kafka, application, and k6 containers with unique names. Never
modify it to accept a shared or production database, broker, or application URL.

## Workload contract

The fixture lives outside Flyway and contains one published free course with eight
protected lessons. k6 registers forty students and enrolls all of them through the
real HTTP contracts. It then maps global iteration numbers to unique student/lesson
pairs and sends terminal progress transitions at eight arrivals per second for
thirty seconds.

A successful run requires at least 235 and at most 320 unique completions, checks
rate `1`, request failure rate `0`, dropped iterations `0`, p95 below 1,000 ms, and
p99 below 2,000 ms. The broad latency budgets detect large regressions; they are not
production objectives.

The drain phase is bounded. Its database gate requires the achieved iteration count
to equal completed progress, outbox total/published rows, analytics facts, and user
notifications. Pending outbox rows, retry attempts, leases, failure codes, and all
four bidirectional event-ID mismatches must be zero. Kafka source offsets must equal
the achieved count; analytics and notification group lag and both DLT offsets must
be zero.

## Evidence

Successful runs create three compact JSON files:

```text
target/performance/learning-event-http-summary.json
target/performance/learning-event-pipeline-summary.json
target/performance/learning-event-resource-summary.json
```

Their formats are `ai-learning-event-throughput-v1`,
`ai-learning-event-pipeline-v1`, and `ai-learning-event-resource-v1`. The pipeline
and resource summaries include the exact production image ID. CI retains only these
three files for fourteen days.

Resource evidence contains at least ten observations of application, PostgreSQL,
Redis, and Kafka container usage; JVM/Hikari maxima; maximum outbox backlog and
oldest age; database/cache counters; and final low-cardinality dispatch/consumer
counters. CPU can exceed 100% when a container uses more than one core. No observed
value is a production limit.

## Security and isolation

- Kafka uses the pinned full Apache image because the harness needs explicit topic,
  offset, and group-lag commands. It exposes no host port, disables topic
  auto-creation, and runs only on the per-run private network.
- PostgreSQL, Redis, and Kafka state is tmpfs-backed. The application and k6 roots
  are read-only. Containers drop capabilities, forbid privilege escalation, and
  have explicit CPU, memory, or PID bounds appropriate to their role.
- The application port is random and loopback-only. Actuator metrics are enabled
  only in the disposable runtime, remain JWT-protected, and must return HTTP 401
  without a token.
- Database, Redis, JWT, diagnostics, and student credentials are generated per run.
  Docker receives secrets by environment-variable name; JWTs and raw samples are
  not persisted as artifacts.
- Cleanup targets exact generated container/network names. After interruption,
  inspect resources carrying label `ai-learning.performance=learning-event` and
  remove only the exact names; never use broad Docker cleanup.

## Diagnosis and change discipline

Treat a missing summary, wrong format/image, insufficient sample count, unmatched
event count, non-zero retry/lag/DLT/duplicate/rejection/failure, unauthorized metric,
threshold breach, timeout, or leftover resource as a failed run. Do not weaken a
gate to make CI green.

Compare runs only when profile version, rate, duration, fixture, image, and runner
conditions are broadly comparable. First reproduce and locate a bottleneck using
the correlated evidence. Any index, cache, partition, concurrency, pool, heap,
thread, timeout, retry, or API change then needs a separate correctness, security,
recovery, and performance review.
