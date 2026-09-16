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
`sha256:e416978581222819c80822a42b95f294b5989d2f050c9c88eaf0c80cdebbc378`,
5,000 courses, 25 iterations per second for 30 seconds, and the original 60/25/15
catalog path mix.

- Correctness checks: `1.0`; iterations: `751`; request failures: `0`; dropped
  iterations: `0`; p95: `16.243 ms`; p99: `27.220 ms`.
- Sampling: `10` observations over `31` seconds (minimum required: `10`).
- Application: max CPU `56.96%`, max memory `34.42%` of its container limit, max
  PIDs `36`, max JVM used memory `249,542,504` bytes, max Hikari active `0`, max
  Hikari pending `0`.
- Application cache: `455` hits, `1` miss, `0` failures.
- PostgreSQL: max CPU `14.63%`, max memory `1.21%`; `7` final connections, `496`
  commits, `0` rollbacks, `510` block reads, `177,843` block hits, `0` temporary
  files/bytes, and `0` deadlocks.
- Redis: max CPU `0.99%`, max memory `0.05%`; `455` keyspace hits, `1` miss, `0`
  evictions, and peak used memory `1,172,928` bytes.
- End-to-end diagnostics duration: `62` seconds including startup, Flyway, seed,
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
- A separate default-mode run passed 750 iterations with no failures/drops, p95
  `16.475 ms`, p99 `25.179 ms`, did not expose/start diagnostics, removed the old
  resource summary, and left no labeled container/network.

## Full pre-push quality gates

- `mvn --batch-mode --no-transfer-progress clean verify` passed `199` tests with no
  failures/errors/skips, all JaCoCo checks, all module/architecture checks, all 12
  Flyway migrations, and generated an application SBOM with `138` components.
- The production image passed its non-root UID/GID, `prod` profile, immutable
  entrypoint, and shell-less runtime contract.
- The image SBOM contains `153` components. The complete vulnerability inventory
  contains `60` findings for triage, while the blocking scan found `0` fixable
  HIGH/CRITICAL vulnerabilities; source and image secret scans were clean.
- PostgreSQL recovery passed in `17` seconds and MinIO current-object recovery
  passed in `37` seconds, including their fail-closed refusal paths.
- All shell scripts passed syntax validation. Both performance modes passed on the
  exact production image, their cache/Redis counters agreed, and cleanup left no
  labeled container or network.

## Initial feature CI evidence

GitHub Actions run `34986408682` passed all six jobs for exact pre-evidence SHA
`45e20fff23a1daac8fd4b4f812371425c78bbfec`: `verify`, `container-image`,
`postgres-recovery`, `minio-recovery`, `performance-baseline`, and the new
`performance-diagnostics` job.

The run retained four non-empty, commit-bound artifacts:

- application SBOM: artifact `10404430027`, `75,091` bytes;
- image security evidence: artifact `10404195956`, `67,772` bytes;
- catalog performance summary: artifact `10403782643`, `469` bytes;
- correlated catalog resource summaries: artifact `10402834491`, `1,144` bytes.

This is initial feature evidence only. The documentation commit that records it
must itself pass the same six-job workflow before the branch is eligible to merge.

## Commits before full delivery gates

- `58cdf03` — close Phase 8.4a evidence and define the Phase 8.4b1 ADR/checklist.
- `92eed91` — add the fail-closed resource collector.
- `118b173` — integrate protected opt-in resource diagnostics.
- `1b513b2` — add the independent diagnostics CI job and artifact retention.
- `4cd30f3` — bind resource evidence to image identity and reduce token lifetime.
- `18647e7` — prevent stale resource evidence in default mode.
- `4efaef4` — add the operations runbook and close local documentation items.
- `d186002` — record final local behavior and diagnostics evidence.
- `45e20ff` — record the full pre-push quality gates.

## Delivery status

Phase 8.4b1 is delivered. Final feature CI run `34987304035` passed all six jobs for
exact SHA `f785cb1a60d21599303ee37678a2172a81ac6a0f`. No-fast-forward merge
`09d01b0b3b693e332dcf7f1dc2e3ddc4508e0b04` passed the full repeated local gates,
and main CI run `34989730512` passed all six jobs for that exact merge SHA.

The main run retained four non-empty artifacts: application SBOM `10404603354`
(`75,092` bytes), image security `10405281440` (`67,762` bytes), catalog performance
`10405845460` (`463` bytes), and catalog resource diagnostics `10405885413` (`1,154`
bytes). Local and remote `main` both resolved to the merge SHA with a clean working
tree after delivery.
