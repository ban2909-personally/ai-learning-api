# Development report — Authenticated learning performance profiles

Date: 2026-09-17

Branch: `feature/authenticated-learning-performance`

## Outcome

Phase 8.4b2 adds separate authenticated learning read and write regression profiles
with correlated resource evidence. The implementation changes no Java production
source, dependency, API contract, authorization rule, Flyway migration, index,
cache behavior, pool, thread/heap setting, or module dependency.

## Architecture and maintainability

- ADR-014 fixes the workload, identity, security, evidence, and non-goal boundaries
  before implementation.
- A fixture outside Flyway represents only isolated performance data. The workload
  uses existing registration, enrollment, lesson-player, and progress contracts.
- The existing resource collector was extracted into one validated operational
  adapter with `catalog` and `authenticated-learning` modes. The catalog entry point
  remains a six-line compatibility wrapper, and the delivered catalog schema stays
  unchanged.
- Read and write execute independently with profile-specific summaries and resource
  evidence. No generic Java abstraction or production runtime behavior was added.

## Security and data lifecycle

- Forty students are registered and enrolled through HTTP per profile; each VU uses
  one JWT held only in k6 memory. A separate disposable JWT reads protected metrics.
- Generated passwords are passed to containers by environment-variable name, never
  by value in a command, log, result file, or artifact.
- PostgreSQL and Redis use tmpfs; application/k6 filesystems are read-only; ports are
  loopback/random; capabilities are dropped; privilege escalation is disabled;
  application CPU/memory/PIDs are bounded.
- The harness proves unauthenticated metrics return 401, validates database/outbox
  invariants, and removes exact containers/network plus raw sample files on exit.

## Local behavior evidence

- Invalid profile input exited `1` before creating a container or network.
- The first write attempt exposed an RFC email local-part overflow because the
  profile name appeared twice. The run failed before traffic; run IDs are now short,
  validated to 48 characters, and represented once in the address.
- Final read: `601` iterations, checks `1.0`, failures/drops `0`, p95 `22.252 ms`,
  p99 `27.061 ms`, and `12` resource samples.
- Final write: `301` iterations, checks `1.0`, failures/drops `0`, p95 `33.117 ms`,
  p99 `40.968 ms`, and `13` resource samples.
- Both final summaries matched the exact production image. Each run proved forty
  identities/enrollments, progress/outbox invariants, and zero remaining labeled
  container/network.
- The shared-collector catalog regression passed `751` iterations with zero
  failures/drops, p95 `17.101 ms`, p99 `26.679 ms`, ten samples, unchanged format,
  matching cache/Redis counters, exact image identity, and clean teardown.

## Commits before delivery gates

- `2442cdf` — define ADR-014/checklist and close Phase 8.4b1 delivery evidence.
- `4ad7f23` — add the deterministic fixture and separate k6 profiles.
- `5ad40cc` — extract and validate the shared resource diagnostics collector.
- `4b6b863` — add fail-closed authenticated profile orchestration.

## Delivery status

### Full local pre-push gates

- Maven `clean verify` passed all `199` tests with zero failures, errors, or skips;
  all twelve Flyway migrations, Spring Modulith, ArchUnit, and the JaCoCo gate
  passed. The application SBOM contains `138` components.
- The production image contract passed with user `65532:65532`, the `prod` Spring
  profile, and the distroless Java entrypoint. The image SBOM contains `153`
  components; the scan recorded `60` findings and zero fixable HIGH/CRITICAL
  findings, with secret scans clean.
- PostgreSQL and MinIO recovery drills passed their positive restore checks and
  every confirmation, non-empty-target, checksum, count, and content guard.
- Catalog baseline passed `751` iterations with zero failures/drops, p95
  `18.875 ms`, and p99 `31.342 ms`. Catalog diagnostics repeated `751` iterations
  with zero failures/drops, p95 `18.507 ms`, p99 `31.219 ms`, and ten samples.
- Authenticated read passed `600` iterations with zero failures/drops, p95
  `21.521 ms`, p99 `26.661 ms`, and thirteen samples. Authenticated write passed
  `301` iterations with zero failures/drops, p95 `40.016 ms`, p99 `58.219 ms`,
  and thirteen samples.
- All six compact performance files matched their expected formats and exact
  production image. Cleanup left zero labeled performance containers or networks.

Functional behavior, full local regression, security/isolation, recovery, and
production-image gates are proven. Exact feature CI, final evidence commit,
no-fast-forward merge, repeated merge gates, exact main CI, and remote artifact
verification remain pending.
