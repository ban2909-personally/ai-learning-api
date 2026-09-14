#!/usr/bin/env bash
set -euo pipefail

required_variables=(PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD BACKUP_FILE RESTORE_CONFIRMATION)
required_commands=(awk pg_restore psql sed sha256sum)

fail() {
  printf 'PostgreSQL restore refused: %s\n' "$1" >&2
  exit 1
}

export PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-10}"

for variable_name in "${required_variables[@]}"; do
  [[ -n "${!variable_name:-}" ]] || fail "required environment variable $variable_name is missing"
done

for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command $command_name is unavailable"
done

[[ "$PGPORT" =~ ^[0-9]+$ ]] || fail 'PGPORT must be numeric'
[[ "$RESTORE_CONFIRMATION" == "restore:$PGDATABASE" ]] \
  || fail "RESTORE_CONFIRMATION must equal restore:$PGDATABASE"
[[ -f "$BACKUP_FILE" && ! -L "$BACKUP_FILE" ]] || fail 'BACKUP_FILE must be a readable regular file'
[[ -r "$BACKUP_FILE" ]] || fail 'BACKUP_FILE is not readable'

checksum_file="$BACKUP_FILE.sha256"
[[ -f "$checksum_file" && ! -L "$checksum_file" ]] || fail 'checksum sidecar is missing or invalid'
metadata_file="$BACKUP_FILE.metadata"
[[ -f "$metadata_file" && ! -L "$metadata_file" ]] || fail 'metadata sidecar is missing or invalid'

expected_checksum="$(awk 'NR == 1 { print $1 }' "$checksum_file")"
[[ "$expected_checksum" =~ ^[0-9a-fA-F]{64}$ ]] || fail 'checksum sidecar does not contain a SHA-256 digest'
actual_checksum="$(sha256sum "$BACKUP_FILE" | awk '{print $1}')"
[[ "$actual_checksum" == "$expected_checksum" ]] || fail 'backup checksum does not match its sidecar'

pg_restore --list "$BACKUP_FILE" >/dev/null 2>&1 || fail 'backup is not a valid PostgreSQL custom-format archive'

server_version_num="$(
  psql --no-psqlrc --quiet --tuples-only --no-align \
    --command='SHOW server_version_num' 2>/dev/null
)" || fail 'target database is unreachable or credentials are invalid'
[[ "$server_version_num" =~ ^[0-9]+$ ]] || fail 'target returned an invalid server version'

client_version="$(pg_restore --version | sed -E 's/.* ([0-9]+)(\.[0-9]+)?.*/\1/')"
[[ "$client_version" =~ ^[0-9]+$ ]] || fail 'could not determine pg_restore major version'
target_major=$((server_version_num / 10000))
((client_version >= target_major)) || fail "pg_restore major $client_version is older than target major $target_major"

source_version_num="$(awk -F= '$1 == "server_version_num" { print $2 }' "$metadata_file")"
[[ "$source_version_num" =~ ^[0-9]+$ ]] || fail 'metadata does not contain a valid source server version'
source_major=$((source_version_num / 10000))
((target_major >= source_major)) \
  || fail "target major $target_major is older than source major $source_major"

user_object_count="$(
  psql --no-psqlrc --quiet --tuples-only --no-align --command="
    SELECT count(*)
    FROM pg_class AS relation
    JOIN pg_namespace AS namespace ON namespace.oid = relation.relnamespace
    WHERE namespace.nspname NOT IN ('pg_catalog', 'information_schema')
      AND namespace.nspname !~ '^pg_toast'
      AND relation.relkind IN ('r', 'p', 'v', 'm', 'S', 'f');
  " 2>/dev/null
)" || fail 'could not inspect target database contents'
[[ "$user_object_count" =~ ^[0-9]+$ ]] || fail 'target returned an invalid object count'

if ((user_object_count > 0)) && [[ "${RESTORE_ALLOW_NONEMPTY:-false}" != 'true' ]]; then
  fail "target database contains $user_object_count user objects; use an isolated empty target"
fi

restore_arguments=(
  --exit-on-error
  --single-transaction
  --clean
  --if-exists
  --no-owner
  --no-privileges
  --dbname="$PGDATABASE"
  "$BACKUP_FILE"
)

pg_restore "${restore_arguments[@]}" || fail 'pg_restore did not complete successfully'
printf 'PostgreSQL restore completed for target database: %s\n' "$PGDATABASE"
