#!/usr/bin/env bash
set -euo pipefail

required_variables=(
  MC_HOST_source
  MC_HOST_backup
  MINIO_SOURCE_BUCKET
  MINIO_BACKUP_BUCKET
  MINIO_SNAPSHOT_ID
  MINIO_BACKUP_CONFIRMATION
)
required_commands=(date mc mktemp rm sha256sum)
private_directory=''

fail() {
  printf 'MinIO backup failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [[ -n "$private_directory" && -d "$private_directory" ]]; then
    rm -rf -- "$private_directory"
  fi
}

count_nonempty_lines() {
  local file="$1"
  local count=0
  local line

  while IFS= read -r line; do
    [[ -n "$line" ]] && count=$((count + 1))
  done <"$file"
  printf '%s' "$count"
}

trap cleanup EXIT
umask 077

for variable_name in "${required_variables[@]}"; do
  [[ -n "${!variable_name:-}" ]] || fail "required environment variable $variable_name is missing"
done
for command_name in "${required_commands[@]}"; do
  command -v "$command_name" >/dev/null 2>&1 || fail "required command $command_name is unavailable"
done

bucket_pattern='^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$'
snapshot_pattern='^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
[[ "$MINIO_SOURCE_BUCKET" =~ $bucket_pattern ]] || fail 'MINIO_SOURCE_BUCKET is invalid'
[[ "$MINIO_BACKUP_BUCKET" =~ $bucket_pattern ]] || fail 'MINIO_BACKUP_BUCKET is invalid'
[[ "$MINIO_SNAPSHOT_ID" =~ $snapshot_pattern ]] || fail 'MINIO_SNAPSHOT_ID is invalid'
[[ "$MINIO_BACKUP_CONFIRMATION" == "snapshot:$MINIO_SOURCE_BUCKET:$MINIO_SNAPSHOT_ID" ]] \
  || fail "MINIO_BACKUP_CONFIRMATION must equal snapshot:$MINIO_SOURCE_BUCKET:$MINIO_SNAPSHOT_ID"

private_directory="$(mktemp -d)"
export MC_CONFIG_DIR="$private_directory/mc"
inventory_file="$private_directory/inventory.tsv"
metadata_file="$private_directory/snapshot.metadata"
script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

source_root="source/$MINIO_SOURCE_BUCKET"
snapshot_root="backup/$MINIO_BACKUP_BUCKET/snapshots/$MINIO_SNAPSHOT_ID"
snapshot_objects="$snapshot_root/objects"

mc stat "$source_root" >/dev/null 2>&1 || fail 'source bucket is unreachable'
mc stat "backup/$MINIO_BACKUP_BUCKET" >/dev/null 2>&1 || fail 'backup bucket is unreachable'

# `mc find` reports a missing object prefix as an error. The parent backup bucket
# was already authenticated above, so an absent prefix is the expected new-snapshot
# state. Existing objects still produce output and are refused.
existing_objects="$(mc find "$snapshot_root" --print '{}' 2>/dev/null || true)"
[[ -z "$existing_objects" ]] || fail 'snapshot prefix already contains data'

source_objects="$(mc find "$source_root" --print '{}')" || fail 'could not enumerate source objects'
if [[ -n "$source_objects" ]]; then
  mc mirror --quiet --retry --preserve --checksum SHA256 \
    "$source_root" "$snapshot_objects" \
    || fail 'object mirror did not complete successfully'

  differences="$(mc diff --json "$source_root" "$snapshot_objects")" \
    || fail 'could not compare source and backup objects'
  [[ -z "$differences" ]] || fail 'source changed or backup differs after mirror'

  MINIO_INVENTORY_ROOT="$snapshot_objects" \
    bash "$script_directory/create-minio-inventory.sh" >"$inventory_file" \
    || fail 'could not generate the backup inventory'
else
  : >"$inventory_file"
fi

object_count="$(count_nonempty_lines "$inventory_file")"
inventory_checksum="$(sha256sum "$inventory_file")"
inventory_checksum="${inventory_checksum%% *}"
created_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

mc pipe --quiet --checksum SHA256 \
  "$snapshot_root/inventory.tsv" <"$inventory_file" \
  || fail 'could not publish the snapshot inventory'

printf 'format=ai-learning-minio-current-object-v1\nstate=complete\ncreated_at_utc=%s\nsource_bucket=%s\nsnapshot_id=%s\nobject_count=%s\ninventory_sha256=%s\n' \
  "$created_at" "$MINIO_SOURCE_BUCKET" "$MINIO_SNAPSHOT_ID" \
  "$object_count" "$inventory_checksum" >"$metadata_file"

# Publish the completion marker last. A prefix without this marker is incomplete.
mc pipe --quiet --checksum SHA256 \
  "$snapshot_root/snapshot.metadata" <"$metadata_file" \
  || fail 'could not publish the snapshot completion marker'

inventory_stat="$(mc stat --json "$snapshot_root/inventory.tsv")" \
  || fail 'published inventory is unreadable'
metadata_stat="$(mc stat --json "$snapshot_root/snapshot.metadata")" \
  || fail 'published completion marker is unreadable'
[[ "$inventory_stat" == *'"SHA256":"'* ]] || fail 'published inventory lacks SHA-256'
[[ "$metadata_stat" == *'"SHA256":"'* ]] || fail 'completion marker lacks SHA-256'

printf 'MinIO snapshot completed: %s (%s objects)\n' "$snapshot_root" "$object_count"
