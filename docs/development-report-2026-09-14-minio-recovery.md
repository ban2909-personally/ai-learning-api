# Development report — MinIO current-object recovery readiness

Date: 2026-09-14

Branch: `feature/minio-object-recovery`

## Outcome

Phase 8.3b establishes a fail-closed, provider-neutral recovery contract for the
current private lesson-media object set. A snapshot is published only after the
object mirror, storage comparison, deterministic inventory, and integrity metadata
complete. Restore accepts only a completed snapshot and an empty, explicitly
confirmed target, then independently verifies the restored inventory.

This slice does not modify application source, `pom.xml`, HTTP contracts, Flyway
migrations, business-module dependencies, or database behavior. It does not claim
object-version history, deletion recovery, provider retention/lock/replication,
database/object atomicity, scheduling, encrypted off-site storage, or production
RPO/RTO.

## Changed files

- `.github/workflows/ci.yml` — adds an isolated `minio-recovery` job after full
  Maven verification.
- `README.md` — links the object-recovery operations entry point and its limits.
- `docs/architecture/ADR-011-minio-current-object-recovery.md` — records the
  current-object recovery contract before implementation.
- `docs/architecture/phase-8.3-data-recovery-plan.md` — tracks completed local work
  and keeps feature/main delivery plus provider policy explicit.
- `docs/operations/minio-recovery.md` — defines operator prerequisites, credentials,
  snapshot, restore, verification, full recovery ordering, and failure handling.
- `scripts/create-minio-inventory.sh` — validates application object-key shape and
  emits a deterministic key/size/SHA-256/content-type inventory.
- `scripts/backup-minio.sh` — creates a new immutable-intent snapshot prefix and
  publishes its completion marker last.
- `scripts/restore-minio.sh` — validates the target-bound confirmation, snapshot
  metadata, inventory integrity/count, and empty target before restore.
- `scripts/verify-minio-restore.sh` — read-only restored-object comparison and
  inventory verification.
- `scripts/test-minio-recovery.sh` — disposable three-server positive/negative drill
  with hardened container execution and cleanup.

## Architecture and maintainability

- Recovery tooling remains outside the shell-less application image and adds no
  Spring bean, interface, utility class, shared constant, runtime dependency, or
  cross-module access.
- Backup, restore, inventory generation, verification, and drill orchestration have
  separate responsibilities. Production scripts contain no Docker test fixtures.
- Object storage remains behind the existing catalog output port; API and adapter
  behavior are unchanged.
- The manifest format is explicit and versioned. Sorted rows make evidence stable
  and reviewable without introducing a second application data model.
- Database and object recovery remain separate procedures with a documented paired
  exercise because the platform cannot honestly promise cross-system atomicity yet.

## Security

- Required values fail closed. Bucket and snapshot identifiers are constrained, and
  backup/restore require exact operation-, target-, and snapshot-bound confirmation.
- Production scripts accept MinIO aliases only through secret-injected environment
  variables and create an ephemeral private client configuration under `umask 077`.
- Existing snapshot prefixes and non-empty restore buckets are refused; no script
  contains a delete/overwrite escape hatch.
- Restore verifies the completion state, snapshot id, inventory SHA-256, and object
  count before copying. Post-restore verification detects missing, extra, or changed
  objects and differences in key, size, checksum, or content type.
- The drill passes credential variable names—not values—to Docker. Its generated
  disposable password is not persisted or printed.
- Both MinIO server and client containers run as UID/GID `1000`, drop every Linux
  capability, disallow privilege escalation, use tmpfs for mutable data, and use
  images pinned by immutable digest.

## Performance and operations

- `mc mirror` streams current objects between storage endpoints and avoids routing
  media bytes through the application JVM.
- Checksum calculation, retry, metadata preservation, post-copy diff, and a compact
  sorted inventory favor deterministic recovery over an unverified fast copy.
- Snapshot prefixes are append-only by procedure; failed/incomplete prefixes never
  become restorable because the completion marker is the final publication boundary.
- The final local representative drill completed in 34 seconds for two objects and
  three isolated servers. This development measurement is not a production RTO.
- Production capacity, multipart scale, bandwidth, retention, schedule, monitoring,
  off-site replication, and RPO/RTO remain environment decisions for Phase 8.3c.

## Verification evidence before feature push

- Bash syntax passed for all five MinIO recovery scripts.
- The hardened isolated drill passed with source, backup, and restore MinIO servers
  on tmpfs and two UUID-keyed media objects with distinct bytes/content types.
- Positive checks passed for exact bytes, `video/mp4`, `video/webm`, custom metadata,
  SHA-256 inventory, completion metadata, and final read-only verification.
- Negative checks passed for reused snapshot prefix, missing confirmation, non-empty
  target, tampered inventory, metadata/inventory count mismatch, missing object, and
  extra object.
- Drill cleanup left no container or network carrying the recovery label.
- Full `mvn clean verify` passed all 199 tests with 0 failures, 0 errors, and 0 skips;
  all 12 Flyway migrations, Spring Modulith, ArchUnit, and the JaCoCo gate passed.
- Application SBOM validation passed as CycloneDX 1.6 with 138 components.
- Diff/check, executable modes, generated-artifact scope, and credential-pattern
  audits passed. The only password assignment is a generated disposable drill value.
- Feature CI run `34842957143` passed `verify`, `container-image`,
  `postgres-recovery`, and `minio-recovery` for exact implementation/report commit
  `b8f12b7cd17a26cbd915df59d07eefc0933af429`.

## Commits before delivery evidence

- `d0bd63b` — define the MinIO current-object recovery ADR and migration checklist.
- `4436164` — add guarded snapshot, restore, inventory, and verifier scripts.
- `f787d10` — add the hardened three-server recovery drill.
- `f9d2eb7` — add the independent MinIO recovery CI gate.
- `b7cef71` — add the operations runbook and close the local implementation checklist.

## Delivery evidence

- Final feature CI run `34843900103` passed `verify`, `container-image`,
  `postgres-recovery`, and `minio-recovery` for exact evidence commit
  `4d84f28a78cf00d828399f9ea096010d7a6cdcb6`.
- No-fast-forward merge `039e1a07f043019b0a1687b2351efaf1b55a54cf`
  repeated the MinIO and PostgreSQL drills, full Maven verification, runtime image
  contract, source/image secret scans, application/image SBOM validation, and fixed
  HIGH/CRITICAL vulnerability gate locally before push.
- Main CI run `34944318649` passed the same four jobs for that exact merge SHA.

Phase 8.3b is complete. Phase 8.3c remains blocked on a selected production
environment, provider capabilities, retention/encryption ownership, alert routing,
and business-approved RPO/RTO; no values are inferred by this report.
