# Public catalog performance baseline operations

## Purpose and evidence boundary

This runbook operates the Phase 8.4a engineering regression baseline for public
catalog reads. It proves response correctness and detects large throughput or
latency regressions through the production container image, Redis, JPA, Flyway,
and PostgreSQL. It runs only against disposable local containers.

The result is specific to the machine or CI runner and the synthetic dataset. It
is not a production SLO, capacity forecast, availability promise, autoscaling
policy, or release load test. Never point this harness at production or a shared
developer environment.

## Prerequisites

- Docker Engine with Linux-container support;
- Bash, `curl`, `openssl`, and standard Unix text tools;
- the repository production image tagged as `ai-learning-api:ci`;
- enough local capacity for PostgreSQL, Redis, the API, and k6 within the script's
  explicit CPU and memory bounds.

From the repository root, build and run the same contract used by CI:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/test-catalog-performance.sh
bash scripts/test-catalog-performance.sh
```

Set `PERFORMANCE_APP_IMAGE` only when validating another locally available image:

```bash
PERFORMANCE_APP_IMAGE='ai-learning-api:candidate' bash scripts/test-catalog-performance.sh
```

Do not set it to an unreviewed remote tag. Build or pull the intended immutable
candidate through the approved supply-chain procedure first.

## Fixed workload contract

| Control | Value |
| --- | --- |
| Dataset | one instructor and 5,000 deterministic published courses |
| Arrival rate | 25 iterations per second |
| Duration | 30 seconds |
| Expected scheduled iterations | approximately 750; minimum accepted is 740 |
| Popular-page reads | 60%, warmed once and expected to exercise Redis hits |
| Category-filtered reads | 25%, expected total of 1,250 backend courses |
| Stable course-detail reads | 15%, fixed course and instructor assertions |
| Response checks | 100% must pass |
| HTTP request failure rate | less than 1% |
| Dropped iterations | zero |
| Request-duration p95 | less than 750 ms |
| Request-duration p99 | less than 1,500 ms |

Seed data lives in `performance/catalog-seed.sql`; it is applied only after the
application has completed Flyway startup. It is not a production migration and is
never inserted by normal application startup. The mixed workload lives in
`performance/catalog-read.js`.

## Result and interpretation

A successful run writes the compact, non-secret result to:

```text
target/performance/catalog-performance-summary.json
```

Confirm `format` is `ai-learning-catalog-performance-v1`, then review the dataset,
requested arrival rate and duration, checks rate, achieved iterations, dropped
iterations, request failure rate, p95, and p99. A missing field, missing file,
incorrect response body, or breached threshold fails the run.

Compare results only when the image, workload, dataset, and host conditions are
known. One faster run does not prove an optimization, and one slower shared-host
run does not identify a bottleneck. CI retains only this compact summary for 14
days; response bodies, database contents, raw request logs, and credentials are
not artifacts.

## Failure triage

Investigate in this order:

1. Correctness failures: inspect the failed endpoint and API logs before considering
   latency. A fast incorrect response is always a failure.
2. Request errors: identify application, PostgreSQL, Redis, networking, or startup
   failures and reproduce with the same immutable image.
3. Dropped iterations: verify runner contention and application saturation; do not
   lower the arrival rate merely to turn the gate green.
4. Latency regression: reproduce at least three controlled runs, then capture JVM,
   CPU, memory, connection-pool, PostgreSQL query-plan/statistics, and Redis cache
   evidence to locate the constrained resource.
5. Proposed tuning: change one diagnosed control at a time and rerun correctness,
   architecture, security, recovery, and performance gates.

Do not add an index, expand pools or threads, enlarge heap, or weaken caching,
authorization, encryption, logging, or recovery controls based only on this summary.
Resource diagnosis and broader traffic profiles belong to Phase 8.4b.

For an isolated opt-in run that correlates this workload with JVM, Hikari,
PostgreSQL, Redis, and container resource evidence, follow
`docs/operations/catalog-resource-diagnostics.md`. The default command above does
not expose Actuator metrics or create a resource summary.

## Isolation, security, and cleanup

The orchestrator creates a unique labeled Docker network and PostgreSQL, Redis,
API, and k6 containers. Credentials are generated per run. Service data and Redis
configuration use tmpfs; the application filesystem is read-only; containers run
non-root, drop capabilities, and disallow privilege escalation. Images are pinned
by immutable digest where they are external to the repository build.

An exit trap removes the exact containers and network on success, failure, or
interruption. Verify cleanup when diagnosing an abnormal host shutdown:

```bash
docker ps --all --filter label=ai-learning.performance=catalog
docker network ls --filter label=ai-learning.performance=catalog
```

Do not delete unrelated containers. The existing `ai-learning-local` stack may
remain running because this harness uses unique resource names, a separate network,
and an ephemeral host port.

## Remaining work

Phase 8.4b must add authenticated journeys, write/event/WebSocket/AI/media-specific
profiles, resource telemetry, spike, saturation, recovery, and soak exercises.
Phase 8.4c requires real topology, instance sizes, downstream quotas, traffic and
growth forecasts, business-approved SLIs/SLOs, error budgets, capacity headroom,
alert routing, rollback ownership, and operational approval.
