# AI Mentor concurrency and token-cost performance operations

## Purpose and boundary

This profile detects regressions across the existing authenticated AI Mentor path:
lesson access, Redis quota, conversation persistence, the OpenAI Responses HTTP/SSE
adapter, application SSE delivery, persisted token usage, and bounded executor
behavior. It calls a deterministic private protocol simulator, never a commercial
provider.

Results apply only to the isolated single-instance topology, fixed simulator delay,
synthetic token counts, and fixture described below. They are not an answer-quality
evaluation, production capacity claim, SLO, provider-latency measurement, pricing
estimate, or deployment-sizing recommendation.

## Run the isolated profile

Build the production image, validate the shell scripts, and run the harness:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/test-ai-mentor-performance.sh
bash scripts/test-ai-mentor-performance.sh
```

Set `PERFORMANCE_APP_IMAGE` only to verify another image already present on the
local Docker daemon. The harness requires Docker, curl, OpenSSL, and the commands it
checks before allocating resources. It creates a private network and disposable
PostgreSQL, Redis, provider, application, and k6 containers. Never point it at a
shared or production service.

## Workload and regression contract

The fixture is outside Flyway and contains one published free course with one
protected lesson. k6 registers and enrolls forty students through the real HTTP
contracts, then schedules eight mentor turns per second for thirty seconds. Each
identity stays below the configured twenty-request quota.

The provider waits 500 ms per request, validates the real Responses request shape,
emits two fixed SSE deltas, and reports 128 input plus 64 output tokens under the
synthetic `mentor-performance-stub` model. The application output ceiling remains
192 tokens.

The client gate requires at least 235 complete turns, no failed request or dropped
iteration, exactly one accepted event, one or more deltas, exactly one completion,
no error event, p95 below 2,000 ms, and p99 below 4,000 ms. Provider maximum
concurrency must be between two and the executor maximum of 32.

The pipeline gate requires exact agreement between client completions, provider
requests/completions, application accepted/completed metrics, conversation and
message rows, token totals, and Redis quota consumption. Provider rejection,
failure, active requests after drain, incomplete usage rows, unexpected models,
application failure outcomes, and quota breaches must all be zero.

## Evidence

Successful runs create three compact JSON files:

```text
target/performance/ai-mentor-client-summary.json
target/performance/ai-mentor-pipeline-summary.json
target/performance/ai-mentor-resource-summary.json
```

Their formats are `ai-learning-ai-mentor-performance-v1`,
`ai-learning-ai-mentor-pipeline-v1`, and
`ai-learning-ai-mentor-resource-v1`. Pipeline and resource evidence contain the
exact production image ID. CI retains only these summaries for fourteen days.

Resource evidence requires at least ten correlated samples and records application,
JVM, Hikari, PostgreSQL, Redis, provider, and container maxima. CPU can exceed 100%
when a container uses more than one core. No measured maximum is a production
limit. Token counts are a stable cost proxy; converting them to currency requires
an approved current pricing source, region, model, cache policy, and budget owner.

## Executor diagnosis

The first local profile completed 238 turns without request failures, but dropped
two scheduled iterations and measured p95 at 2,126 ms. The executor had four core
workers, a maximum of 32, and a queue of 100. `ThreadPoolTaskExecutor` filled core
workers and queued work before creating workers up to the maximum, so the request
rate ran at the four-worker theoretical boundary and accumulated queue latency.

The executor is now explicitly bounded and configurable with defaults of eight
core workers, 32 maximum workers, and queue capacity 32. The passing local rerun
completed 240 turns with zero failures or drops, p95 at 542 ms, p99 at 1,074 ms,
and provider maximum concurrency eight. Treat these numbers as diagnostic evidence,
not a guarantee. Change `MENTOR_EXECUTOR_CORE_POOL_SIZE`,
`MENTOR_EXECUTOR_MAX_POOL_SIZE`, or `MENTOR_EXECUTOR_QUEUE_CAPACITY` only with a
repeatable workload and validation that core does not exceed maximum.

## Security and cleanup

- Only the application port is bound to a random loopback port. PostgreSQL, Redis,
  and the provider remain reachable only on the per-run private network.
- The unauthenticated actuator probe must return HTTP 401 before a disposable
  diagnostics JWT is created.
- The simulator validates its generated bearer credential, does not log prompts,
  and retains only aggregate counters. It never receives a commercial API key.
- Database, Redis, JWT, provider, diagnostics, and student credentials are generated
  per run and never written to summaries, URLs, or retained logs.
- PostgreSQL and Redis state is tmpfs-backed. Application, provider, and k6 roots
  are read-only; containers drop capabilities and disallow privilege escalation.
- Cleanup targets only generated names. After interruption, inspect resources with
  label `ai-learning.performance=ai-mentor` and remove only those exact resources;
  never use broad Docker cleanup.

Treat a missing field or summary, wrong image ID, insufficient samples, inexact
count, non-zero rejection/failure/drop, latency breach, unauthorized metrics access,
or leftover resource as a failed run. Diagnose the client, pipeline, and resource
summaries together; do not weaken a gate to make CI green.
