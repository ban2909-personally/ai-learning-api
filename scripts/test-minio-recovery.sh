#!/usr/bin/env bash
set -euo pipefail

server_image='cgr.dev/chainguard/minio:latest@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1'
client_image='cgr.dev/chainguard/minio-client:latest@sha256:b8b144ab34694ecea25aa352c4be9de4c26ee2a02701521dce02ee5593c57338'
resource_prefix="ai-learning-minio-recovery-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
source_server="$resource_prefix-source"
backup_server="$resource_prefix-backup"
restore_server="$resource_prefix-target"
started_at="$(date +%s)"

export MINIO_ROOT_USER='recoverydrill'
export MINIO_ROOT_PASSWORD="recovery-drill-$(date -u +'%s')-$$"
export MC_HOST_source="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@$source_server:9000"
export MC_HOST_backup="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@$backup_server:9000"
export MC_HOST_restore="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@$restore_server:9000"
export MINIO_SOURCE_BUCKET='lesson-media-source'
export MINIO_BACKUP_BUCKET='lesson-media-backup'
export MINIO_TARGET_BUCKET='lesson-media-restore'
export MINIO_SNAPSHOT_ID="drill-$(date -u +'%Y%m%dT%H%M%SZ')-$$"
export MINIO_BACKUP_CONFIRMATION="snapshot:$MINIO_SOURCE_BUCKET:$MINIO_SNAPSHOT_ID"
export MINIO_RESTORE_CONFIRMATION="restore:$MINIO_TARGET_BUCKET:$MINIO_SNAPSHOT_ID"

object_one='courses/11111111-1111-1111-1111-111111111111/lessons/22222222-2222-2222-2222-222222222222/33333333-3333-3333-3333-333333333333'
object_two='courses/11111111-1111-1111-1111-111111111111/lessons/44444444-4444-4444-4444-444444444444/55555555-5555-5555-5555-555555555555'
extra_object='courses/66666666-6666-6666-6666-666666666666/lessons/77777777-7777-7777-7777-777777777777/88888888-8888-8888-8888-888888888888'
export EXTRA_OBJECT="$extra_object"
export SNAPSHOT_ROOT="backup/$MINIO_BACKUP_BUCKET/snapshots/$MINIO_SNAPSHOT_ID"

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
script_mount_source="$script_directory"

if command -v cygpath >/dev/null 2>&1; then
  script_mount_source="$(cygpath -w "$script_directory")"
  export MSYS_NO_PATHCONV=1
fi

fail() {
  printf 'MinIO recovery drill failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  docker rm --force "$source_server" "$backup_server" "$restore_server" >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
  unset MINIO_ROOT_USER MINIO_ROOT_PASSWORD
  unset MC_HOST_source MC_HOST_backup MC_HOST_restore
  unset MINIO_SOURCE_BUCKET MINIO_BACKUP_BUCKET MINIO_TARGET_BUCKET
  unset MINIO_SNAPSHOT_ID MINIO_BACKUP_CONFIRMATION MINIO_RESTORE_CONFIRMATION
  unset EXTRA_OBJECT SNAPSHOT_ROOT
}

client_arguments=(
  --rm
  --network "$network_name"
  --label ai-learning.recovery=minio
  --user 1000:1000
  --cap-drop ALL
  --security-opt no-new-privileges:true
  --tmpfs /tmp:rw,noexec,nosuid,mode=1777,size=16m
  --env MC_CONFIG_DIR=/tmp/mc
  --env MC_HOST_source
  --env MC_HOST_backup
  --env MC_HOST_restore
)

run_mc() {
  docker run "${client_arguments[@]}" "$client_image" "$@"
}

run_client_bash() {
  docker run "${client_arguments[@]}" \
    --env MINIO_BACKUP_BUCKET \
    --env MINIO_TARGET_BUCKET \
    --env MINIO_SNAPSHOT_ID \
    --env EXTRA_OBJECT \
    --env SNAPSHOT_ROOT \
    --entrypoint /usr/bin/bash \
    "$client_image" -c "$1"
}

run_recovery_script() {
  local script_name="$1"

  docker run "${client_arguments[@]}" \
    --mount "type=bind,source=$script_mount_source,target=/scripts,readonly" \
    --env MINIO_SOURCE_BUCKET \
    --env MINIO_BACKUP_BUCKET \
    --env MINIO_TARGET_BUCKET \
    --env MINIO_SNAPSHOT_ID \
    --env MINIO_BACKUP_CONFIRMATION \
    --env MINIO_RESTORE_CONFIRMATION \
    --entrypoint /usr/bin/bash \
    "$client_image" "/scripts/$script_name"
}

wait_for_server() {
  local alias_name="$1"
  local server_name="$2"
  local attempt

  for attempt in $(seq 1 30); do
    run_mc ready "$alias_name" >/dev/null 2>&1 && return 0
    sleep 2
  done

  docker logs "$server_name" >&2 || true
  fail "server $alias_name did not become ready"
}

trap cleanup EXIT
command -v docker >/dev/null 2>&1 || fail 'docker is unavailable'
command -v sha256sum >/dev/null 2>&1 || fail 'sha256sum is unavailable'
command -v cut >/dev/null 2>&1 || fail 'cut is unavailable'
command -v seq >/dev/null 2>&1 || fail 'seq is unavailable'
docker info >/dev/null 2>&1 || fail 'docker daemon is unavailable'

docker network create --label ai-learning.recovery=minio "$network_name" >/dev/null
for server_name in "$source_server" "$backup_server" "$restore_server"; do
  docker run --detach \
    --name "$server_name" \
    --network "$network_name" \
    --label ai-learning.recovery=minio \
    --user 1000:1000 \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    --tmpfs /data:rw,noexec,nosuid,size=128m \
    --env MINIO_ROOT_USER \
    --env MINIO_ROOT_PASSWORD \
    "$server_image" server /data --console-address ':9001' >/dev/null
done

wait_for_server source "$source_server"
wait_for_server backup "$backup_server"
wait_for_server restore "$restore_server"

run_mc mb "source/$MINIO_SOURCE_BUCKET" >/dev/null
run_mc mb "backup/$MINIO_BACKUP_BUCKET" >/dev/null
run_mc mb "restore/$MINIO_TARGET_BUCKET" >/dev/null

docker run "${client_arguments[@]}" \
  --env MINIO_SOURCE_BUCKET \
  --env OBJECT_ONE="$object_one" \
  --env OBJECT_TWO="$object_two" \
  --entrypoint /usr/bin/bash \
  "$client_image" -c '
    printf "video-one-recovery-bytes" >/tmp/lesson.mp4
    printf "video-two-recovery-bytes-are-different" >/tmp/lesson.webm
    mc cp --quiet --attr "Recovery-Test=preserve" /tmp/lesson.mp4 "source/$MINIO_SOURCE_BUCKET/$OBJECT_ONE"
    mc cp --quiet /tmp/lesson.webm "source/$MINIO_SOURCE_BUCKET/$OBJECT_TWO"
  '

run_recovery_script backup-minio.sh

if run_recovery_script backup-minio.sh; then
  fail 'backup accepted an existing snapshot prefix'
fi

saved_restore_confirmation="$MINIO_RESTORE_CONFIRMATION"
unset MINIO_RESTORE_CONFIRMATION
if run_recovery_script restore-minio.sh; then
  fail 'restore accepted a missing target-bound confirmation'
fi
export MINIO_RESTORE_CONFIRMATION="$saved_restore_confirmation"

run_client_bash 'printf "guard" >/tmp/guard.mp4; mc cp --quiet /tmp/guard.mp4 "restore/$MINIO_TARGET_BUCKET/$EXTRA_OBJECT"'
if run_recovery_script restore-minio.sh; then
  fail 'restore accepted a non-empty target bucket'
fi
run_mc rm --force "restore/$MINIO_TARGET_BUCKET/$extra_object" >/dev/null

snapshot_root="$SNAPSHOT_ROOT"
run_client_bash '
  mc cp --quiet "$SNAPSHOT_ROOT/inventory.tsv" "backup/$MINIO_BACKUP_BUCKET/test-fixtures/original-inventory.tsv"
  mc cat "backup/$MINIO_BACKUP_BUCKET/test-fixtures/original-inventory.tsv" >/tmp/inventory.tsv
  printf "tampered\n" >>/tmp/inventory.tsv
  mc cp --quiet --checksum SHA256 /tmp/inventory.tsv "$SNAPSHOT_ROOT/inventory.tsv"
'
if run_recovery_script restore-minio.sh; then
  fail 'restore accepted a tampered snapshot inventory'
fi
run_mc cp --quiet --preserve --checksum SHA256 \
  "backup/$MINIO_BACKUP_BUCKET/test-fixtures/original-inventory.tsv" \
  "$snapshot_root/inventory.tsv"

run_mc cp --quiet --preserve --checksum SHA256 \
  "$snapshot_root/snapshot.metadata" \
  "backup/$MINIO_BACKUP_BUCKET/test-fixtures/original-snapshot.metadata"
run_client_bash '
  metadata="$(mc cat "$SNAPSHOT_ROOT/snapshot.metadata")"
  metadata="${metadata/object_count=2/object_count=3}"
  printf "%s\n" "$metadata" | mc pipe --quiet --checksum SHA256 "$SNAPSHOT_ROOT/snapshot.metadata"
'
if run_recovery_script restore-minio.sh; then
  fail 'restore accepted a snapshot metadata object count mismatch'
fi
run_mc cp --quiet --preserve --checksum SHA256 \
  "backup/$MINIO_BACKUP_BUCKET/test-fixtures/original-snapshot.metadata" \
  "$snapshot_root/snapshot.metadata"

run_recovery_script restore-minio.sh

expected_one="$(printf 'video-one-recovery-bytes' | sha256sum | cut -d' ' -f1)"
actual_one="$(run_mc cat "restore/$MINIO_TARGET_BUCKET/$object_one" | sha256sum | cut -d' ' -f1)"
expected_two="$(printf 'video-two-recovery-bytes-are-different' | sha256sum | cut -d' ' -f1)"
actual_two="$(run_mc cat "restore/$MINIO_TARGET_BUCKET/$object_two" | sha256sum | cut -d' ' -f1)"
[[ "$actual_one" == "$expected_one" ]] || fail 'first restored object bytes differ'
[[ "$actual_two" == "$expected_two" ]] || fail 'second restored object bytes differ'

first_stat="$(run_mc stat --json "restore/$MINIO_TARGET_BUCKET/$object_one")"
second_stat="$(run_mc stat --json "restore/$MINIO_TARGET_BUCKET/$object_two")"
[[ "$first_stat" == *'"Content-Type":"video/mp4"'* ]] || fail 'video/mp4 content type was not preserved'
[[ "$first_stat" == *'"X-Amz-Meta-Recovery-Test":"preserve"'* ]] || fail 'custom metadata sample was not preserved'
[[ "$second_stat" == *'"Content-Type":"video/webm"'* ]] || fail 'video/webm content type was not preserved'

run_mc rm --force "restore/$MINIO_TARGET_BUCKET/$object_two" >/dev/null
if run_recovery_script verify-minio-restore.sh; then
  fail 'verification accepted a missing restored object'
fi
run_mc cp --quiet --preserve --checksum SHA256 \
  "$snapshot_root/objects/$object_two" \
  "restore/$MINIO_TARGET_BUCKET/$object_two"

run_client_bash 'printf "extra" >/tmp/extra.mp4; mc cp --quiet --checksum SHA256 /tmp/extra.mp4 "restore/$MINIO_TARGET_BUCKET/$EXTRA_OBJECT"'
if run_recovery_script verify-minio-restore.sh; then
  fail 'verification accepted an extra restored object'
fi
run_mc rm --force "restore/$MINIO_TARGET_BUCKET/$extra_object" >/dev/null

run_recovery_script verify-minio-restore.sh

finished_at="$(date +%s)"
printf 'MinIO current-object recovery drill passed in %s seconds.\n' "$((finished_at - started_at))"
