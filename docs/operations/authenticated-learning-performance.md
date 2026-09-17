# Authenticated learning performance operations

## Purpose and boundary

These profiles detect regressions in the existing JWT-protected learning path:
token verification, enrollment authorization, lesson access, progress reads, and
progress upserts. Read and write results remain separate so a write regression
cannot be hidden by faster reads.

The results are synthetic CI evidence from one disposable runner. They are not
production capacity, SLOs, autoscaling policy, instance sizing, or a forecast.
They do not cover lesson-completion events, Kafka, WebSocket fan-out, AI providers,
media delivery, spike, saturation, recovery, or soak behavior.

## Run the isolated profiles

Build the production image and validate the shell harness:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/test-authenticated-learning-performance.sh
```

Run each profile explicitly. There is no default profile:

```bash
AUTHENTICATED_LEARNING_PROFILE=read bash scripts/test-authenticated-learning-performance.sh
AUTHENTICATED_LEARNING_PROFILE=write bash scripts/test-authenticated-learning-performance.sh
```

An invalid or missing profile is rejected before a password, container, or network
is created. Each invocation creates a new PostgreSQL, Redis, application, k6, and
network set, and removes the exact set through an exit trap.

## Workload contract

Both profiles seed one published free course, one section, and four non-preview
lessons outside Flyway. k6 registers forty students through the real registration
API and enrolls each through the real enrollment API. The setup fails immediately
unless every identity receives a student JWT and an active enrollment.

| Profile | Arrival rate | Duration | Protected path | Minimum iterations |
| --- | ---: | ---: | --- | ---: |
| `read` | 20/s | 30 s | 70% progress GET, 30% lesson-player GET | 590 |
| `write` | 10/s | 30 s | progress PUT with `completed=false` | 295 |

Each possible VU receives one token from the in-memory setup result. Write requests
stay incomplete so the profile cannot accidentally become an outbox/Kafka test.
The harness verifies forty identities, forty enrollments, no completion events,
valid progress rows, and zero writes from the read profile before cleanup.

Correctness checks must all pass, the workload failure rate and dropped iteration
count must be zero, and achieved work must meet the table. Read p95/p99 budgets are
750/1,500 ms; write budgets are 1,000/2,000 ms. These intentionally broad CI
budgets catch large regressions and are not production latency objectives.

## Evidence

Successful runs create profile-specific compact JSON:

```text
target/performance/authenticated-learning-read-summary.json
target/performance/authenticated-learning-read-resource-summary.json
target/performance/authenticated-learning-write-summary.json
target/performance/authenticated-learning-write-resource-summary.json
```

The k6 files use format `ai-learning-authenticated-learning-v1`. Their latency
metrics include only steady-state learning requests; registration and enrollment
remain correctness-gated setup traffic. Resource files use format
`ai-learning-authenticated-learning-resource-v1` and cover setup plus workload, so
they conservatively include password hashing and enrollment overhead.

Each resource file records the exact production image ID, observed sample count and
span, application/JVM/Hikari maxima, PostgreSQL container/database counters, and
Redis container/server counters. CPU may exceed 100% when more than one CPU core is
used. No CPU, memory, PID, pool, database, or cache value is assigned a speculative
production threshold.

## Security and isolation

- The application port is random and bound only to loopback. Actuator `metrics` is
  enabled only in the disposable application and remains JWT-protected; an
  unauthenticated probe must return HTTP 401.
- Student passwords and the diagnostics password are generated for one run. Docker
  receives secret environment variables by name rather than command-line value.
  Student JWTs remain in k6 process memory; the diagnostics JWT is removed from the
  parent shell immediately after the collector forks.
- Credentials, JWTs, raw responses, raw resource samples, and database content are
  never artifacts. CI retains only the four bounded summaries for 14 days.
- PostgreSQL data, Redis data, identities, enrollments, progress, and the diagnostics
  identity live only in tmpfs-backed disposable containers. Never adapt this script
  to accept an external/shared database URL.
- Containers run with dropped capabilities and no privilege escalation. The
  application and k6 filesystems are read-only with bounded tmpfs; the application
  has explicit CPU, memory, and PID limits.

## Diagnosis and change discipline

Compare only identical profile format, rate, duration, dataset, production image,
and broadly comparable runner conditions. First reproduce a regression, then use
the correlated JVM/Hikari/PostgreSQL/Redis/container evidence to select the next
investigation. An index, cache, pool, heap, thread, or API change requires a
repeatable diagnosed bottleneck plus correctness and security regression testing.

If a run is interrupted, inspect only resources labeled
`ai-learning.performance=authenticated-learning`; never use broad Docker cleanup.
The normal exit trap removes exact generated names. A missing summary, insufficient
samples, wrong profile/format/image, unauthorized metric, failed invariant, failed
threshold, or leftover resource is a failed run, not partial evidence.
