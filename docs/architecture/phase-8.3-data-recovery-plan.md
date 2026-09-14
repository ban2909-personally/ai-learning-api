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
- [ ] Commit cohesive changes, push the feature, wait for exact feature CI, merge
  no-fast-forward, repeat local gates, push main, and wait for exact main CI.

### Phase 8.3b — MinIO object recovery

- [ ] Decide a portable object inventory and checksum manifest.
- [ ] Add bucket versioning/object recovery procedures without copying MinIO's live
  filesystem or exposing credentials.
- [ ] Prove restore into an isolated MinIO instance, including object bytes,
  metadata, and missing/extra-object detection.
- [ ] Define application/database/object consistency limits and the order of a full
  recovery exercise.

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
