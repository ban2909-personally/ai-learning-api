# Development report — Catalog resource diagnostics

Date: 2026-09-15

Branch: `feature/catalog-resource-diagnostics`

## Outcome

Phase 8.4b1 adds opt-in, fail-closed resource diagnosis to the isolated public
catalog workload. A diagnostic run correlates the existing correctness/latency
summary with production-image identity, container CPU/memory/PIDs, JVM memory,
Hikari pool observations, application cache counters, PostgreSQL database counters,
and Redis server counters.

The original regression mode remains separate and unchanged. This slice changes no
Java application source, `pom.xml`, API contract, security rule, production
configuration default, Flyway migration, query/index, cache behavior, connection
pool, thread/heap setting, module dependency, or runtime image content.

## Changed files

- `.github/workflows/ci.yml` — adds an independent `performance-diagnostics` job
  after the production-image gate and retains both correlated summaries.
- `README.md` — links the diagnostics operations entry point and evidence boundary.
- `docs/architecture/ADR-013-catalog-resource-diagnostics.md` — records isolation,
  observation, security, summary, and no-speculative-tuning decisions.
- `docs/architecture/phase-8.4-performance-readiness-plan.md` — closes Phase 8.4a
  with exact feature/main evidence and tracks Phase 8.4b1.
- `docs/development-report-2026-09-15-catalog-performance-baseline.md` — records the
  final Phase 8.4a feature, merge, main CI, and artifact evidence.
- `docs/operations/catalog-performance-baseline.md` — distinguishes default
  regression operation from opt-in diagnostics.
- `docs/operations/catalog-resource-diagnostics.md` — defines operation, evidence
  interpretation, comparison discipline, security, cleanup, and diagnosis order.
- `scripts/collect-catalog-resource-diagnostics.sh` — samples protected Actuator and
  Docker statistics, captures PostgreSQL/Redis counters, validates consistency, and
  writes the versioned compact summary.
- `scripts/test-catalog-performance.sh` — integrates the collector behind an exact
  opt-in flag, provisions a disposable diagnostics identity, protects its token,
  validates both summaries, and prevents stale evidence.

## Architecture and maintainability

- Diagnostics remain an operational adapter outside every business bounded context
  and outside the production image. No speculative application abstraction or
  generic utility was introduced.
- The collector owns observation and summary generation; the existing orchestrator
  owns isolated lifecycle and workload execution; k6 continues to own response and
  latency assertions.
- Default and observed workloads use the same deterministic dataset, endpoint mix,
  rate, duration, and production image. CI runs them in separate jobs so observation
  overhead cannot change the original regression signal.
- The resource summary includes the exact Docker content ID and a versioned format.
  It records both minimum sample count and observed span instead of claiming a
  scheduling precision the host cannot guarantee.
- Application cache and Redis server hit/miss counters must agree. Missing,
  unauthorized, non-numeric, insufficient, stale, or inconsistent evidence fails
  the run.

## Security

- Actuator metrics remain excluded by production defaults. They are exposed only
  for an opt-in disposable container on a loopback-bound host port and remain behind
  the existing JWT resource-server filter.
- The harness proves an unauthenticated metrics request returns HTTP 401 before
  collection begins.
- A one-run student is registered through the existing HTTP contract using a
  generated password supplied through standard input. The access token is never a
  command-line argument, file, log, or artifact; the parent shell removes its copy
  immediately after the collector forks.
- Raw samples live under a private temporary directory and are deleted by the exit
  trap. Only two compact non-secret JSON summaries are retained.
- All pre-existing non-root, read-only, tmpfs, dropped-capability,
  no-privilege-escalation, immutable-image, unique-name/network, and exact cleanup
  controls remain in force.

## Final local diagnostic evidence

The final documented run used production image content ID
`sha256:d7cb1d42c9e27e1a66b581a0011104e64396bdb1a2bf89c200a9eed6ea4896ea`,
5,000 courses, 25 iterations per second for 30 seconds, and the original 60/25/15
catalog path mix.

- Correctness checks: `1.0`; iterations: `751`; request failures: `0`; dropped
  iterations: `0`; p95: `18.526 ms`; p99: `27.705 ms`.
- Sampling: `10` observations over `32` seconds (minimum required: `10`).
- Application: max CPU `63.95%`, max memory `33.94%` of its container limit, max
  PIDs `37`, max JVM used memory `245,841,384` bytes, max Hikari active `1`, max
  Hikari pending `0`.
- Application cache: `455` hits, `1` miss, `0` failures.
- PostgreSQL: max CPU `8.97%`, max memory `1.19%`; `7` final connections, `464`
  commits, `0` rollbacks, `510` block reads, `177,008` block hits, `0` temporary
  files/bytes, and `0` deadlocks.
- Redis: max CPU `1.26%`, max memory `0.05%`; `455` keyspace hits, `1` miss, `0`
  evictions, and peak used memory `1,172,896` bytes.
- End-to-end diagnostics duration: `58` seconds including startup, Flyway, seed,
  warm-up, JWT security probe/registration, workload, summary validation, and
  cleanup.

These observations show no diagnosed bottleneck at this synthetic rate. They do
not justify lowering/raising any limit or predicting production capacity.

## Local behavior and failure evidence

- Invalid `PERFORMANCE_CAPTURE_RESOURCES` values are refused before resources are
  created.
- The first Windows portability failure exposed a `/dev/null` argument mismatch;
  the security probe now captures its short response in memory with no temp output.
- Insufficient sample attempts failed at 4 and 7 observations. Docker stats are now
  batched across all containers and sampled continuously; the minimum was not
  lowered.
- PostgreSQL's valid JSON whitespace initially failed a strict text matcher. The
  validator now permits JSON whitespace while still requiring every named field.
- The final diagnostic run passed image-ID, summary format, numeric, sample-count,
  cache consistency, zero cache-failure, k6, and cleanup checks.
- A separate default-mode run passed 751 iterations with no failures/drops, p95
  `17.913 ms`, p99 `25.950 ms`, did not expose/start diagnostics, removed the old
  resource summary, and left no labeled container/network.

## Commits before full delivery gates

- `58cdf03` — close Phase 8.4a evidence and define the Phase 8.4b1 ADR/checklist.
- `92eed91` — add the fail-closed resource collector.
- `118b173` — integrate protected opt-in resource diagnostics.
- `1b513b2` — add the independent diagnostics CI job and artifact retention.
- `4cd30f3` — bind resource evidence to image identity and reduce token lifetime.
- `18647e7` — prevent stale resource evidence in default mode.
- `4efaef4` — add the operations runbook and close local documentation items.

## Delivery status

Functional and security behavior is locally proven. Full Maven, recovery,
production-image security, feature CI, final evidence commit, no-fast-forward merge,
repeated merge gates, and exact main CI remain pending. Phase 8.4b1 is not complete
until those gates and the non-empty CI artifacts pass for the delivered SHAs.
