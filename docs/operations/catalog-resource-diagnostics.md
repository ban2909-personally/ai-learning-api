# Catalog resource diagnostics operations

## Purpose and boundary

This runbook correlates the Phase 8.4a public-catalog workload with application,
JVM, Hikari, PostgreSQL, Redis, and container observations. Use it to decide where
to investigate a repeatable regression before proposing a tuning change.

The observations come from a disposable synthetic environment and one runner.
They are not production CPU/memory limits, pool sizing, cache targets, database
capacity, SLIs/SLOs, availability evidence, or autoscaling policy. Do not compare
unlike machines or extrapolate this run to production traffic.

## Run the isolated diagnostic profile

Build the same production image used by CI, verify both scripts, and explicitly
enable resource capture:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/collect-catalog-resource-diagnostics.sh scripts/test-catalog-performance.sh
PERFORMANCE_CAPTURE_RESOURCES=true bash scripts/test-catalog-performance.sh
```

The flag accepts only `true` or `false`. Omitting it runs the original regression
baseline, removes stale diagnostic evidence, does not expose the metrics endpoint,
and creates only `catalog-performance-summary.json`.

An opt-in successful run creates two correlated non-secret files:

```text
target/performance/catalog-performance-summary.json
target/performance/catalog-resource-summary.json
```

The resource summary must use format `ai-learning-catalog-resource-v1` and records
the production image content ID, fixed workload identity, sample count, minimum
sample requirement, and observed time span. Treat either a missing/invalid summary
or a failed k6 threshold as a failed diagnostic run.

## Security and isolation

- The orchestrator exposes the existing Actuator `metrics` endpoint only when the
  opt-in flag is true and only through the disposable API's loopback-bound host
  port. Production configuration files are unchanged.
- An unauthenticated metrics probe must return HTTP 401 before collection begins.
  The collector then uses a short-lived JWT issued through the existing registration
  contract to read metrics through the normal security filter chain.
- The diagnostics student's password is generated and sent in the HTTP request
  body through standard input. The bearer token is never a command-line argument,
  log line, file, or artifact; the parent shell removes its copy immediately after
  starting the collector.
- The identity and all observations are destroyed with the ephemeral PostgreSQL,
  Redis, and application containers. No production or shared database is accepted.
- The same non-root, read-only filesystem, dropped-capability, no-privilege-
  escalation, tmpfs, unique-network, exact-name cleanup, and immutable external-
  image controls from the baseline remain in force.

## Evidence fields

The summary records maxima observed during the workload, plus final subsystem
counters:

| Area | Evidence |
| --- | --- |
| Application container | CPU %, memory %, PID count |
| JVM | used memory bytes |
| Hikari | active and pending connections |
| Catalog cache | existing application hit, miss, and failure counters |
| PostgreSQL container | CPU % and memory % |
| PostgreSQL database | connections, commits, rollbacks, block reads/hits, temporary files/bytes, deadlocks |
| Redis container | CPU % and memory % |
| Redis | keyspace hits/misses, evictions, peak used memory bytes |

Application cache hits/misses must numerically match Redis keyspace hits/misses,
the cache failure counter must be zero, all required values must be numeric, and at
least ten observations must span the workload. These are evidence-integrity
guards, not production resource budgets.

## Diagnosis workflow

1. Confirm the k6 correctness, error, dropped-iteration, p95, and p99 fields first.
   Never investigate resource speed while accepting an incorrect response.
2. Compare only runs with the same workload format, dataset, rate, duration, path
   mix, image content ID, and comparable host conditions. Reproduce a suspected
   regression at least three times.
3. If application CPU rises, inspect request profiles and hot methods before
   changing thread counts. If JVM used memory rises, capture GC/allocation evidence
   in a dedicated controlled run before changing heap.
4. If Hikari pending connections rise, correlate PostgreSQL CPU, block reads/hits,
   locks, query plans, and connection duration before changing pool size.
5. If PostgreSQL block reads, temporary bytes, rollback, or deadlock counters change,
   inspect the exact query/transaction path and repeat with database-native plans
   and statistics. Do not add an index from this aggregate summary alone.
6. If cache failures, misses, evictions, or Redis memory change, verify key shape,
   TTL, serialization, and cache/server counters before changing cache size or TTL.
7. Change one diagnosed control at a time, retain before/after summaries, and rerun
   Maven, architecture, recovery, security, baseline, and diagnostics gates.

Zero sampled Hikari active/pending connections does not prove the pool was unused;
short queries can finish between observations. It proves only that no active or
pending connection was observed at the recorded instants.

## CI and cleanup

The `performance-diagnostics` CI job runs independently after `container-image` and
retains both compact summaries for 14 days under
`catalog-resource-<commit-sha>`. The original `performance-baseline` job remains a
separate unobserved run.

After any abnormal host shutdown, inspect only labeled resources:

```bash
docker ps --all --filter label=ai-learning.performance=catalog
docker network ls --filter label=ai-learning.performance=catalog
```

The normal exit trap removes the exact collector process, k6/API/Redis/PostgreSQL
containers, network, bearer-token environment, raw samples, and temporary directory.
Do not delete unrelated Docker Desktop projects such as `ai-learning-local`.
