# Phase 8.3 — Data recovery readiness

## Goal

Prove that authoritative data can be restored into an isolated environment using
portable, fail-closed procedures. Recovery evidence, not backup creation alone, is
the completion criterion.

## Previous delivery evidence

Phase 8.2 feature run `34705802749` passed both CI jobs at
`7b410eb9d94422d5e97b5c37a37de071bce3e779`. Merge
`1b70f2a66960fa8a4b996f71259f3319f051a3d2` then passed 199 Maven tests,
the coverage and architecture gates, application/image SBOM validation, the
fixed HIGH/CRITICAL vulnerability gate, secret scans, 12 Flyway migrations, and a
hardened runtime smoke test. Main run `34747663121` passed `verify` and
`container-image` for that exact merge SHA.

## Controlled slices

### Phase 8.3a — PostgreSQL logical recovery

- [x] Reconfirm a clean synchronized main after exact Phase 8.2 main CI success.
- [x] Inspect the current runtime, CI, local infrastructure, and deferred decisions.
- [x] Record the portable PostgreSQL recovery contract before implementation.
- [x] Add fail-closed backup and restore scripts without adding database tools to the
  application image.
- [x] Add an isolated automated drill with representative relational data and
  schema/data integrity assertions.
- [x] Document operator prerequisites, secure artifact handling, failure modes, and
  measured versus promised RPO/RTO.
- [x] Run script checks, negative paths, the isolated recovery drill, full Maven
  verification, and repository/security audits.
- [x] Commit cohesive changes, push the feature, wait for exact feature CI, merge
  no-fast-forward, repeat local gates, push main, and wait for exact main CI.

### Phase 8.3b — MinIO object recovery

- [x] Close Phase 8.3a only after final feature CI, no-fast-forward merge, repeated
  local gates, and exact main CI success.
- [x] Inspect the application object-key/metadata contract and verify the selected
  MinIO Client mirror, checksum, diff, and metadata behavior before implementation.
- [x] Decide a portable current-object inventory and checksum manifest while keeping
  version history and provider retention claims explicit.
- [x] Add current-object snapshot/restore procedures without copying MinIO's live
  filesystem, exposing credentials, or pretending version history is covered.
- [x] Prove restore into an isolated MinIO instance, including object bytes,
  metadata, and missing/extra-object detection.
- [x] Define application/database/object consistency limits and the order of a full
  recovery exercise.
- [x] Document credential handling, least privilege, backup/restore/verify steps,
  failure handling, and limits that remain provider-specific.
- [x] Pass Bash syntax, positive and negative recovery paths, full Maven verification,
  application SBOM validation, repository/security audits, and Docker cleanup checks.
- [x] Push the complete feature, require exact four-job feature CI success, merge
  no-fast-forward, repeat local gates, push main, and require exact main CI success.
- [ ] Bind bucket versioning, object lock, retention, replication, and deletion
  recovery to the selected production object-store provider in Phase 8.3c.

### Phase 8.3c — Operational policy integration

- [ ] Bind schedules, encrypted off-site storage, retention, access policy, and
  alert routing to the selected production environment.
- [ ] Obtain business-approved RPO/RTO targets and compare them with measured drills.
- [ ] Run and record a complete database-plus-object recovery exercise.

## Guardrails

- Never target a live production database during an automated drill.
- Never log, archive, commit, or pass credentials as command-line arguments.
- Never restore an artifact before verifying its checksum.
- Never call a backup successful solely because the dump command exited zero.
- Never claim MinIO, scheduling, retention, encryption, or RPO/RTO completion from
  the PostgreSQL-only slice.
- Keep application APIs, migrations, business modules, and persistence behavior
  unchanged in Phase 8.3a.

## Still blocked on external decisions

- payment gateway/account/sandbox and verified callback contract;
- invitation delivery provider and seat billing/capacity semantics;
- production hosting, registry/signing, encrypted backup destination, retention,
  business RPO/RTO, SLOs, capacity targets, and alert routing.

## Phase 8.3a delivery evidence

Final feature run `34836352979` passed `verify`, `container-image`, and
`postgres-recovery` at `a789d181e127af3cec0ba70e16d44fb7db544872`. Merge
`b555fc510176795ca482774411c01a200c27c907` then passed the recovery drill,
all 199 Maven tests, 12 migrations, architecture and coverage gates, application and
image SBOM validation, image metadata assertions, vulnerability policy, and secret
scans locally. Main run `34838327062` passed all three jobs for that exact merge SHA.

## Phase 8.3b delivery evidence

Feature run `34842957143` passed all four jobs at implementation/report commit
`b8f12b7cd17a26cbd915df59d07eefc0933af429`. Final feature run `34843900103`
passed `verify`, `container-image`, `postgres-recovery`, and `minio-recovery` at
evidence commit `4d84f28a78cf00d828399f9ea096010d7a6cdcb6`.

No-fast-forward merge `039e1a07f043019b0a1687b2351efaf1b55a54cf` then passed the
hardened MinIO drill, PostgreSQL regression drill, all 199 Maven tests, 12 Flyway
migrations, Spring Modulith, ArchUnit, JaCoCo, application/image SBOM validation,
runtime metadata assertions, source/image secret scans, and the fixed
HIGH/CRITICAL vulnerability policy locally. Main run `34944318649` passed all four
jobs for that exact merge SHA.
