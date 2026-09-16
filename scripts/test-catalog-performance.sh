#!/usr/bin/env bash
set -euo pipefail

postgres_image='postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0'
redis_image='redis:7.4-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf'
k6_image='grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6'
app_image="${PERFORMANCE_APP_IMAGE:-ai-learning-api:ci}"
capture_resources="${PERFORMANCE_CAPTURE_RESOURCES:-false}"
resource_prefix="ai-learning-performance-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
postgres_container="$resource_prefix-postgres"
redis_container="$resource_prefix-redis"
app_container="$resource_prefix-api"
k6_container="$resource_prefix-k6"
database_name='ai_learning_performance'
database_user='performance_operator'
started_at="$(date +%s)"
collector_pid=''
diagnostics_directory=''
diagnostics_stop_file=''
diagnostics_samples_file=''

export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
export REDIS_PASSWORD="$(openssl rand -hex 24)"
export REDISCLI_AUTH="$REDIS_PASSWORD"
export DB_URL="jdbc:postgresql://$postgres_container:5432/$database_name"
export DB_USERNAME="$database_user"
export DB_PASSWORD="$POSTGRES_PASSWORD"
export REDIS_HOST="$redis_container"
export REDIS_PORT='6379'
export JWT_SECRET="$(openssl rand -base64 48 | tr -d '\r\n')"
export JWT_ISSUER='ai-learning-performance-baseline'
export CORS_ALLOWED_ORIGIN='https://performance.example.invalid'
export KAFKA_BOOTSTRAP_SERVERS='unused.invalid:9092'
export OPENAI_API_KEY='performance-baseline-no-provider-call'
export MINIO_ENDPOINT='http://unused.invalid:9000'
export MINIO_ACCESS_KEY='performance-baseline-unused-access'
export MINIO_SECRET_KEY="$(openssl rand -hex 24)"
export MINIO_MEDIA_BUCKET='lesson-media-performance'

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
  printf 'Catalog performance baseline failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [[ -n "$collector_pid" ]] && kill -0 "$collector_pid" >/dev/null 2>&1; then
    kill "$collector_pid" >/dev/null 2>&1 || true
    wait "$collector_pid" >/dev/null 2>&1 || true
  fi
  docker rm --force \
    "$k6_container" "$app_container" "$redis_container" "$postgres_container" \
    >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
  unset POSTGRES_PASSWORD REDIS_PASSWORD REDISCLI_AUTH DB_URL DB_USERNAME DB_PASSWORD
  unset REDIS_HOST REDIS_PORT JWT_SECRET JWT_ISSUER CORS_ALLOWED_ORIGIN
  unset KAFKA_BOOTSTRAP_SERVERS OPENAI_API_KEY MINIO_ENDPOINT
  unset MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_MEDIA_BUCKET
  unset DIAGNOSTICS_APP_PORT DIAGNOSTICS_APP_CONTAINER
  unset DIAGNOSTICS_POSTGRES_CONTAINER DIAGNOSTICS_REDIS_CONTAINER
  unset DIAGNOSTICS_DATABASE_NAME DIAGNOSTICS_DATABASE_USER
  unset DIAGNOSTICS_BEARER_TOKEN DIAGNOSTICS_APP_IMAGE_ID DIAGNOSTICS_STOP_FILE
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

trap cleanup EXIT
for command_name in curl docker grep id mkdir openssl seq tr; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "required command $command_name is unavailable"
done
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
docker image inspect "$app_image" >/dev/null 2>&1 \
  || fail "application image $app_image is unavailable; build it before running the baseline"
[[ "$capture_resources" == 'true' || "$capture_resources" == 'false' ]] \
  || fail 'PERFORMANCE_CAPTURE_RESOURCES must be true or false'
[[ -f "$performance_directory/catalog-seed.sql" ]] || fail 'catalog seed file is missing'
[[ -f "$performance_directory/catalog-read.js" ]] || fail 'k6 workload file is missing'
if [[ "$capture_resources" == 'true' ]]; then
  for command_name in awk kill mktemp sed sleep; do
    command -v "$command_name" >/dev/null 2>&1 \
      || fail "required diagnostics command $command_name is unavailable"
  done
  [[ -x "$script_directory/collect-catalog-resource-diagnostics.sh" ]] \
    || fail 'catalog resource collector is missing or not executable'
  [[ -x "$script_directory/collect-resource-diagnostics.sh" ]] \
    || fail 'shared resource collector is missing or not executable'
fi

mkdir -p "$results_directory"
rm -f -- \
  "$results_directory/catalog-performance-summary.json" \
  "$results_directory/catalog-resource-summary.json"

docker network create --label ai-learning.performance=catalog "$network_name" >/dev/null

docker run --detach \
  --name "$postgres_container" \
  --network "$network_name" \
  --label ai-learning.performance=catalog \
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
  --label ai-learning.performance=catalog \
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
  ' \
  >/dev/null

wait_for_database
wait_for_redis

application_diagnostics_arguments=()
if [[ "$capture_resources" == 'true' ]]; then
  application_diagnostics_arguments+=(
    --env MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics
  )
fi

docker run --detach \
  --name "$app_container" \
  --network "$network_name" \
  --label ai-learning.performance=catalog \
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
  "${application_diagnostics_arguments[@]}" \
  "$app_image" >/dev/null

application_port="$(wait_for_application)"

docker exec --interactive "$postgres_container" \
  psql --no-psqlrc --username "$database_user" --dbname "$database_name" \
  <"$performance_directory/catalog-seed.sql" >/dev/null

seeded_count="$(
  docker exec "$postgres_container" \
    psql --no-psqlrc --quiet --tuples-only --no-align \
    --username "$database_user" --dbname "$database_name" \
    --command "SELECT count(*) FROM courses WHERE status = 'PUBLISHED'"
)"
[[ "$seeded_count" == '5000' ]] || fail "seeded course count is $seeded_count instead of 5000"

curl --fail --silent --show-error \
  "http://127.0.0.1:$application_port/api/v1/courses?size=12" \
  >/dev/null || fail 'catalog warm-up request failed'

if [[ "$capture_resources" == 'true' ]]; then
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
    printf '{"email":"%s","password":"%s","displayName":"Performance Diagnostics"}' \
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

  diagnostics_directory="$(mktemp -d "${TMPDIR:-/tmp}/ai-learning-performance.XXXXXX")"
  diagnostics_stop_file="$diagnostics_directory/stop"
  diagnostics_samples_file="$diagnostics_directory/samples.tsv"
  export DIAGNOSTICS_APP_PORT="$application_port"
  export DIAGNOSTICS_APP_CONTAINER="$app_container"
  export DIAGNOSTICS_POSTGRES_CONTAINER="$postgres_container"
  export DIAGNOSTICS_REDIS_CONTAINER="$redis_container"
  export DIAGNOSTICS_DATABASE_NAME="$database_name"
  export DIAGNOSTICS_DATABASE_USER="$database_user"
  export DIAGNOSTICS_BEARER_TOKEN="$diagnostics_token"
  export DIAGNOSTICS_APP_IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$app_image")"
  export DIAGNOSTICS_STOP_FILE="$diagnostics_stop_file"
  export DIAGNOSTICS_SAMPLES_FILE="$diagnostics_samples_file"
  export DIAGNOSTICS_OUTPUT_FILE="$results_directory/catalog-resource-summary.json"
  "$script_directory/collect-catalog-resource-diagnostics.sh" &
  collector_pid="$!"
  unset diagnostics_token DIAGNOSTICS_BEARER_TOKEN
  sleep 2
  kill -0 "$collector_pid" >/dev/null 2>&1 \
    || fail 'catalog resource collector exited before the workload started'
fi

docker run --rm \
  --name "$k6_container" \
  --network "$network_name" \
  --label ai-learning.performance=catalog \
  "${k6_user_arguments[@]}" \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --mount "type=bind,source=$performance_mount_source,target=/workload,readonly" \
  --mount "type=bind,source=$results_mount_source,target=/results" \
  --env BASE_URL="http://$app_container:8080" \
  "$k6_image" run --quiet /workload/catalog-read.js \
  || fail 'k6 workload or a regression threshold failed'

if [[ "$capture_resources" == 'true' ]]; then
  : >"$diagnostics_stop_file"
  wait "$collector_pid" || fail 'catalog resource collector failed'
  collector_pid=''
  resource_summary_file="$results_directory/catalog-resource-summary.json"
  [[ -s "$resource_summary_file" ]] || fail 'resource summary file is missing or empty'
  grep --quiet '"format": "ai-learning-catalog-resource-v1"' "$resource_summary_file" \
    || fail 'resource summary format marker is missing'
  for resource_name in \
    maxJvmUsedBytes \
    maxHikariActive \
    maxHikariPending \
    catalogCacheHits \
    catalogCacheFailures \
    deadlocks \
    keyspaceHits \
    usedMemoryPeakBytes; do
    grep --extended-regexp --quiet "\"$resource_name\"[[:space:]]*:" "$resource_summary_file" \
      || fail "resource summary field $resource_name is missing"
  done
fi

summary_file="$results_directory/catalog-performance-summary.json"
[[ -s "$summary_file" ]] || fail 'k6 summary file is missing or empty'
grep --quiet '"format": "ai-learning-catalog-performance-v1"' "$summary_file" \
  || fail 'k6 summary format marker is missing'
for result_name in \
  checksRate \
  iterations \
  droppedIterations \
  requestFailureRate \
  requestDurationP95Ms \
  requestDurationP99Ms; do
  grep --quiet "\"$result_name\":" "$summary_file" \
    || fail "k6 summary result $result_name is missing"
done

finished_at="$(date +%s)"
printf 'Catalog performance baseline passed in %s seconds; summary: %s\n' \
  "$((finished_at - started_at))" "$summary_file"
