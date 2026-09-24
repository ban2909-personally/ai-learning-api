#!/usr/bin/env bash
set -euo pipefail

postgres_image='postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0'
redis_image='redis:7.4-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf'
k6_image='grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6'
provider_image='python:3.13-alpine@sha256:79e7a9b9ff1cbceff819f856fb374477792a5967759d94df266de7b7b4120e6f'
app_image="${PERFORMANCE_APP_IMAGE:-ai-learning-api:ci}"
resource_prefix="alp-mentor-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
postgres_container="$resource_prefix-postgres"
redis_container="$resource_prefix-redis"
provider_container="$resource_prefix-provider"
app_container="$resource_prefix-api"
k6_container="$resource_prefix-k6"
database_name='ai_learning_mentor_performance'
database_user='performance_operator'
expected_identities=40
provider_input_tokens=128
provider_output_tokens=64
provider_max_output_tokens=192
provider_delay_ms=500
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
export JWT_ISSUER='ai-learning-mentor-performance'
export CORS_ALLOWED_ORIGIN='https://performance.example.invalid'
export KAFKA_BOOTSTRAP_SERVERS='unused.invalid:9092'
export PROVIDER_AUTH_TOKEN="$(openssl rand -hex 32)"
export PROVIDER_MODEL='mentor-performance-stub'
export PROVIDER_DELAY_MS="$provider_delay_ms"
export PROVIDER_INPUT_TOKENS="$provider_input_tokens"
export PROVIDER_OUTPUT_TOKENS="$provider_output_tokens"
export PROVIDER_MAX_OUTPUT_TOKENS="$provider_max_output_tokens"
export OPENAI_BASE_URL="http://$provider_container:8080/v1/"
export OPENAI_API_KEY="$PROVIDER_AUTH_TOKEN"
export OPENAI_MODEL="$PROVIDER_MODEL"
export OPENAI_MAX_OUTPUT_TOKENS="$PROVIDER_MAX_OUTPUT_TOKENS"
export OPENAI_CONNECT_TIMEOUT='PT2S'
export OPENAI_READ_TIMEOUT='PT5S'
export OPENAI_CALL_TIMEOUT='PT6S'
export MENTOR_STREAM_TIMEOUT='PT8S'
export MENTOR_QUOTA_REQUESTS='20'
export MINIO_ENDPOINT='http://unused.invalid:9000'
export MINIO_ACCESS_KEY='mentor-performance-unused-access'
export MINIO_SECRET_KEY="$(openssl rand -hex 24)"
export MINIO_MEDIA_BUCKET='lesson-media-mentor-performance'
export AI_MENTOR_PASSWORD="$(openssl rand -hex 24)"
export AI_MENTOR_RUN_ID="$resource_prefix"

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
  printf 'AI Mentor performance failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [[ -n "$collector_pid" ]] && kill -0 "$collector_pid" >/dev/null 2>&1; then
    kill "$collector_pid" >/dev/null 2>&1 || true
    wait "$collector_pid" >/dev/null 2>&1 || true
  fi
  docker rm --force \
    "$k6_container" "$app_container" "$provider_container" \
    "$redis_container" "$postgres_container" \
    >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
  unset POSTGRES_PASSWORD REDIS_PASSWORD REDISCLI_AUTH DB_URL DB_USERNAME DB_PASSWORD
  unset REDIS_HOST REDIS_PORT JWT_SECRET JWT_ISSUER CORS_ALLOWED_ORIGIN
  unset KAFKA_BOOTSTRAP_SERVERS PROVIDER_AUTH_TOKEN PROVIDER_MODEL PROVIDER_DELAY_MS
  unset PROVIDER_INPUT_TOKENS PROVIDER_OUTPUT_TOKENS PROVIDER_MAX_OUTPUT_TOKENS
  unset OPENAI_BASE_URL OPENAI_API_KEY OPENAI_MODEL OPENAI_MAX_OUTPUT_TOKENS
  unset OPENAI_CONNECT_TIMEOUT OPENAI_READ_TIMEOUT OPENAI_CALL_TIMEOUT
  unset MENTOR_STREAM_TIMEOUT MENTOR_QUOTA_REQUESTS MINIO_ENDPOINT
  unset MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_MEDIA_BUCKET
  unset AI_MENTOR_PASSWORD AI_MENTOR_RUN_ID
  unset DIAGNOSTICS_WORKLOAD_KIND DIAGNOSTICS_APP_PORT DIAGNOSTICS_APP_CONTAINER
  unset DIAGNOSTICS_POSTGRES_CONTAINER DIAGNOSTICS_REDIS_CONTAINER
  unset DIAGNOSTICS_PROVIDER_CONTAINER DIAGNOSTICS_DATABASE_NAME
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

provider_request() {
  local path="$1"
  docker exec "$provider_container" python -c '
import json
import os
import sys
import urllib.request
request = urllib.request.Request(
    "http://127.0.0.1:8080" + sys.argv[1],
    headers={"Authorization": "Bearer " + os.environ["PROVIDER_AUTH_TOKEN"]},
)
with urllib.request.urlopen(request, timeout=2) as response:
    print(json.dumps(json.load(response), separators=(",", ":")))
' "$path"
}

wait_for_provider() {
  local attempt
  for attempt in $(seq 1 30); do
    if provider_request '/health' 2>/dev/null | grep --quiet '"status":"UP"'; then
      return 0
    fi
    sleep 1
  done
  docker logs "$provider_container" >&2 || true
  fail 'provider simulator did not become ready'
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

json_number_from_text() {
  local text="$1"
  local key="$2"
  printf '%s' "$text" \
    | sed -n "s/.*\"$key\":[[:space:]]*\([0-9][0-9]*\).*/\1/p"
}

trap cleanup EXIT
for command_name in awk curl docker grep head id kill mkdir mktemp openssl sed seq sleep tr; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "required command $command_name is unavailable"
done
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
docker image inspect "$app_image" >/dev/null 2>&1 \
  || fail "application image $app_image is unavailable; build it before running the profile"
[[ -f "$performance_directory/ai-mentor-seed.sql" ]] \
  || fail 'AI Mentor seed file is missing'
[[ -f "$performance_directory/ai-mentor-provider.py" ]] \
  || fail 'AI Mentor provider simulator is missing'
[[ -f "$performance_directory/ai-mentor.js" ]] \
  || fail 'AI Mentor k6 workload is missing'
[[ -x "$script_directory/collect-resource-diagnostics.sh" ]] \
  || fail 'resource collector is missing or not executable'

mkdir -p "$results_directory"
client_summary_file="$results_directory/ai-mentor-client-summary.json"
pipeline_summary_file="$results_directory/ai-mentor-pipeline-summary.json"
resource_summary_file="$results_directory/ai-mentor-resource-summary.json"
rm -f -- "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"

docker network create \
  --label ai-learning.performance=ai-mentor \
  "$network_name" >/dev/null

docker run --detach \
  --name "$postgres_container" \
  --network "$network_name" \
  --label ai-learning.performance=ai-mentor \
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
  --label ai-learning.performance=ai-mentor \
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
  --name "$provider_container" \
  --network "$network_name" \
  --label ai-learning.performance=ai-mentor \
  --user 65532:65532 \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --read-only \
  --pids-limit 128 \
  --memory 128m \
  --cpus 1 \
  --tmpfs /tmp:rw,noexec,nosuid,uid=65532,gid=65532,mode=1700,size=16m \
  --mount "type=bind,source=$performance_mount_source,target=/workload,readonly" \
  --env PROVIDER_AUTH_TOKEN \
  --env PROVIDER_MODEL \
  --env PROVIDER_DELAY_MS \
  --env PROVIDER_INPUT_TOKENS \
  --env PROVIDER_OUTPUT_TOKENS \
  --env PROVIDER_MAX_OUTPUT_TOKENS \
  "$provider_image" python /workload/ai-mentor-provider.py >/dev/null

wait_for_database
wait_for_redis
wait_for_provider

docker run --detach \
  --name "$app_container" \
  --network "$network_name" \
  --label ai-learning.performance=ai-mentor \
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
  --env OPENAI_BASE_URL \
  --env OPENAI_API_KEY \
  --env OPENAI_MODEL \
  --env OPENAI_MAX_OUTPUT_TOKENS \
  --env OPENAI_CONNECT_TIMEOUT \
  --env OPENAI_READ_TIMEOUT \
  --env OPENAI_CALL_TIMEOUT \
  --env MENTOR_STREAM_TIMEOUT \
  --env MENTOR_QUOTA_REQUESTS \
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
  <"$performance_directory/ai-mentor-seed.sql" >/dev/null

fixture_counts="$(database_scalar "
  SELECT concat_ws(':',
    (SELECT count(*) FROM courses WHERE slug = 'ai-mentor-performance' AND status = 'PUBLISHED' AND price = 0),
    (SELECT count(*) FROM lessons WHERE id = '32000000-0000-0000-0000-000000000001' AND preview = FALSE)
  );
")"
[[ "$fixture_counts" == '1:1' ]] \
  || fail "fixture validation returned $fixture_counts instead of 1:1"

curl --fail --silent --show-error \
  "http://127.0.0.1:$application_port/api/v1/courses/ai-mentor-performance" \
  >/dev/null || fail 'published mentor course warm-up failed'

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
  printf '{"email":"%s","password":"%s","displayName":"Mentor Diagnostics"}' \
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

diagnostics_directory="$(mktemp -d "${TMPDIR:-/tmp}/ai-learning-mentor.XXXXXX")"
diagnostics_stop_file="$diagnostics_directory/stop"
diagnostics_samples_file="$diagnostics_directory/samples.tsv"
export DIAGNOSTICS_WORKLOAD_KIND='ai-mentor'
export DIAGNOSTICS_APP_PORT="$application_port"
export DIAGNOSTICS_APP_CONTAINER="$app_container"
export DIAGNOSTICS_POSTGRES_CONTAINER="$postgres_container"
export DIAGNOSTICS_REDIS_CONTAINER="$redis_container"
export DIAGNOSTICS_PROVIDER_CONTAINER="$provider_container"
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
  --label ai-learning.performance=ai-mentor \
  "${k6_user_arguments[@]}" \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --mount "type=bind,source=$performance_mount_source,target=/workload,readonly" \
  --mount "type=bind,source=$results_mount_source,target=/results" \
  --env BASE_URL="http://$app_container:8080" \
  --env AI_MENTOR_RUN_ID \
  --env AI_MENTOR_PASSWORD \
  "$k6_image" run --quiet /workload/ai-mentor.js \
  || fail 'k6 workload or a regression threshold failed'

: >"$diagnostics_stop_file"
wait "$collector_pid" || fail 'resource collector failed'
collector_pid=''

[[ -s "$client_summary_file" ]] || fail 'client summary is missing or empty'
[[ -s "$resource_summary_file" ]] || fail 'resource summary is missing or empty'
grep --quiet '"format": "ai-learning-ai-mentor-performance-v1"' "$client_summary_file" \
  || fail 'client summary format marker is missing'
grep --quiet '"format": "ai-learning-ai-mentor-resource-v1"' "$resource_summary_file" \
  || fail 'resource summary format marker is missing'
for result_name in \
  checksRate iterations completedTurns droppedIterations requestFailureRate \
  requestDurationP95Ms requestDurationP99Ms; do
  grep --quiet "\"$result_name\":" "$client_summary_file" \
    || fail "client summary result $result_name is missing"
done
for resource_name in \
  applicationImageId maxJvmUsedBytes maxHikariActive maxHikariPending \
  mentorTurnsTotal mentorAccepted mentorCompleted mentorUnexpectedOutcomes \
  deadlocks keyspaceHits usedMemoryPeakBytes provider; do
  grep --extended-regexp --quiet "\"$resource_name\"[[:space:]]*:" "$resource_summary_file" \
    || fail "resource summary field $resource_name is missing"
done

iterations="$(json_number_from_file "$client_summary_file" iterations)"
completed_turns="$(json_number_from_file "$client_summary_file" completedTurns)"
[[ "$iterations" =~ ^[0-9]+$ && "$completed_turns" =~ ^[0-9]+$ ]] \
  || fail 'client summary iteration counts are missing or non-numeric'
[[ "$iterations" == "$completed_turns" ]] \
  || fail "client iterations $iterations do not match completed turns $completed_turns"

provider_stats="$(provider_request '/stats')" \
  || fail 'provider stats could not be read'
provider_requests="$(json_number_from_text "$provider_stats" requests)"
provider_completed="$(json_number_from_text "$provider_stats" completed)"
provider_rejected="$(json_number_from_text "$provider_stats" rejected)"
provider_failed="$(json_number_from_text "$provider_stats" failed)"
provider_active="$(json_number_from_text "$provider_stats" active)"
provider_max_concurrent="$(json_number_from_text "$provider_stats" maxConcurrent)"
for value in \
  "$provider_requests" "$provider_completed" "$provider_rejected" \
  "$provider_failed" "$provider_active" "$provider_max_concurrent"; do
  [[ "$value" =~ ^[0-9]+$ ]] || fail 'provider stats contain a missing or non-numeric value'
done
[[ "$provider_requests" == "$completed_turns" ]] \
  || fail "provider requests $provider_requests do not match completed turns $completed_turns"
[[ "$provider_completed" == "$completed_turns" ]] \
  || fail "provider completions $provider_completed do not match completed turns $completed_turns"
[[ "$provider_rejected:$provider_failed:$provider_active" == '0:0:0' ]] \
  || fail "provider rejected/failed/active counts are $provider_rejected:$provider_failed:$provider_active"
((provider_max_concurrent >= 2 && provider_max_concurrent <= 32)) \
  || fail "provider maximum concurrency $provider_max_concurrent is outside 2..32"

database_counts="$(database_scalar "
  SELECT concat_ws(':',
    (SELECT count(*) FROM users WHERE email LIKE 'mentor-$resource_prefix-%@example.invalid'),
    (SELECT count(*) FROM enrollments WHERE course_id = '12000000-0000-0000-0000-000000000001'),
    (SELECT count(*) FROM mentor_conversations WHERE course_id = '12000000-0000-0000-0000-000000000001'),
    (SELECT count(*) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'USER'),
    (SELECT count(*) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT'),
    (SELECT count(*) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT' AND (message.provider_model IS NULL OR message.input_tokens IS NULL OR message.output_tokens IS NULL)),
    (SELECT coalesce(sum(message.input_tokens), 0) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT'),
    (SELECT coalesce(sum(message.output_tokens), 0) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT'),
    (SELECT coalesce(max(message.output_tokens), 0) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT'),
    (SELECT count(*) FROM mentor_messages message JOIN mentor_conversations conversation ON conversation.id = message.conversation_id WHERE conversation.course_id = '12000000-0000-0000-0000-000000000001' AND message.role = 'ASSISTANT' AND message.provider_model <> 'mentor-performance-stub')
  );
")"
IFS=: read -r identity_count enrollment_count conversation_count user_messages \
  assistant_messages incomplete_usage input_tokens output_tokens max_output_tokens \
  unexpected_models <<<"$database_counts"
expected_input_tokens=$((completed_turns * provider_input_tokens))
expected_output_tokens=$((completed_turns * provider_output_tokens))
[[ "$identity_count:$enrollment_count:$conversation_count" == \
  "$expected_identities:$expected_identities:$expected_identities" ]] \
  || fail "identity/enrollment/conversation counts are $identity_count:$enrollment_count:$conversation_count"
[[ "$user_messages:$assistant_messages:$incomplete_usage" == \
  "$completed_turns:$completed_turns:0" ]] \
  || fail "user/assistant/incomplete usage counts are $user_messages:$assistant_messages:$incomplete_usage"
[[ "$input_tokens:$output_tokens:$max_output_tokens:$unexpected_models" == \
  "$expected_input_tokens:$expected_output_tokens:$provider_output_tokens:0" ]] \
  || fail "persisted token/model evidence is $input_tokens:$output_tokens:$max_output_tokens:$unexpected_models"

quota_counts="$(docker exec --env REDISCLI_AUTH "$redis_container" redis-cli --no-auth-warning --raw EVAL '
  local keys = redis.call("KEYS", ARGV[1])
  local total = 0
  local maximum = 0
  for _, key in ipairs(keys) do
    local value = tonumber(redis.call("GET", key))
    total = total + value
    if value > maximum then maximum = value end
  end
  return {#keys, total, maximum}
' 0 'mentor:quota:v1:user:*')" || fail 'Redis quota evidence could not be read'
quota_counts="$(printf '%s\n' "$quota_counts" | awk 'NF { values[++count] = $1 } END { print values[1] ":" values[2] ":" values[3] }')"
IFS=: read -r quota_keys quota_total quota_max <<<"$quota_counts"
[[ "$quota_keys:$quota_total" == "$expected_identities:$completed_turns" ]] \
  || fail "quota key/consumption counts are $quota_keys:$quota_total"
((quota_max <= MENTOR_QUOTA_REQUESTS)) \
  || fail "maximum per-user quota consumption $quota_max exceeds $MENTOR_QUOTA_REQUESTS"

mentor_accepted="$(json_number_from_file "$resource_summary_file" mentorAccepted)"
mentor_completed="$(json_number_from_file "$resource_summary_file" mentorCompleted)"
mentor_unexpected="$(json_number_from_file "$resource_summary_file" mentorUnexpectedOutcomes)"
[[ "$mentor_accepted:$mentor_completed:$mentor_unexpected" == \
  "$completed_turns:$completed_turns:0" ]] \
  || fail "application mentor counters are $mentor_accepted:$mentor_completed:$mentor_unexpected"
grep --quiet "\"applicationImageId\": \"$application_image_id\"" "$resource_summary_file" \
  || fail 'resource summary is not bound to the exact application image'

generated_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
cat >"$pipeline_summary_file" <<EOF
{
  "format": "ai-learning-ai-mentor-pipeline-v1",
  "generatedAtUtc": "$generated_at",
  "applicationImageId": "$application_image_id",
  "completedTurns": $completed_turns,
  "provider": {"requests": $provider_requests, "completed": $provider_completed, "rejected": $provider_rejected, "failed": $provider_failed, "active": $provider_active, "maxConcurrent": $provider_max_concurrent, "delayMs": $provider_delay_ms},
  "database": {"identities": $identity_count, "enrollments": $enrollment_count, "conversations": $conversation_count, "userMessages": $user_messages, "assistantMessages": $assistant_messages, "incompleteUsageRows": $incomplete_usage, "inputTokens": $input_tokens, "outputTokens": $output_tokens, "maxOutputTokens": $max_output_tokens, "unexpectedModels": $unexpected_models},
  "quota": {"keys": $quota_keys, "consumed": $quota_total, "maxPerIdentity": $quota_max, "configuredPerIdentity": $MENTOR_QUOTA_REQUESTS}
}
EOF

[[ -s "$pipeline_summary_file" ]] || fail 'pipeline summary is missing or empty'
grep --quiet '"format": "ai-learning-ai-mentor-pipeline-v1"' "$pipeline_summary_file" \
  || fail 'pipeline summary format marker is missing'

elapsed=$(( $(date +%s) - started_at ))
printf 'AI Mentor performance passed in %s seconds; summaries: %s, %s, %s\n' \
  "$elapsed" "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"
