#!/usr/bin/env bash
set -euo pipefail

postgres_image='postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0'
source_database='ai_learning_source'
restore_database='ai_learning_restore'
database_user='recovery_operator'
database_password="recovery-drill-$(date -u +'%s')-$$"
resource_prefix="ai-learning-pg-recovery-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
source_container="$resource_prefix-source"
restore_container="$resource_prefix-target"
backup_volume="$resource_prefix-backups"
started_at="$(date +%s)"

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
script_mount_source="$script_directory"

# Git Bash rewrites container paths unless conversion is disabled. Keep the host
# bind source native and all container paths unchanged.
if command -v cygpath >/dev/null 2>&1; then
  script_mount_source="$(cygpath -w "$script_directory")"
  export MSYS_NO_PATHCONV=1
fi

fail() {
  printf 'PostgreSQL recovery drill failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  docker rm --force "$source_container" "$restore_container" >/dev/null 2>&1 || true
  docker volume rm --force "$backup_volume" >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
}

wait_for_database() {
  local container_name="$1"
  local attempt
  local health

  for attempt in $(seq 1 30); do
    health="$(docker inspect --format '{{.State.Health.Status}}' "$container_name" 2>/dev/null || true)"
    [[ "$health" == 'healthy' ]] && return 0
    sleep 2
  done

  docker logs "$container_name" >&2 || true
  fail "container $container_name did not become healthy"
}

run_restore() {
  local confirmation="$1"
  local backup_file="$2"

  docker run --rm \
    --network "$network_name" \
    --user postgres \
    --cap-drop ALL \
    --security-opt no-new-privileges:true \
    --mount "type=bind,source=$script_mount_source,target=/scripts,readonly" \
    --mount "type=volume,source=$backup_volume,target=/backups" \
    --env PGHOST="$restore_container" \
    --env PGPORT=5432 \
    --env PGDATABASE="$restore_database" \
    --env PGUSER="$database_user" \
    --env PGPASSWORD="$database_password" \
    --env BACKUP_FILE="$backup_file" \
    --env RESTORE_CONFIRMATION="$confirmation" \
    "$postgres_image" bash /scripts/restore-postgres.sh
}

assert_target_query() {
  local query="$1"
  local expected="$2"
  local actual

  actual="$(
    docker exec "$restore_container" \
      psql --no-psqlrc --quiet --tuples-only --no-align \
      --username "$database_user" --dbname "$restore_database" --command "$query"
  )"
  [[ "$actual" == "$expected" ]] || fail "query returned '$actual'; expected '$expected'"
}

trap cleanup EXIT
command -v docker >/dev/null 2>&1 || fail 'docker is unavailable'
command -v seq >/dev/null 2>&1 || fail 'seq is unavailable'
docker info >/dev/null 2>&1 || fail 'docker daemon is unavailable'

docker network create "$network_name" >/dev/null
docker volume create "$backup_volume" >/dev/null

for container_spec in \
  "$source_container:$source_database" \
  "$restore_container:$restore_database"; do
  container_name="${container_spec%%:*}"
  database_name="${container_spec#*:}"
  docker run --detach \
    --name "$container_name" \
    --network "$network_name" \
    --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=256m \
    --env POSTGRES_DB="$database_name" \
    --env POSTGRES_USER="$database_user" \
    --env POSTGRES_PASSWORD="$database_password" \
    --health-cmd "pg_isready --username $database_user --dbname $database_name" \
    --health-interval 2s \
    --health-timeout 2s \
    --health-retries 20 \
    "$postgres_image" >/dev/null
done

wait_for_database "$source_container"
wait_for_database "$restore_container"

docker exec --interactive "$source_container" \
  psql --no-psqlrc --set ON_ERROR_STOP=1 \
  --username "$database_user" --dbname "$source_database" <<'SQL'
CREATE TABLE course_snapshot (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug text NOT NULL UNIQUE,
    price numeric(12, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE enrollment_snapshot (
    learner_id uuid NOT NULL,
    course_id bigint NOT NULL REFERENCES course_snapshot(id),
    active boolean NOT NULL,
    PRIMARY KEY (learner_id, course_id)
);

CREATE VIEW active_enrollment_snapshot AS
SELECT learner_id, course_id
FROM enrollment_snapshot
WHERE active;

INSERT INTO course_snapshot (slug, price)
VALUES ('java-foundations', 0), ('spring-modulith', 1499000);

INSERT INTO enrollment_snapshot (learner_id, course_id, active)
VALUES
    ('00000000-0000-0000-0000-000000000001', 1, true),
    ('00000000-0000-0000-0000-000000000001', 2, true),
    ('00000000-0000-0000-0000-000000000002', 2, false);
SQL

# The named volume is initialized as root. Restrict it before running backup and
# restore tools as the image's unprivileged postgres user.
docker run --rm \
  --mount "type=volume,source=$backup_volume,target=/backups" \
  "$postgres_image" bash -c 'chown postgres:postgres /backups && chmod 700 /backups'

docker run --rm \
  --network "$network_name" \
  --user postgres \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --mount "type=bind,source=$script_mount_source,target=/scripts,readonly" \
  --mount "type=volume,source=$backup_volume,target=/backups" \
  --env PGHOST="$source_container" \
  --env PGPORT=5432 \
  --env PGDATABASE="$source_database" \
  --env PGUSER="$database_user" \
  --env PGPASSWORD="$database_password" \
  --env BACKUP_OUTPUT_DIR=/backups \
  "$postgres_image" bash /scripts/backup-postgres.sh

backup_file="$(
  docker run --rm \
    --user postgres \
    --mount "type=volume,source=$backup_volume,target=/backups,readonly" \
    "$postgres_image" find /backups -maxdepth 1 -type f -name '*.dump' -print -quit
)"
[[ -n "$backup_file" ]] || fail 'backup artifact was not created'

artifact_mode="$(
  docker run --rm \
    --user postgres \
    --mount "type=volume,source=$backup_volume,target=/backups,readonly" \
    --env BACKUP_FILE="$backup_file" \
    "$postgres_image" bash -c 'stat -c "%a" "$BACKUP_FILE" && stat -c "%a" "$BACKUP_FILE.sha256" && stat -c "%a" "$BACKUP_FILE.metadata"'
)"
[[ "$artifact_mode" == $'600\n600\n600' ]] || fail "backup artifacts are not private: $artifact_mode"

if run_restore '' "$backup_file"; then
  fail 'restore accepted a missing target-bound confirmation'
fi

docker exec "$restore_container" \
  psql --no-psqlrc --set ON_ERROR_STOP=1 --quiet \
  --username "$database_user" --dbname "$restore_database" \
  --command 'CREATE TABLE restore_guard (id integer PRIMARY KEY);'
if run_restore "restore:$restore_database" "$backup_file"; then
  fail 'restore accepted a non-empty target without an override'
fi
docker exec "$restore_container" \
  psql --no-psqlrc --set ON_ERROR_STOP=1 --quiet \
  --username "$database_user" --dbname "$restore_database" \
  --command 'DROP TABLE restore_guard;'

tampered_file='/backups/tampered.dump'
docker run --rm \
  --user postgres \
  --mount "type=volume,source=$backup_volume,target=/backups" \
  --env BACKUP_FILE="$backup_file" \
  --env TAMPERED_FILE="$tampered_file" \
  "$postgres_image" bash -c \
    'cp "$BACKUP_FILE" "$TAMPERED_FILE"; printf "tampered" >>"$TAMPERED_FILE"; cp "$BACKUP_FILE.sha256" "$TAMPERED_FILE.sha256"; cp "$BACKUP_FILE.metadata" "$TAMPERED_FILE.metadata"'
if run_restore "restore:$restore_database" "$tampered_file"; then
  fail 'restore accepted an artifact with a mismatched checksum'
fi

run_restore "restore:$restore_database" "$backup_file"

assert_target_query 'SELECT count(*) FROM course_snapshot;' '2'
assert_target_query 'SELECT count(*) FROM enrollment_snapshot;' '3'
assert_target_query 'SELECT count(*) FROM active_enrollment_snapshot;' '2'
assert_target_query "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND conrelid = 'enrollment_snapshot'::regclass;" '1'
assert_target_query 'SELECT last_value FROM course_snapshot_id_seq;' '2'

finished_at="$(date +%s)"
printf 'PostgreSQL recovery drill passed in %s seconds.\n' "$((finished_at - started_at))"
