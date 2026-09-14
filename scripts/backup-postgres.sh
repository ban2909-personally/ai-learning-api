#!/usr/bin/env bash
set -euo pipefail

required_variables=(PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD BACKUP_OUTPUT_DIR)
required_commands=(awk date mktemp mv pg_dump psql sed sha256sum)
temporary_file=''

fail() {
  printf 'PostgreSQL backup failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [[ -n "$temporary_file" && -f "$temporary_file" ]]; then
    rm -f -- "$temporary_file"
  fi
}

trap cleanup EXIT
umask 077
export PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-10}"

for variable_name in "${required_variables[@]}"; do
  [[ -n "${!variable_name:-}" ]] || fail "required environment variable $variable_name is missing"
done

for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command $command_name is unavailable"
done

[[ "$PGPORT" =~ ^[0-9]+$ ]] || fail 'PGPORT must be numeric'
[[ "$PGDATABASE" =~ ^[A-Za-z0-9_.-]+$ ]] || fail 'PGDATABASE contains unsupported filename characters'
[[ -d "$BACKUP_OUTPUT_DIR" ]] || fail 'BACKUP_OUTPUT_DIR must be an existing directory'
[[ ! -L "$BACKUP_OUTPUT_DIR" ]] || fail 'BACKUP_OUTPUT_DIR must not be a symbolic link'
[[ -w "$BACKUP_OUTPUT_DIR" ]] || fail 'BACKUP_OUTPUT_DIR is not writable'

server_version_num="$(
  psql --no-psqlrc --quiet --tuples-only --no-align \
    --command='SHOW server_version_num' 2>/dev/null
)" || fail 'database is unreachable or credentials are invalid'
[[ "$server_version_num" =~ ^[0-9]+$ ]] || fail 'database returned an invalid server version'

client_version="$(pg_dump --version | sed -E 's/.* ([0-9]+)(\.[0-9]+)?.*/\1/')"
[[ "$client_version" =~ ^[0-9]+$ ]] || fail 'could not determine pg_dump major version'
server_major=$((server_version_num / 10000))
((client_version >= server_major)) || fail "pg_dump major $client_version is older than server major $server_major"

created_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
filename_timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
backup_name="${PGDATABASE}-${filename_timestamp}.dump"
backup_file="${BACKUP_OUTPUT_DIR%/}/$backup_name"
checksum_file="$backup_file.sha256"
metadata_file="$backup_file.metadata"

[[ ! -e "$backup_file" && ! -e "$checksum_file" && ! -e "$metadata_file" ]] \
  || fail "backup artifact already exists: $backup_name"

temporary_file="$(mktemp "${BACKUP_OUTPUT_DIR%/}/.${backup_name}.partial.XXXXXX")"

pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --serializable-deferrable \
  --file="$temporary_file" \
  || fail 'pg_dump did not complete successfully'

[[ -s "$temporary_file" ]] || fail 'pg_dump produced an empty artifact'
mv -- "$temporary_file" "$backup_file"
temporary_file=''

checksum="$(sha256sum "$backup_file" | awk '{print $1}')"
printf '%s  %s\n' "$checksum" "$backup_name" >"$checksum_file"
printf 'created_at_utc=%s\ndatabase=%s\nserver_version_num=%s\npg_dump_major=%s\nformat=postgresql-custom\nchecksum_algorithm=sha256\n' \
  "$created_at" "$PGDATABASE" "$server_version_num" "$client_version" >"$metadata_file"

printf 'PostgreSQL backup created: %s\n' "$backup_file"
