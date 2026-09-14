#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf 'MinIO inventory failed: %s\n' "$1" >&2
  exit 1
}

[[ -n "${MINIO_INVENTORY_ROOT:-}" ]] || fail 'MINIO_INVENTORY_ROOT is missing'
command -v mc >/dev/null 2>&1 || fail 'required command mc is unavailable'
command -v sort >/dev/null 2>&1 || fail 'required command sort is unavailable'

uuid_pattern='[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
object_key_pattern="^courses/${uuid_pattern}/lessons/${uuid_pattern}/${uuid_pattern}$"
object_paths="$(mc find "$MINIO_INVENTORY_ROOT" --print '{}')" \
  || fail 'could not enumerate snapshot objects'

[[ -n "$object_paths" ]] || exit 0

while IFS= read -r object_path; do
  [[ -n "$object_path" ]] || continue
  relative_key="${object_path#"$MINIO_INVENTORY_ROOT"/}"
  [[ "$relative_key" != "$object_path" && -n "$relative_key" ]] \
    || fail 'object path is outside the requested inventory root'
  [[ "$relative_key" =~ $object_key_pattern ]] \
    || fail 'object key does not match the lesson-media key contract'
  [[ "$relative_key" != *$'\t'* ]] || fail 'object key contains a tab'

  object_stat="$(mc stat --json "$object_path")" || fail 'could not inspect an object'
  [[ "$object_stat" == *'"status":"success"'* ]] || fail 'object stat was not successful'

  size_fragment="${object_stat#*'"size":'}"
  [[ "$size_fragment" != "$object_stat" ]] || fail 'object stat does not contain size'
  object_size="${size_fragment%%,*}"
  [[ "$object_size" =~ ^[1-9][0-9]*$ ]] || fail 'object size is invalid'

  checksum_fragment="${object_stat#*'"SHA256":"'}"
  [[ "$checksum_fragment" != "$object_stat" ]] || fail 'object is missing its SHA-256 checksum'
  object_checksum="${checksum_fragment%%\"*}"
  [[ "$object_checksum" =~ ^[A-Za-z0-9+/]+={0,2}$ ]] || fail 'object SHA-256 is invalid'

  content_type_fragment="${object_stat#*'"Content-Type":"'}"
  [[ "$content_type_fragment" != "$object_stat" ]] || fail 'object is missing Content-Type metadata'
  content_type="${content_type_fragment%%\"*}"
  [[ -n "$content_type" && "$content_type" != *$'\t'* ]] || fail 'object Content-Type is invalid'

  printf '%s\t%s\t%s\t%s\n' \
    "$relative_key" "$object_size" "$object_checksum" "$content_type"
done <<<"$object_paths" | LC_ALL=C sort
