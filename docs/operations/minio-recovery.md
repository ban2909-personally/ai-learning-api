# MinIO current-object recovery operations

## Purpose and scope

This runbook implements the portable Phase 8.3b current-object backup and restore
contract for private lesson media. It preserves the current object keys, bytes,
sizes, content types, and object metadata needed by the application. It does not
copy object-version history, delete markers, bucket policy, object-lock state,
replication configuration, or encryption keys.

Use Bash on Linux, WSL, or Git Bash. Run the scripts from a controlled operations
host with MinIO Client (`mc`) available. CI uses the Chainguard server and client
images pinned to immutable multi-platform digests:

```text
cgr.dev/chainguard/minio:latest@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1
cgr.dev/chainguard/minio-client:latest@sha256:b8b144ab34694ecea25aa352c4be9de4c26ee2a02701521dce02ee5593c57338
```

The readable `latest` labels are not floating inputs because Docker resolves the
explicit digests. Both containers run without root privileges in the drill. These
portable images are test tooling only; the production object-store provider and
its supported release policy remain Phase 8.3c decisions.

## Credentials and access

Inject complete `MC_HOST_source`, `MC_HOST_backup`, and `MC_HOST_restore` values
through the approved secret store. Each value follows MinIO Client's host-variable
format and contains credentials, so do not type it into shell history, print it,
store it in a checked-in `.env` file, or pass its value as a Docker argument.

Use separate least-privilege identities:

- source: list the source bucket and read its current objects and metadata;
- backup: list the backup bucket and write only the selected snapshot prefix;
- restore: list the isolated target bucket and write its objects;
- verification: read the selected backup prefix and restored target.

Production endpoints must use HTTPS with valid certificate verification. Bucket
creation, versioning, retention, encryption, and access policy are platform
provisioning responsibilities and are deliberately not changed by these scripts.

## Before every snapshot

- Quiesce lesson-media uploads, replacements, and deletes, and keep them stopped
  until both the PostgreSQL backup and object snapshot finish.
- Record one incident/change identifier and one snapshot identifier that pair the
  database and object artifacts in the environment's operational system.
- Confirm the source and backup accounts, buckets, endpoint TLS, available capacity,
  off-site destination health, and responsible validation owner.
- Choose a new snapshot identifier. Never reuse or update an existing snapshot
  prefix; an incomplete prefix is retained for investigation or removed only under
  an independently approved cleanup procedure.

The repository scripts cannot make independently captured PostgreSQL and MinIO
artifacts atomic. Quiescing writes and recording the artifact pair are mandatory
until provider-native coordination is selected in Phase 8.3c.

## Create a current-object snapshot

After the secret store has injected `MC_HOST_source` and `MC_HOST_backup`, set only
the non-secret controls:

```bash
export MINIO_SOURCE_BUCKET='lesson-media'
export MINIO_BACKUP_BUCKET='ai-learning-recovery'
export MINIO_SNAPSHOT_ID='incident-1234-20260914T120000Z'
export MINIO_BACKUP_CONFIRMATION="snapshot:$MINIO_SOURCE_BUCKET:$MINIO_SNAPSHOT_ID"
bash scripts/backup-minio.sh
unset MINIO_BACKUP_CONFIRMATION
```

The script writes a new prefix with this contract:

```text
snapshots/<snapshot-id>/
  objects/courses/<course-uuid>/lessons/<lesson-uuid>/<object-uuid>
  inventory.tsv
  snapshot.metadata
```

Objects are mirrored with metadata preservation and SHA-256 calculation. The sorted
inventory records relative key, size, SHA-256, and content type. The
`snapshot.metadata` completion marker is uploaded last; a prefix without a valid
completion marker is not restorable. A post-copy difference is a failed snapshot,
even when the object copy command itself exited successfully.

## Restore into an isolated target

Provision an existing empty bucket in a non-production recovery environment. Keep
the application, Kafka consumers, schedulers, and all writers disconnected. After
the secret store injects `MC_HOST_backup` and `MC_HOST_restore`, run:

```bash
export MINIO_BACKUP_BUCKET='ai-learning-recovery'
export MINIO_TARGET_BUCKET='lesson-media-recovery'
export MINIO_SNAPSHOT_ID='incident-1234-20260914T120000Z'
export MINIO_RESTORE_CONFIRMATION="restore:$MINIO_TARGET_BUCKET:$MINIO_SNAPSHOT_ID"
bash scripts/restore-minio.sh
unset MINIO_RESTORE_CONFIRMATION
```

Restore refuses a missing or mismatched confirmation, invalid identifiers, an
unreachable bucket, a non-empty target, an incomplete snapshot, a modified inventory,
or inconsistent inventory count before copying objects. Success requires a second,
read-only inventory and difference verification after the copy.

## Verify an existing restored bucket

The verifier does not require the destructive-action confirmation and does not write
to object storage:

```bash
export MINIO_BACKUP_BUCKET='ai-learning-recovery'
export MINIO_TARGET_BUCKET='lesson-media-recovery'
export MINIO_SNAPSHOT_ID='incident-1234-20260914T120000Z'
bash scripts/verify-minio-restore.sh
```

It fails on a missing, extra, or changed object and on any inventory key, size,
SHA-256, or content-type difference.

## Full database-plus-object recovery order

1. Keep all application and asynchronous writers stopped.
2. Select the explicitly paired PostgreSQL dump and MinIO snapshot identifier.
3. Verify backup metadata and checksums before mutating either isolated target.
4. Restore PostgreSQL into an empty recovery database using the PostgreSQL runbook.
5. Restore MinIO into an empty recovery bucket using this runbook.
6. Compare database media keys with the object inventory; every referenced current
   object must exist, and unexpected objects must be investigated.
7. Start the application against only the isolated targets. Keep event publication
   disabled and validate health plus read-only critical media journeys.
8. Record durations, artifact sizes, counts, failures, and operator actions before
   destroying or reclassifying the recovery environment.

This order is a controlled exercise, not a production RPO/RTO promise. Promotion or
traffic cutover needs an environment-specific disaster-recovery plan and approval.

## Repository recovery drill

Run the same isolated positive and negative paths enforced by CI:

```bash
bash -n scripts/create-minio-inventory.sh scripts/backup-minio.sh scripts/restore-minio.sh scripts/verify-minio-restore.sh scripts/test-minio-recovery.sh
bash scripts/test-minio-recovery.sh
```

The drill creates unique source, backup, and restore MinIO servers on tmpfs. Servers
and clients run non-root, drop all capabilities, and disallow privilege escalation.
The cleanup trap removes the containers and network. It proves byte equality,
content-type and custom-metadata preservation, exact inventory verification, and
refusal of reused prefixes, missing confirmation, non-empty targets, modified
inventory, inconsistent counts, and missing or extra restored objects.

## Failure handling

- Treat an interrupted snapshot without a valid completion marker as incomplete.
  Do not repair it in place or publish the marker manually.
- If the source changes during capture, keep writes quiesced, choose a new snapshot
  identifier, and repeat both paired backups as the incident plan requires.
- If restore or verification fails, keep the target isolated. Investigate endpoint,
  TLS, permission, capacity, metadata, and artifact-integrity evidence before
  recreating an empty target and retrying.
- Never delete the last verified snapshot while handling a failure. Never upload
  credentials, media objects, or secret-bearing client configuration as CI artifacts.
- A successful object restore is not a full recovery until database references and
  application read paths pass against the paired isolated targets.

## Remaining production decisions

Phase 8.3c must bind provider-specific versioning, deletion recovery, object lock,
encrypted off-site replication, lifecycle retention, key management, schedules,
access review, audit logs, monitoring, alert routing, capacity, legal requirements,
and business-approved RPO/RTO. This baseline must not be used to claim those controls
already exist.
