# ADR-011 — MinIO current-object recovery baseline

Status: accepted for Phase 8.3b

Date: 2026-09-14

## Context

PostgreSQL stores lesson-media object keys, content types, sizes, and upload ETags,
while the media bytes live in a private MinIO/S3 bucket. Database recovery alone can
therefore recreate references to missing objects. Copying MinIO's live filesystem is
not a supported portable backup and can capture inconsistent internal state.

The production object-store provider, off-site backup account, retention policy,
object-lock mode, encryption key manager, and bucket-versioning policy are not yet
selected. The current application writes UUID-based keys under
`courses/<course-id>/lessons/<lesson-id>/<object-id>` and depends on current object
bytes and content type. It does not expose object versions, tags, or custom metadata
as a business contract.

This slice must prove recovery of the current object set without claiming version
history, point-in-time deletion recovery, or database/object atomicity that the
platform does not yet provide.

## Decision

- Run recovery tooling outside the application image with MinIO Client (`mc`). CI
  uses a readable release pinned to an immutable multi-platform digest.
- Connect through `MC_HOST_source`, `MC_HOST_backup`, and `MC_HOST_restore`
  environment variables supplied by the secret store. Never create persistent alias
  files or place credentials in commands, logs, manifests, or repository files.
- Require operators to quiesce media writes and deletes before capturing the object
  snapshot and the corresponding PostgreSQL backup. The script requires an explicit
  snapshot-bound confirmation but cannot manufacture cross-system atomicity.
- Write each backup to a new immutable-intent prefix:
  `snapshots/<snapshot-id>/objects/`. Refuse an existing prefix instead of updating
  or deleting prior backup data.
- Copy current objects with `mc mirror --preserve --checksum SHA256 --retry` to retain
  the application-relevant metadata and attach a server-verifiable SHA-256 checksum.
- Fail when `mc diff` reports a name, size, or modification difference after copy.
  Source mutation during capture is a failed snapshot, not a warning.
- Generate a sorted inventory from the backup copy containing relative object key,
  size, SHA-256, and content type. Upload the inventory and a non-secret snapshot
  metadata marker only after object copy and verification complete. The completion
  marker is the publication boundary.
- Restore only from a completed snapshot into an existing empty target bucket after
  exact target-bound confirmation. Recreate an inventory from the restored bucket
  and require it to match the recorded inventory before success.
- Prove the workflow with isolated source, backup, and restore MinIO servers. The
  drill must check multiple UUID-shaped nested keys, distinct bytes and content
  types, a custom metadata preservation sample, missing/extra-object detection,
  target non-empty refusal, inventory tampering, and cleanup.
- Keep the HTTP API, PostgreSQL schema, application modules, and MinIO adapter
  unchanged. Recovery is an operational boundary, not a new business interface.

## Snapshot contract

Backup inputs:

- `MC_HOST_source` and `MC_HOST_backup`;
- `MINIO_SOURCE_BUCKET`, `MINIO_BACKUP_BUCKET`, and `MINIO_SNAPSHOT_ID`;
- `MINIO_BACKUP_CONFIRMATION=snapshot:<source-bucket>:<snapshot-id>`.

Restore inputs:

- `MC_HOST_backup` and `MC_HOST_restore`;
- `MINIO_BACKUP_BUCKET`, `MINIO_TARGET_BUCKET`, and `MINIO_SNAPSHOT_ID`;
- `MINIO_RESTORE_CONFIRMATION=restore:<target-bucket>:<snapshot-id>`.

Buckets and access policies are provisioned outside these scripts. Source access is
read-only; backup access is limited to its destination prefix; restore access is
limited to the isolated target. TLS certificate validation is mandatory in
production.

## Consequences

The project gains deterministic evidence that the current lesson-media object set,
bytes, size, content type, and required key structure survive a backup/restore path.
The application image stays minimal and cloud-neutral.

This baseline does not copy prior object versions, delete markers, bucket policies,
replication state, retention locks, encryption keys, or provider audit configuration.
It also does not make independently captured PostgreSQL and MinIO snapshots atomic.
Those limitations must remain visible until Phase 8.3c binds provider capabilities,
backup ordering, retention, and business-approved RPO/RTO to a real environment.
