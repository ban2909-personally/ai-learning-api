#!/usr/bin/env bash
set -euo pipefail

required_variables=(
  MC_HOST_backup
  MC_HOST_restore
  MINIO_BACKUP_BUCKET
  MINIO_TARGET_BUCKET
  MINIO_SNAPSHOT_ID
)
required_commands=(mc mktemp rm sha256sum)
private_directory=''

fail() {
  printf 'MinIO restore verification failed: %s\n' "$1" >&2
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
[[ "$MINIO_BACKUP_BUCKET" =~ $bucket_pattern ]] || fail 'MINIO_BACKUP_BUCKET is invalid'
[[ "$MINIO_TARGET_BUCKET" =~ $bucket_pattern ]] || fail 'MINIO_TARGET_BUCKET is invalid'
[[ "$MINIO_SNAPSHOT_ID" =~ $snapshot_pattern ]] || fail 'MINIO_SNAPSHOT_ID is invalid'

private_directory="$(mktemp -d)"
export MC_CONFIG_DIR="$private_directory/mc"
expected_inventory="$private_directory/expected-inventory.tsv"
actual_inventory="$private_directory/actual-inventory.tsv"
metadata_file="$private_directory/snapshot.metadata"
script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

snapshot_root="backup/$MINIO_BACKUP_BUCKET/snapshots/$MINIO_SNAPSHOT_ID"
snapshot_objects="$snapshot_root/objects"
target_root="restore/$MINIO_TARGET_BUCKET"

mc stat "$target_root" >/dev/null 2>&1 || fail 'restore target bucket is unreachable'
mc cat "$snapshot_root/snapshot.metadata" >"$metadata_file" \
  || fail 'snapshot completion marker is missing'
mc cat "$snapshot_root/inventory.tsv" >"$expected_inventory" \
  || fail 'snapshot inventory is missing'

snapshot_format=''
snapshot_state=''
recorded_snapshot_id=''
recorded_object_count=''
recorded_inventory_checksum=''
while IFS='=' read -r key value; do
  case "$key" in
    format) snapshot_format="$value" ;;
    state) snapshot_state="$value" ;;
    snapshot_id) recorded_snapshot_id="$value" ;;
    object_count) recorded_object_count="$value" ;;
    inventory_sha256) recorded_inventory_checksum="$value" ;;
  esac
done <"$metadata_file"

[[ "$snapshot_format" == 'ai-learning-minio-current-object-v1' ]] || fail 'snapshot format is unsupported'
[[ "$snapshot_state" == 'complete' ]] || fail 'snapshot is not complete'
[[ "$recorded_snapshot_id" == "$MINIO_SNAPSHOT_ID" ]] || fail 'snapshot metadata id does not match'
[[ "$recorded_object_count" =~ ^[0-9]+$ ]] || fail 'snapshot object count is invalid'
[[ "$recorded_inventory_checksum" =~ ^[0-9a-f]{64}$ ]] || fail 'snapshot inventory checksum is invalid'

expected_checksum="$(sha256sum "$expected_inventory")"
expected_checksum="${expected_checksum%% *}"
[[ "$expected_checksum" == "$recorded_inventory_checksum" ]] || fail 'snapshot inventory checksum does not match metadata'
[[ "$(count_nonempty_lines "$expected_inventory")" == "$recorded_object_count" ]] \
  || fail 'snapshot inventory object count does not match metadata'

if ((recorded_object_count > 0)); then
  MINIO_INVENTORY_ROOT="$target_root" \
    bash "$script_directory/create-minio-inventory.sh" >"$actual_inventory" \
    || fail 'could not generate restore target inventory'

  differences="$(mc diff --json "$snapshot_objects" "$target_root")" \
    || fail 'could not compare snapshot and restore target'
  [[ -z "$differences" ]] || fail 'restore target has missing, extra, or different objects'
else
  target_objects="$(mc find "$target_root" --print '{}')" || fail 'could not inspect restore target'
  [[ -z "$target_objects" ]] || fail 'restore target contains objects for an empty snapshot'
  : >"$actual_inventory"
fi

actual_checksum="$(sha256sum "$actual_inventory")"
actual_checksum="${actual_checksum%% *}"
[[ "$actual_checksum" == "$recorded_inventory_checksum" ]] \
  || fail 'restore target key, size, checksum, or content type differs from inventory'

printf 'MinIO restore verified: %s (%s objects)\n' "$target_root" "$recorded_object_count"
