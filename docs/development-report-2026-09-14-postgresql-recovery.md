# Development report — PostgreSQL recovery readiness

Date: 2026-09-14

Branch: `feature/backup-restore-readiness`

## Outcome

Phase 8.3a establishes a provider-neutral PostgreSQL logical backup and restore
contract. Backup creation is fail-closed and private; restore requires a verified
checksum, compatible version metadata, an explicit target-bound confirmation, and
an empty target. An isolated Docker drill proves both rejection paths and relational
integrity after restore.

This slice does not change application source, HTTP contracts, Flyway migrations,
business module dependencies, or database behavior. It does not claim MinIO recovery,
production scheduling/retention/encryption, or a business-approved RPO/RTO.

## Changed files

- `.github/workflows/ci.yml` — adds the gated `postgres-recovery` CI job after full
  Maven verification.
- `README.md` — links the database recovery contract.
- `docs/architecture/ADR-010-postgresql-recovery-baseline.md` — records decisions,
  boundaries, and the recovery contract before implementation.
- `docs/architecture/phase-8.3-data-recovery-plan.md` — tracks PostgreSQL, MinIO, and
  production-policy recovery slices separately.
- `docs/operations/postgresql-recovery.md` — operator prerequisites, backup, restore,
  validation, failure handling, and remaining environment decisions.
- `scripts/backup-postgres.sh` — creates a custom-format dump, SHA-256 sidecar, and
  non-secret version metadata with private file permissions.
- `scripts/restore-postgres.sh` — verifies confirmation, artifact integrity, version
  compatibility, and target emptiness before a single-transaction restore.
- `scripts/test-postgres-recovery.sh` — disposable source/target recovery drill with
  positive, negative, integrity, and cleanup assertions.
- `docs/development-report-2026-09-12-container-supply-chain-security.md` — replaces
  stale pending wording with exact Phase 8.2 feature/merge/main CI evidence.

## Architecture and maintainability

- Administrative tooling remains outside the Distroless application image and does
  not add a Spring bean, interface, utility, constant, module dependency, or runtime
  library.
- Backup and restore each have one clear operational responsibility. The drill owns
  orchestration and test data, so production scripts contain no Docker/test concerns.
- Standard PostgreSQL connection variables preserve portability across self-hosted
  and managed databases.
- Source/target version evidence prevents accidental downgrade recovery. The target
  non-empty guard prevents a routine drill from silently overwriting user objects.
- Database and object-storage recovery remain separate controlled slices because
  their consistency, inventory, and versioning rules differ.

## Security

- Required values fail closed; connection attempts have a bounded default timeout.
- Passwords are read from environment/secret injection and never printed, stored in
  artifacts, filenames, metadata, or Docker command arguments.
- Backup output uses `umask 077`; the drill verifies mode `600` for dump, checksum,
  and metadata.
- Restore rejects missing confirmation, missing/invalid sidecars, checksum mismatch,
  invalid archive format, an older target major, and a non-empty target by default.
- `pg_restore` uses `--exit-on-error` and `--single-transaction`, plus portable
  `--no-owner` and `--no-privileges` semantics.
- CI recovery tool containers drop all capabilities, forbid privilege escalation,
  run as the unprivileged `postgres` user, and use a digest-pinned image.
- Raw database dumps are never uploaded as CI artifacts.

## Performance and operations

- PostgreSQL custom format supports later selective/parallel operational workflows
  without coupling the application image to backup tooling.
- Backup publication is atomic at the dump-file boundary; incomplete `pg_dump`
  output remains under a private temporary name and is removed by the exit trap.
- The local representative drill completed in 18 seconds after the final credential
  hardening. This is a development measurement, not a production RTO.
- Production RPO/RTO, retention, encryption, off-site replication, schedule, capacity,
  and alert routing remain explicit deployment/business decisions.

## Verification evidence before feature push

- Bash syntax check passed for all three recovery scripts.
- The final isolated recovery drill passed with disposable PostgreSQL 17 source and
  restore instances on tmpfs.
- Negative paths passed: missing target confirmation, non-empty target, and tampered
  dump checksum were all refused before restore.
- Positive restore passed: 2 courses, 3 enrollments, 2 active-view rows, one foreign
  key, and the identity sequence value were preserved.
- Drill cleanup left no matching container, Docker volume, or network.
- Full `mvn clean verify` passed all 199 tests with 0 failures, 0 errors, and 0 skips;
  all 12 Flyway migrations, Spring Modulith, ArchUnit, and the JaCoCo 70% gate passed.
- Application SBOM validation passed as CycloneDX 1.6 with 138 components.
- Repository diff/check, executable mode, generated-artifact, and credential-pattern
  audits passed. The only password assignment in the drill is a generated disposable
  value; production scripts contain no credential value.
- Feature CI run `34835933891` passed `verify`, `container-image`, and
  `postgres-recovery` on commit `3f2424638f5717db95bedbdc64aeb1a224a58afb`.

## Commits before delivery evidence

- `c6238d2` — define the PostgreSQL recovery ADR and Phase 8.3 checklist.
- `b4f2e88` — add guarded PostgreSQL backup and restore scripts.
- `e915655` — add the isolated recovery drill and CI gate.
- `527b2b0` — add the operator runbook and close Phase 8.2 delivery evidence.
- `e92d78d` — keep the disposable drill credential out of Docker arguments.

## Pending delivery gates

Require exact CI success for the final report commit, merge no-fast-forward, repeat
local gates on the merge commit, push `main`, and require exact main CI success.
Phase 8.3b MinIO recovery starts only after this sequence completes.
