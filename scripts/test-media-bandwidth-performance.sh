#!/usr/bin/env bash
set -euo pipefail

postgres_image='postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0'
redis_image='redis:7.4-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf'
minio_server_image='cgr.dev/chainguard/minio:latest@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1'
minio_client_image='cgr.dev/chainguard/minio-client:latest@sha256:b8b144ab34694ecea25aa352c4be9de4c26ee2a02701521dce02ee5593c57338'
k6_image='grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6'
app_image="${PERFORMANCE_APP_IMAGE:-ai-learning-api:ci}"
resource_prefix="alp-media-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
postgres_container="$resource_prefix-postgres"
redis_container="$resource_prefix-redis"
minio_container="$resource_prefix-minio"
app_container="$resource_prefix-api"
k6_container="$resource_prefix-k6"
database_name='ai_learning_media_performance'
database_user='performance_operator'
expected_identities=40
object_size_bytes=33554432
range_size_bytes=1048576
range_sha256='30e14955ebf1352266dc2ff8067e68104607e750abb9d3b36582b8af909fcb58'
media_bucket='lesson-media-bandwidth-performance'
object_key='courses/14000000-0000-0000-0000-000000000001/lessons/34000000-0000-0000-0000-000000000001/media'
started_at="$(date +%s)"
collector_pid=''
diagnostics_directory=''
diagnostics_stop_file=''
diagnostics_samples_file=''

export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
export REDIS_PASSWORD="$(openssl rand -hex 24)"
export REDISCLI_AUTH="$REDIS_PASSWORD"
export MINIO_ROOT_USER='media-performance'
export MINIO_ROOT_PASSWORD="$(openssl rand -hex 24)"
export MC_HOST_media="http://$MINIO_ROOT_USER:$MINIO_ROOT_PASSWORD@$minio_container:9000"
export DB_URL="jdbc:postgresql://$postgres_container:5432/$database_name"
export DB_USERNAME="$database_user"
export DB_PASSWORD="$POSTGRES_PASSWORD"
export REDIS_HOST="$redis_container"
export REDIS_PORT='6379'
export JWT_SECRET="$(openssl rand -base64 48 | tr -d '\r\n')"
export JWT_ISSUER='ai-learning-media-performance'
export CORS_ALLOWED_ORIGIN='https://performance.example.invalid'
export KAFKA_BOOTSTRAP_SERVERS='unused.invalid:9092'
export OPENAI_API_KEY="$(openssl rand -hex 24)"
export MINIO_ENDPOINT="http://$minio_container:9000"
export MINIO_ACCESS_KEY="$MINIO_ROOT_USER"
export MINIO_SECRET_KEY="$MINIO_ROOT_PASSWORD"
export MINIO_MEDIA_BUCKET="$media_bucket"
export MEDIA_BANDWIDTH_PASSWORD="$(openssl rand -hex 24)"
export MEDIA_BANDWIDTH_RUN_ID="$resource_prefix"
export MEDIA_RANGE_SHA256="$range_sha256"

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_directory="$(cd -- "$script_directory/.." && pwd)"
performance_directory="$repository_directory/performance"
performance_mount_source="$performance_directory"
results_directory="$repository_directory/target/performance"
results_mount_source="$results_directory"
k6_user_arguments=(--user "$(id -u):$(id -g)")

if command -v cygpath >/dev/null 2>&1; then
  performance_mount_source="$(cygpath -w "$performance_directory")"
  results_mount_source="$(cygpath -w "$results_directory")"
  k6_user_arguments=()
  export MSYS_NO_PATHCONV=1
fi

fail() {
  printf 'Media bandwidth performance failed: %s\n' "$1" >&2
  if docker inspect "$app_container" >/dev/null 2>&1; then
    printf '%s\n' '--- application log tail (failure diagnostics) ---' >&2
    docker logs --tail 200 "$app_container" >&2 || true
    printf '%s\n' '--- end application log tail ---' >&2
  fi
  exit 1
}

cleanup() {
  if [[ -n "$collector_pid" ]] && kill -0 "$collector_pid" >/dev/null 2>&1; then
    kill "$collector_pid" >/dev/null 2>&1 || true
    wait "$collector_pid" >/dev/null 2>&1 || true
  fi
  docker rm --force \
    "$k6_container" "$app_container" "$minio_container" \
    "$redis_container" "$postgres_container" \
    >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
  unset POSTGRES_PASSWORD REDIS_PASSWORD REDISCLI_AUTH DB_URL DB_USERNAME DB_PASSWORD
  unset REDIS_HOST REDIS_PORT JWT_SECRET JWT_ISSUER CORS_ALLOWED_ORIGIN
  unset KAFKA_BOOTSTRAP_SERVERS OPENAI_API_KEY MINIO_ENDPOINT MINIO_ACCESS_KEY
  unset MINIO_SECRET_KEY MINIO_MEDIA_BUCKET MINIO_ROOT_USER MINIO_ROOT_PASSWORD
  unset MC_HOST_media MEDIA_BANDWIDTH_PASSWORD MEDIA_BANDWIDTH_RUN_ID
  unset MEDIA_RANGE_SHA256 MEDIA_ETAG
  unset DIAGNOSTICS_WORKLOAD_KIND DIAGNOSTICS_APP_PORT DIAGNOSTICS_APP_CONTAINER
  unset DIAGNOSTICS_POSTGRES_CONTAINER DIAGNOSTICS_REDIS_CONTAINER
  unset DIAGNOSTICS_MINIO_CONTAINER DIAGNOSTICS_DATABASE_NAME
  unset DIAGNOSTICS_DATABASE_USER DIAGNOSTICS_BEARER_TOKEN
  unset DIAGNOSTICS_APP_IMAGE_ID DIAGNOSTICS_STOP_FILE
  unset DIAGNOSTICS_SAMPLES_FILE DIAGNOSTICS_OUTPUT_FILE
  [[ -z "$diagnostics_stop_file" ]] || rm -f -- "$diagnostics_stop_file"
  [[ -z "$diagnostics_samples_file" ]] || rm -f -- "$diagnostics_samples_file"
  [[ -z "$diagnostics_directory" ]] || rmdir -- "$diagnostics_directory" 2>/dev/null || true
}

wait_for_database() {
  local attempt
  local health
  for attempt in $(seq 1 30); do
    health="$(docker inspect --format '{{.State.Health.Status}}' "$postgres_container" 2>/dev/null || true)"
    [[ "$health" == 'healthy' ]] && return 0
    sleep 2
  done
  docker logs "$postgres_container" >&2 || true
  fail 'PostgreSQL did not become healthy'
}

wait_for_redis() {
  local attempt
  for attempt in $(seq 1 30); do
    if docker exec --env REDISCLI_AUTH \
      "$redis_container" redis-cli ping 2>/dev/null | grep --quiet '^PONG$'; then
      return 0
    fi
    sleep 2
  done
  docker logs "$redis_container" >&2 || true
  fail 'Redis did not become ready'
}

minio_client_arguments=(
  --rm
  --network "$network_name"
  --label ai-learning.performance=media-bandwidth
  --user 1000:1000
  --cap-drop ALL
  --security-opt no-new-privileges:true
  --tmpfs /tmp:rw,noexec,nosuid,mode=1777,size=64m
  --env MC_CONFIG_DIR=/tmp/mc
  --env MC_HOST_media
)

run_mc() {
  docker run "${minio_client_arguments[@]}" "$minio_client_image" "$@"
}

wait_for_minio() {
  local attempt
  for attempt in $(seq 1 30); do
    run_mc ready media >/dev/null 2>&1 && return 0
    sleep 2
  done
  docker logs "$minio_container" >&2 || true
  fail 'MinIO did not become ready'
}

wait_for_application() {
  local attempt
  local application_port
  application_port="$(docker inspect --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' "$app_container")"
  for attempt in $(seq 1 60); do
    if curl --fail --silent --show-error \
      "http://127.0.0.1:$application_port/actuator/health/readiness" \
      >/dev/null 2>&1; then
      printf '%s' "$application_port"
      return 0
    fi
    sleep 2
  done
  docker logs "$app_container" >&2 || true
  fail 'application did not become ready'
}

database_scalar() {
  local query="$1"
  docker exec "$postgres_container" \
    psql --no-psqlrc --quiet --tuples-only --no-align \
    --username "$database_user" --dbname "$database_name" \
    --command "$query"
}

json_number_from_file() {
  local file="$1"
  local key="$2"
  sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p" "$file" | head -n 1
}

trap cleanup EXIT
for command_name in awk curl docker grep head id kill mkdir mktemp openssl sed seq sleep tr; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "required command $command_name is unavailable"
done
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
docker image inspect "$app_image" >/dev/null 2>&1 \
  || fail "application image $app_image is unavailable; build it before running the profile"
[[ -f "$performance_directory/media-bandwidth-seed.sql" ]] \
  || fail 'media bandwidth seed file is missing'
[[ -f "$performance_directory/media-bandwidth.js" ]] \
  || fail 'media bandwidth k6 workload is missing'
[[ -x "$script_directory/collect-resource-diagnostics.sh" ]] \
  || fail 'resource collector is missing or not executable'

mkdir -p "$results_directory"
client_summary_file="$results_directory/media-bandwidth-client-summary.json"
pipeline_summary_file="$results_directory/media-bandwidth-pipeline-summary.json"
resource_summary_file="$results_directory/media-bandwidth-resource-summary.json"
rm -f -- "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"

docker network create \
  --label ai-learning.performance=media-bandwidth \
  "$network_name" >/dev/null

docker run --detach \
  --name "$postgres_container" \
  --network "$network_name" \
  --label ai-learning.performance=media-bandwidth \
  --user postgres \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,uid=999,gid=999,mode=0700,size=512m \
  --env POSTGRES_DB="$database_name" \
  --env POSTGRES_USER="$database_user" \
  --env POSTGRES_PASSWORD \
  --health-cmd "pg_isready --username $database_user --dbname $database_name" \
  --health-interval 2s \
  --health-timeout 2s \
  --health-retries 20 \
  "$postgres_image" >/dev/null

docker run --detach \
  --name "$redis_container" \
  --network "$network_name" \
  --label ai-learning.performance=media-bandwidth \
  --user 999:1000 \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --tmpfs /data:rw,noexec,nosuid,size=64m \
  --tmpfs /tmp:rw,noexec,nosuid,mode=1777,size=1m \
  --env REDIS_PASSWORD \
  --entrypoint /bin/sh \
  "$redis_image" -c '
    umask 077
    printf "save \"\"\nappendonly no\nrequirepass %s\n" "$REDIS_PASSWORD" >/tmp/redis.conf
    exec redis-server /tmp/redis.conf
  ' >/dev/null

docker run --detach \
  --name "$minio_container" \
  --network "$network_name" \
  --label ai-learning.performance=media-bandwidth \
  --user 1000:1000 \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --pids-limit 128 \
  --memory 512m \
  --cpus 1 \
  --tmpfs /data:rw,noexec,nosuid,size=2g \
  --env MINIO_CI_CD=1 \
  --env MINIO_ROOT_USER \
  --env MINIO_ROOT_PASSWORD \
  "$minio_server_image" server /data --console-address ':9001' >/dev/null

wait_for_database
wait_for_redis
wait_for_minio

run_mc mb "media/$media_bucket" >/dev/null
docker run "${minio_client_arguments[@]}" \
  --env MEDIA_BUCKET="$media_bucket" \
  --env OBJECT_KEY="$object_key" \
  --entrypoint /usr/bin/bash \
  "$minio_client_image" -c '
    set -euo pipefail
    dd if=/dev/zero bs=1048576 count=32 status=none \
      | mc pipe --quiet --attr "Content-Type=video/mp4" "media/$MEDIA_BUCKET/$OBJECT_KEY"
  '

object_stat="$(run_mc stat --json "media/$media_bucket/$object_key")" \
  || fail 'MinIO object stat failed'
object_size="$(printf '%s' "$object_stat" \
  | sed -n 's/.*"size":[[:space:]]*\([0-9][0-9]*\).*/\1/p')"
export MEDIA_ETAG="$(printf '%s' "$object_stat" \
  | sed -n 's/.*"etag":"\([0-9a-f-]*\)".*/\1/p')"
unset object_stat
[[ "$object_size" == "$object_size_bytes" ]] \
  || fail "MinIO object size is $object_size instead of $object_size_bytes"
[[ "$MEDIA_ETAG" =~ ^[0-9a-f]{32}(-[0-9]+)?$ ]] \
  || fail 'MinIO object ETag is missing or invalid'

docker run --detach \
  --name "$app_container" \
  --network "$network_name" \
  --label ai-learning.performance=media-bandwidth \
  --publish 127.0.0.1::8080 \
  --read-only \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --pids-limit 512 \
  --memory 1g \
  --cpus 2 \
  --tmpfs /tmp:rw,noexec,nosuid,uid=65532,gid=65532,mode=1700,size=64m \
  --env DB_URL \
  --env DB_USERNAME \
  --env DB_PASSWORD \
  --env REDIS_HOST \
  --env REDIS_PORT \
  --env REDIS_PASSWORD \
  --env JWT_SECRET \
  --env JWT_ISSUER \
  --env CORS_ALLOWED_ORIGIN \
  --env KAFKA_BOOTSTRAP_SERVERS \
  --env OPENAI_API_KEY \
  --env MINIO_ENDPOINT \
  --env MINIO_ACCESS_KEY \
  --env MINIO_SECRET_KEY \
  --env MINIO_MEDIA_BUCKET \
  --env MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics \
  "$app_image" >/dev/null

application_port="$(wait_for_application)"
application_image_id="$(docker image inspect --format '{{.Id}}' "$app_image")"

docker exec --interactive "$postgres_container" \
  psql --no-psqlrc --username "$database_user" --dbname "$database_name" \
  --set=media_etag="$MEDIA_ETAG" \
  <"$performance_directory/media-bandwidth-seed.sql" >/dev/null

fixture_counts="$(database_scalar "
  SELECT concat_ws(':',
    (SELECT count(*) FROM courses WHERE slug = 'media-bandwidth-performance' AND status = 'PUBLISHED' AND price = 0),
    (SELECT count(*) FROM lessons WHERE id = '34000000-0000-0000-0000-000000000001' AND media_size_bytes = $object_size_bytes AND media_etag = '$MEDIA_ETAG')
  );
")"
[[ "$fixture_counts" == '1:1' ]] \
  || fail "fixture validation returned $fixture_counts instead of 1:1"

curl --fail --silent --show-error \
  "http://127.0.0.1:$application_port/api/v1/courses/media-bandwidth-performance" \
  >/dev/null || fail 'published media course warm-up failed'

metrics_response="$(curl --silent --show-error --write-out $'\n%{http_code}' \
  "http://127.0.0.1:$application_port/actuator/metrics")" \
  || fail 'unauthenticated metrics security probe failed'
metrics_status="${metrics_response##*$'\n'}"
unset metrics_response
[[ "$metrics_status" == '401' ]] \
  || fail "unauthenticated metrics request returned HTTP $metrics_status instead of 401"

diagnostics_password="$(openssl rand -hex 24)"
diagnostics_email="diagnostics-$resource_prefix@example.invalid"
registration_response="$({
  printf '{"email":"%s","password":"%s","displayName":"Media Diagnostics"}' \
    "$diagnostics_email" "$diagnostics_password"
} | curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data-binary @- \
  "http://127.0.0.1:$application_port/api/v1/auth/register")" \
  || fail 'disposable diagnostics identity could not be registered'
diagnostics_token="$(printf '%s' "$registration_response" \
  | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
[[ "$diagnostics_token" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]] \
  || fail 'registration response did not contain a valid access-token shape'
unset registration_response diagnostics_password

diagnostics_directory="$(mktemp -d "${TMPDIR:-/tmp}/ai-learning-media.XXXXXX")"
diagnostics_stop_file="$diagnostics_directory/stop"
diagnostics_samples_file="$diagnostics_directory/samples.tsv"
export DIAGNOSTICS_WORKLOAD_KIND='media-bandwidth'
export DIAGNOSTICS_APP_PORT="$application_port"
export DIAGNOSTICS_APP_CONTAINER="$app_container"
export DIAGNOSTICS_POSTGRES_CONTAINER="$postgres_container"
export DIAGNOSTICS_REDIS_CONTAINER="$redis_container"
export DIAGNOSTICS_MINIO_CONTAINER="$minio_container"
export DIAGNOSTICS_DATABASE_NAME="$database_name"
export DIAGNOSTICS_DATABASE_USER="$database_user"
export DIAGNOSTICS_BEARER_TOKEN="$diagnostics_token"
export DIAGNOSTICS_APP_IMAGE_ID="$application_image_id"
export DIAGNOSTICS_STOP_FILE="$diagnostics_stop_file"
export DIAGNOSTICS_SAMPLES_FILE="$diagnostics_samples_file"
export DIAGNOSTICS_OUTPUT_FILE="$resource_summary_file"
"$script_directory/collect-resource-diagnostics.sh" &
collector_pid="$!"
unset diagnostics_token DIAGNOSTICS_BEARER_TOKEN
sleep 2
kill -0 "$collector_pid" >/dev/null 2>&1 \
  || fail 'resource collector exited before the workload started'

docker run --rm \
  --name "$k6_container" \
  --network "$network_name" \
  --label ai-learning.performance=media-bandwidth \
  "${k6_user_arguments[@]}" \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --read-only \
  --pids-limit 256 \
  --memory 512m \
  --cpus 2 \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --mount "type=bind,source=$performance_mount_source,target=/workload,readonly" \
  --mount "type=bind,source=$results_mount_source,target=/results" \
  --env BASE_URL="http://$app_container:8080" \
  --env MEDIA_BANDWIDTH_RUN_ID \
  --env MEDIA_BANDWIDTH_PASSWORD \
  --env MEDIA_ETAG \
  --env MEDIA_RANGE_SHA256 \
  "$k6_image" run --quiet /workload/media-bandwidth.js \
  || fail 'k6 workload or a regression threshold failed'

: >"$diagnostics_stop_file"
wait "$collector_pid" || fail 'resource collector failed'
collector_pid=''

[[ -s "$client_summary_file" ]] || fail 'client summary is missing or empty'
[[ -s "$resource_summary_file" ]] || fail 'resource summary is missing or empty'
grep --quiet '"format": "ai-learning-media-bandwidth-v1"' "$client_summary_file" \
  || fail 'client summary format marker is missing'
grep --quiet '"format": "ai-learning-media-bandwidth-resource-v1"' "$resource_summary_file" \
  || fail 'resource summary format marker is missing'
for result_name in \
  checksRate iterations completedResponses transferredBytes droppedIterations \
  requestFailureRate requestDurationP95Ms requestDurationP99Ms; do
  grep --quiet "\"$result_name\":" "$client_summary_file" \
    || fail "client summary result $result_name is missing"
done
for resource_name in \
  applicationImageId maxJvmUsedBytes maxHikariActive maxHikariPending \
  deadlocks keyspaceHits usedMemoryPeakBytes minio; do
  grep --extended-regexp --quiet "\"$resource_name\"[[:space:]]*:" "$resource_summary_file" \
    || fail "resource summary field $resource_name is missing"
done

iterations="$(json_number_from_file "$client_summary_file" iterations)"
completed_responses="$(json_number_from_file "$client_summary_file" completedResponses)"
transferred_bytes="$(json_number_from_file "$client_summary_file" transferredBytes)"
for value in "$iterations" "$completed_responses" "$transferred_bytes"; do
  [[ "$value" =~ ^[0-9]+$ ]] || fail 'client summary counts are missing or non-numeric'
done
[[ "$iterations" == "$completed_responses" ]] \
  || fail "client iterations $iterations do not match valid responses $completed_responses"
expected_transferred_bytes=$((completed_responses * range_size_bytes))
[[ "$transferred_bytes" == "$expected_transferred_bytes" ]] \
  || fail "transferred bytes $transferred_bytes do not match expected $expected_transferred_bytes"

database_counts="$(database_scalar "
  SELECT concat_ws(':',
    (SELECT count(*) FROM users WHERE email LIKE 'media-$resource_prefix-%@example.invalid'),
    (SELECT count(*) FROM enrollments WHERE course_id = '14000000-0000-0000-0000-000000000001'),
    (SELECT count(*) FROM lesson_progress),
    (SELECT count(*) FROM learning_event_outbox)
  );
")"
IFS=: read -r identity_count enrollment_count progress_count outbox_count <<<"$database_counts"
[[ "$identity_count:$enrollment_count" == "$expected_identities:$expected_identities" ]] \
  || fail "identity/enrollment counts are $identity_count:$enrollment_count"
[[ "$progress_count:$outbox_count" == '0:0' ]] \
  || fail "media reads created progress/outbox rows $progress_count:$outbox_count"

final_object_stat="$(run_mc stat --json "media/$media_bucket/$object_key")" \
  || fail 'final MinIO object stat failed'
final_object_size="$(printf '%s' "$final_object_stat" \
  | sed -n 's/.*"size":[[:space:]]*\([0-9][0-9]*\).*/\1/p')"
final_object_etag="$(printf '%s' "$final_object_stat" \
  | sed -n 's/.*"etag":"\([0-9a-f-]*\)".*/\1/p')"
unset final_object_stat
[[ "$final_object_size:$final_object_etag" == "$object_size_bytes:$MEDIA_ETAG" ]] \
  || fail "final object size/ETag changed: $final_object_size:$final_object_etag"
grep --quiet "\"applicationImageId\": \"$application_image_id\"" "$resource_summary_file" \
  || fail 'resource summary is not bound to the exact application image'

for secret_value in \
  "$POSTGRES_PASSWORD" "$REDIS_PASSWORD" "$MINIO_ROOT_PASSWORD" \
  "$JWT_SECRET" "$OPENAI_API_KEY" "$MEDIA_BANDWIDTH_PASSWORD"; do
  if grep --fixed-strings --quiet "$secret_value" \
    "$client_summary_file" "$resource_summary_file"; then
    fail 'a generated secret was retained in a summary'
  fi
done

generated_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
cat >"$pipeline_summary_file" <<EOF
{
  "format": "ai-learning-media-bandwidth-pipeline-v1",
  "generatedAtUtc": "$generated_at",
  "applicationImageId": "$application_image_id",
  "fixture": {"bucket": "$media_bucket", "objectKey": "$object_key", "objectSizeBytes": $object_size_bytes, "rangeSizeBytes": $range_size_bytes, "rangeSha256": "$range_sha256", "etag": "$MEDIA_ETAG"},
  "database": {"identities": $identity_count, "enrollments": $enrollment_count, "progressRows": $progress_count, "outboxRows": $outbox_count},
  "delivery": {"iterations": $iterations, "completedResponses": $completed_responses, "transferredBytes": $transferred_bytes}
}
EOF

[[ -s "$pipeline_summary_file" ]] || fail 'pipeline summary is missing or empty'
grep --quiet '"format": "ai-learning-media-bandwidth-pipeline-v1"' "$pipeline_summary_file" \
  || fail 'pipeline summary format marker is missing'

elapsed=$(( $(date +%s) - started_at ))
printf 'Media bandwidth performance passed in %s seconds; summaries: %s, %s, %s\n' \
  "$elapsed" "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"
