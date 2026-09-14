# PostgreSQL recovery operations

## Purpose and scope

This runbook implements the portable Phase 8.3a logical backup/restore contract.
It proves database recovery without choosing a cloud provider or changing the
shell-less application image. It does not cover MinIO objects, scheduled backups,
retention, encryption keys, or a production RPO/RTO.

Use Bash on Linux, WSL, or Git Bash. PostgreSQL client tools must be the same major
version as the source server or newer. A restore target must run the source major
version or newer. CI uses this immutable PostgreSQL 17 tool image:

```text
postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0
```

## Before every backup

- Select an encrypted output directory outside the source repository and application
  container. Only the backup operator may read it.
- Obtain a database account that can read every application schema and sequence but
  cannot administer the host or unrelated databases.
- Inject the password through the approved secret store. Do not put it in a command,
  checked-in `.env` file, shell history, CI log, or backup filename.
- Confirm available space, PostgreSQL reachability, TLS verification, backup
  destination health, and the intended source database.
- Record the incident/change identifier and the planned recovery validation owner in
  the environment's operational system; this repository does not invent one.

## Create a logical backup

Set the standard PostgreSQL variables and an existing private destination:

```bash
export PGHOST='database.internal.example'
export PGPORT='5432'
export PGDATABASE='ai_learning'
export PGUSER='backup_operator'
export PGSSLMODE='verify-full'
export BACKUP_OUTPUT_DIR='/secure-backups/ai-learning'
read -r -s -p 'PostgreSQL password: ' PGPASSWORD
export PGPASSWORD
bash scripts/backup-postgres.sh
unset PGPASSWORD
```

The script creates three mode-`600` files:

- `<database>-<UTC timestamp>.dump`: PostgreSQL custom-format archive;
- `<archive>.sha256`: integrity sidecar required by restore;
- `<archive>.metadata`: non-secret creation time and version evidence.

The dump first writes to a private temporary file and is moved to its final name only
after `pg_dump` succeeds. Copy all three files together to the approved encrypted,
access-controlled, off-site destination. Creating these files is not a successful
recovery test.

## Restore into an isolated target

Provision an empty target database separately. Keep application instances and event
dispatchers disconnected until the restore and validation steps complete. Use a
target-specific credential with `CONNECT`, `CREATE`, and object ownership privileges.

```bash
export PGHOST='recovery-database.internal.example'
export PGPORT='5432'
export PGDATABASE='ai_learning_restore'
export PGUSER='restore_operator'
export PGSSLMODE='verify-full'
export BACKUP_FILE='/secure-backups/ai-learning/ai_learning-YYYYMMDDTHHMMSSZ.dump'
export RESTORE_CONFIRMATION="restore:$PGDATABASE"
read -r -s -p 'PostgreSQL password: ' PGPASSWORD
export PGPASSWORD
bash scripts/restore-postgres.sh
unset PGPASSWORD RESTORE_CONFIRMATION
```

Restore fails before mutation when confirmation is absent, the checksum or metadata
sidecar is missing, the checksum differs, the archive is invalid, the target is an
older PostgreSQL major, or the target already contains user objects. Do not use
`RESTORE_ALLOW_NONEMPTY=true` during routine drills. That override is only for a
separately reviewed disaster-recovery plan with a captured pre-restore state and a
rollback owner.

## Validate recovery

Validation is environment-specific and must include, at minimum:

1. PostgreSQL restore exits successfully in one transaction.
2. Flyway history contains the expected successful versions and no failed row.
3. Counts and sampled immutable identifiers match the source-side backup evidence.
4. Foreign keys, unique constraints, indexes, views, and sequences exist and behave
   correctly.
5. The application starts against the isolated database, readiness becomes `UP`, and
   read-only critical journeys succeed before any writer or event dispatcher starts.
6. Duration, artifact size, failures, and operator actions are recorded. Measured
   times are evidence, not a production RTO promise.

Destroy or reclassify the restored environment after the drill according to the
selected data-handling policy. A restored copy contains production-sensitive data
even when it is not serving production traffic.

## Repository recovery drill

Run the same isolated positive and negative paths enforced by CI:

```bash
bash -n scripts/backup-postgres.sh scripts/restore-postgres.sh scripts/test-postgres-recovery.sh
bash scripts/test-postgres-recovery.sh
```

The drill uses unique Docker resources, PostgreSQL data on tmpfs, and a temporary
Docker volume for backup artifacts. Its trap removes both databases, the backup
volume, and the network. It verifies private file modes, target-bound confirmation,
non-empty-target refusal, checksum mismatch refusal, tables, rows, a view, a foreign
key, and an identity sequence.

## Failure handling

- Keep a failed artifact and non-secret logs only when the approved incident process
  requires them; never upload credentials or a raw database dump to CI artifacts.
- If backup fails, confirm client/server compatibility, TLS, permissions, storage,
  and connectivity before retrying. Do not delete the last known restorable backup.
- If restore fails, keep the target offline. Because restore is transactional, fix
  the cause or recreate an empty target and retry from the verified artifact.
- If validation differs from source evidence, the drill failed even when
  `pg_restore` exited zero. Escalate before promotion.

## Remaining production decisions

The deployment owner must still choose scheduling, encrypted off-site storage,
retention/expiry, key management, access reviews, monitoring, alert routing, legal
retention requirements, and business-approved RPO/RTO. Phase 8.3b must add MinIO
object inventory and restore evidence before a full platform recovery claim.
