# ADR-010 — PostgreSQL recovery baseline

Status: accepted for Phase 8.3a

Date: 2026-09-13

## Context

The production image and its supply chain are now reproducible, but a deployable
artifact is not sufficient for production readiness. PostgreSQL contains the
authoritative identity, catalog, learning, commerce, organization, and notification
state. A backup that has never been restored is not evidence of recoverability.

The hosting provider, managed-database product, backup destination, encryption key
manager, retention period, and business-approved RPO/RTO have not been selected.
This slice must therefore establish a portable recovery contract and measurable
drill without inventing production promises or embedding provider credentials.

MinIO object recovery has different consistency and versioning requirements and is
handled by Phase 8.3b. Database and object recovery must both pass before the project
claims end-to-end data recovery readiness.

## Decision

- Use PostgreSQL custom-format logical dumps. They are portable, inspectable with
  `pg_restore`, and allow a fail-fast, single-transaction restore.
- Run backup and restore tooling outside the shell-less application image. The
  runtime image remains minimal and receives no database administration binaries.
- Accept connection details only through standard PostgreSQL environment variables.
  Passwords are never command-line arguments, filenames, metadata, or log output.
- Create dumps through a private temporary file with process `umask 077`, then move
  the completed file atomically and generate a SHA-256 sidecar.
- Record non-secret evidence beside each backup: UTC creation time, source server
  version, database name, dump format, and checksum algorithm.
- Restore only after checksum verification and an explicit confirmation bound to the
  target database name. Refuse a non-empty target by default; a deliberate override
  is reserved for an approved disaster-recovery procedure.
- Execute restore with `--exit-on-error`, `--single-transaction`, `--no-owner`, and
  `--no-privileges`. A failed restore must not leave a partially restored database.
- Prove the workflow in CI with disposable, isolated PostgreSQL source and restore
  instances, representative relational data, schema/data assertions, and cleanup.
- Treat drill duration as a measurement. Production RPO, RTO, retention, encryption,
  off-site replication, access policy, and alerting require deployment decisions and
  must not be inferred from a local or CI result.

## Recovery contract

Required backup inputs:

- `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD`;
- `BACKUP_OUTPUT_DIR`, which must already exist and must not be inside the repository.

Required restore inputs:

- the same PostgreSQL connection variables, pointed at the recovery target;
- `BACKUP_FILE` and its adjacent `<backup>.sha256` sidecar;
- `RESTORE_CONFIRMATION=restore:<target-database>`.

The scripts must reject missing values, unreadable artifacts, checksum mismatch,
unreachable databases, incompatible client/server versions, and non-empty restore
targets. They must not create roles, databases, retention rules, schedules, or cloud
resources.

## Consequences

Operators and CI gain one deterministic database backup/restore path that can later
be wrapped by a managed-database scheduler or runbook automation. The application
module boundaries and HTTP/database contracts remain unchanged.

This slice does not yet protect MinIO objects, encrypt or upload backups, define a
retention schedule, or establish a production RPO/RTO. Those gaps remain explicit
instead of being hidden by a successful `pg_dump` command.
