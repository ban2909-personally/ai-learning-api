#!/usr/bin/env bash
set -euo pipefail

postgres_image='postgres:17-bookworm@sha256:051f7b7b3abdd564d5d1bd1e8c4b9c1b6e77087d1dd22020ede611c096a272e0'
redis_image='redis:7.4-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf'
kafka_image='apache/kafka:3.9.1@sha256:4ceccc577f03f51f6af8dbfda55194d0d892f4fa7913ffbded567ce3895622ed'
k6_image='grafana/k6:2.2.0@sha256:9bd01d6941fca969cb61bb57d2da5ee9b385fe2aa8881df3798c196564d6ace6'
app_image="${PERFORMANCE_APP_IMAGE:-ai-learning-api:ci}"
resource_prefix="alp-ws-$(date -u +'%s')-$$"
network_name="$resource_prefix-network"
postgres_container="$resource_prefix-postgres"
redis_container="$resource_prefix-redis"
kafka_container="$resource_prefix-kafka"
app_container="$resource_prefix-api"
k6_container="$resource_prefix-k6"
database_name='ai_learning_notification_websocket_performance'
database_user='performance_operator'
source_topic='ai-learning.performance.notification-websocket.lesson-completed.v1'
notifications_dlt='ai-learning.performance.notification-websocket.dlt.v1'
notifications_group='notification-websocket-performance'
frontend_origin='https://performance.example.invalid'
expected_completions=40
expected_sessions=80
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
export JWT_ISSUER='ai-learning-notification-websocket-performance'
export CORS_ALLOWED_ORIGIN="$frontend_origin"
export KAFKA_BOOTSTRAP_SERVERS="$kafka_container:9092"
export OPENAI_API_KEY='notification-websocket-performance-no-provider-call'
export MINIO_ENDPOINT='http://unused.invalid:9000'
export MINIO_ACCESS_KEY='notification-websocket-performance-unused-access'
export MINIO_SECRET_KEY="$(openssl rand -hex 24)"
export MINIO_MEDIA_BUCKET='lesson-media-notification-websocket-performance'
export NOTIFICATION_WEBSOCKET_PASSWORD="$(openssl rand -hex 24)"
export NOTIFICATION_WEBSOCKET_RUN_ID="$resource_prefix"

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
  printf 'Notification WebSocket performance failed: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [[ -n "$collector_pid" ]] && kill -0 "$collector_pid" >/dev/null 2>&1; then
    kill "$collector_pid" >/dev/null 2>&1 || true
    wait "$collector_pid" >/dev/null 2>&1 || true
  fi
  docker rm --force \
    "$k6_container" "$app_container" "$kafka_container" \
    "$redis_container" "$postgres_container" >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
  unset POSTGRES_PASSWORD REDIS_PASSWORD REDISCLI_AUTH DB_URL DB_USERNAME DB_PASSWORD
  unset REDIS_HOST REDIS_PORT JWT_SECRET JWT_ISSUER CORS_ALLOWED_ORIGIN
  unset KAFKA_BOOTSTRAP_SERVERS OPENAI_API_KEY MINIO_ENDPOINT
  unset MINIO_ACCESS_KEY MINIO_SECRET_KEY MINIO_MEDIA_BUCKET
  unset NOTIFICATION_WEBSOCKET_PASSWORD NOTIFICATION_WEBSOCKET_RUN_ID
  unset DIAGNOSTICS_WORKLOAD_KIND DIAGNOSTICS_APP_PORT DIAGNOSTICS_APP_CONTAINER
  unset DIAGNOSTICS_POSTGRES_CONTAINER DIAGNOSTICS_REDIS_CONTAINER
  unset DIAGNOSTICS_KAFKA_CONTAINER DIAGNOSTICS_DATABASE_NAME
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
    if docker exec --env REDISCLI_AUTH "$redis_container" \
      redis-cli ping 2>/dev/null | grep --quiet '^PONG$'; then
      return 0
    fi
    sleep 2
  done
  docker logs "$redis_container" >&2 || true
  fail 'Redis did not become ready'
}

wait_for_kafka() {
  local attempt
  for attempt in $(seq 1 60); do
    if docker exec "$kafka_container" /opt/kafka/bin/kafka-broker-api-versions.sh \
      --bootstrap-server localhost:9092 >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  docker logs "$kafka_container" >&2 || true
  fail 'Kafka did not become ready'
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

create_topic() {
  local topic="$1"
  docker exec "$kafka_container" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$topic" \
    --partitions 4 --replication-factor 1 >/dev/null \
    || fail "Kafka topic $topic could not be created"
}

database_scalar() {
  local query="$1"
  docker exec "$postgres_container" \
    psql --no-psqlrc --quiet --tuples-only --no-align \
    --username "$database_user" --dbname "$database_name" \
    --command "$query"
}

topic_offset_sum() {
  local topic="$1"
  local attempt
  local output
  local offset
  for attempt in $(seq 1 5); do
    output="$(timeout 15 docker exec "$kafka_container" \
      /opt/kafka/bin/kafka-get-offsets.sh \
      --bootstrap-server localhost:9092 --topic "$topic" 2>/dev/null || true)"
    offset="$(printf '%s\n' "$output" \
      | awk -F: 'NF == 3 {sum += $3; found = 1} END {if (found) print sum + 0}')"
    if [[ "$offset" =~ ^[0-9]+$ ]]; then
      printf '%s' "$offset"
      return 0
    fi
    sleep 1
  done
  return 1
}

consumer_group_lag() {
  local group="$1"
  local attempt
  local output
  local lag
  for attempt in $(seq 1 5); do
    output="$(timeout 15 docker exec "$kafka_container" \
      /opt/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 --describe --group "$group" 2>/dev/null || true)"
    lag="$(printf '%s\n' "$output" \
      | awk -v topic="$source_topic" \
        '$2 == topic && $6 ~ /^[0-9]+$/ {sum += $6; found = 1}
         END {if (found) print sum + 0}')"
    if [[ "$lag" =~ ^[0-9]+$ ]]; then
      printf '%s' "$lag"
      return 0
    fi
    sleep 1
  done
  return 1
}

json_number() {
  local file="$1"
  local key="$2"
  local value
  value="$(sed -n "s/.*\"$key\"[[:space:]]*:[[:space:]]*\([-+0-9.eE]*\).*/\1/p" "$file" | head -n 1)"
  [[ "$value" =~ ^-?[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?$ ]] \
    || fail "JSON field $key is missing or non-numeric in $file"
  printf '%s' "$value"
}

numeric_equals() {
  local actual="$1"
  local expected="$2"
  awk -v actual="$actual" -v expected="$expected" \
    'BEGIN { exit !(actual + 0 == expected + 0) }'
}

trap cleanup EXIT
for command_name in awk curl docker grep head id kill mkdir mktemp openssl sed seq sleep timeout tr; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "required command $command_name is unavailable"
done
docker info >/dev/null 2>&1 || fail 'Docker daemon is unavailable'
docker image inspect "$app_image" >/dev/null 2>&1 \
  || fail "application image $app_image is unavailable; build it before running the profile"
[[ -f "$performance_directory/notification-websocket-seed.sql" ]] \
  || fail 'notification WebSocket seed file is missing'
[[ -f "$performance_directory/notification-websocket.js" ]] \
  || fail 'notification WebSocket k6 workload is missing'
[[ -x "$script_directory/collect-resource-diagnostics.sh" ]] \
  || fail 'resource collector is missing or not executable'

mkdir -p "$results_directory"
client_summary_file="$results_directory/notification-websocket-client-summary.json"
pipeline_summary_file="$results_directory/notification-websocket-pipeline-summary.json"
resource_summary_file="$results_directory/notification-websocket-resource-summary.json"
rm -f -- "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"

docker network create --label ai-learning.performance=notification-websocket "$network_name" >/dev/null

docker run --detach \
  --name "$postgres_container" --network "$network_name" \
  --label ai-learning.performance=notification-websocket \
  --user postgres --cap-drop ALL --security-opt no-new-privileges:true \
  --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,uid=999,gid=999,mode=0700,size=512m \
  --env POSTGRES_DB="$database_name" --env POSTGRES_USER="$database_user" \
  --env POSTGRES_PASSWORD \
  --health-cmd "pg_isready --username $database_user --dbname $database_name" \
  --health-interval 2s --health-timeout 2s --health-retries 20 \
  "$postgres_image" >/dev/null

docker run --detach \
  --name "$redis_container" --network "$network_name" \
  --label ai-learning.performance=notification-websocket \
  --user 999:1000 --cap-drop ALL --security-opt no-new-privileges:true \
  --tmpfs /data:rw,noexec,nosuid,size=64m \
  --tmpfs /tmp:rw,noexec,nosuid,mode=1777,size=1m \
  --env REDIS_PASSWORD --entrypoint /bin/sh "$redis_image" -c '
    umask 077
    printf "save \"\"\nappendonly no\nrequirepass %s\n" "$REDIS_PASSWORD" >/tmp/redis.conf
    exec redis-server /tmp/redis.conf
  ' >/dev/null

docker run --detach \
  --name "$kafka_container" --network "$network_name" \
  --label ai-learning.performance=notification-websocket \
  --user 1000:1000 --read-only --cap-drop ALL \
  --security-opt no-new-privileges:true --pids-limit 256 \
  --memory 768m --cpus 1 \
  --tmpfs /tmp:rw,noexec,nosuid,uid=1000,gid=1000,mode=1770,size=512m \
  --tmpfs /mnt/shared/config:rw,noexec,nosuid,uid=1000,gid=1000,mode=0700,size=16m \
  --tmpfs /opt/kafka/config:rw,noexec,nosuid,uid=1000,gid=1000,mode=0700,size=16m \
  --tmpfs /opt/kafka/logs:rw,noexec,nosuid,uid=1000,gid=1000,mode=0700,size=64m \
  --env CLUSTER_ID='MkU3OEVBNTcwNTJENDM2Qk' \
  --env KAFKA_HEAP_OPTS='-Xms256m -Xmx256m' \
  --env KAFKA_NODE_ID=1 \
  --env KAFKA_PROCESS_ROLES='broker,controller' \
  --env KAFKA_LISTENERS='PLAINTEXT://:9092,CONTROLLER://:9093' \
  --env KAFKA_ADVERTISED_LISTENERS="PLAINTEXT://$kafka_container:9092" \
  --env KAFKA_CONTROLLER_LISTENER_NAMES='CONTROLLER' \
  --env KAFKA_LISTENER_SECURITY_PROTOCOL_MAP='CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT' \
  --env KAFKA_CONTROLLER_QUORUM_VOTERS='1@localhost:9093' \
  --env KAFKA_INTER_BROKER_LISTENER_NAME='PLAINTEXT' \
  --env KAFKA_LOG_DIRS='/tmp/kraft-combined-logs' \
  --env KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  --env KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  --env KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  --env KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  --env KAFKA_AUTO_CREATE_TOPICS_ENABLE=false \
  "$kafka_image" >/dev/null

wait_for_database
wait_for_redis
wait_for_kafka
create_topic "$source_topic"
create_topic "$notifications_dlt"

docker run --detach \
  --name "$app_container" --network "$network_name" \
  --label ai-learning.performance=notification-websocket \
  --publish 127.0.0.1::8080 \
  --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  --pids-limit 512 --memory 1g --cpus 2 \
  --tmpfs /tmp:rw,noexec,nosuid,uid=65532,gid=65532,mode=1700,size=64m \
  --env DB_URL --env DB_USERNAME --env DB_PASSWORD \
  --env REDIS_HOST --env REDIS_PORT --env REDIS_PASSWORD \
  --env JWT_SECRET --env JWT_ISSUER --env CORS_ALLOWED_ORIGIN \
  --env KAFKA_BOOTSTRAP_SERVERS \
  --env OPENAI_API_KEY --env MINIO_ENDPOINT --env MINIO_ACCESS_KEY \
  --env MINIO_SECRET_KEY --env MINIO_MEDIA_BUCKET \
  --env MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics \
  --env SPRING_KAFKA_CONSUMER_PROPERTIES_ALLOW_AUTO_CREATE_TOPICS=false \
  --env LEARNING_EVENTS_KAFKA_ENABLED=true \
  --env LESSON_COMPLETED_TOPIC="$source_topic" \
  --env LEARNING_EVENTS_BATCH_SIZE=10 \
  --env LEARNING_EVENTS_POLL_DELAY=PT1S \
  --env NOTIFICATION_KAFKA_ENABLED=true \
  --env NOTIFICATION_KAFKA_GROUP_ID="$notifications_group" \
  --env NOTIFICATION_KAFKA_DLT="$notifications_dlt" \
  --env ANALYTICS_KAFKA_ENABLED=false \
  "$app_image" >/dev/null

application_port="$(wait_for_application)"

docker exec --interactive "$postgres_container" \
  psql --no-psqlrc --username "$database_user" --dbname "$database_name" \
  <"$performance_directory/notification-websocket-seed.sql" >/dev/null

fixture_counts="$(database_scalar "
  SELECT concat_ws(':',
    (SELECT count(*) FROM courses
      WHERE id = '13000000-0000-0000-0000-000000000001'
        AND slug = 'notification-websocket-performance'
        AND status = 'PUBLISHED' AND price = 0),
    (SELECT count(*) FROM lessons
      WHERE id = '33000000-0000-0000-0000-000000000001'
        AND section_id = '23000000-0000-0000-0000-000000000001')
  );
")"
[[ "$fixture_counts" == '1:1' ]] \
  || fail "fixture validation returned $fixture_counts instead of 1:1"

curl --fail --silent --show-error \
  "http://127.0.0.1:$application_port/api/v1/courses/notification-websocket-performance" \
  >/dev/null || fail 'published notification WebSocket course warm-up failed'

metrics_response="$(curl --silent --show-error --write-out $'\n%{http_code}' \
  "http://127.0.0.1:$application_port/actuator/metrics")" \
  || fail 'unauthenticated metrics security probe failed'
metrics_status="${metrics_response##*$'\n'}"
unset metrics_response
[[ "$metrics_status" == '401' ]] \
  || fail "unauthenticated metrics request returned HTTP $metrics_status instead of 401"

untrusted_websocket_response="$(curl --http1.1 --silent --show-error \
  --write-out $'\n%{http_code}' --max-time 5 \
  --header 'Connection: Upgrade' --header 'Upgrade: websocket' \
  --header 'Sec-WebSocket-Version: 13' \
  --header "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
  --header 'Origin: https://untrusted.example.invalid' \
  "http://127.0.0.1:$application_port/ws/notifications")" \
  || fail 'untrusted WebSocket Origin probe failed'
untrusted_websocket_status="${untrusted_websocket_response##*$'\n'}"
unset untrusted_websocket_response
[[ "$untrusted_websocket_status" == '403' ]] \
  || fail "untrusted WebSocket Origin returned HTTP $untrusted_websocket_status instead of 403"

diagnostics_password="$(openssl rand -hex 24)"
diagnostics_email="diagnostics-$resource_prefix@example.invalid"
registration_response="$({
  printf '{"email":"%s","password":"%s","displayName":"WebSocket Diagnostics"}' \
    "$diagnostics_email" "$diagnostics_password"
} | curl --fail --silent --show-error \
  --header 'Content-Type: application/json' --data-binary @- \
  "http://127.0.0.1:$application_port/api/v1/auth/register")" \
  || fail 'disposable diagnostics identity could not be registered'
diagnostics_token="$(printf '%s' "$registration_response" \
  | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
[[ "$diagnostics_token" =~ ^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$ ]] \
  || fail 'registration response did not contain a valid access-token shape'
unset registration_response diagnostics_password

diagnostics_directory="$(mktemp -d "${TMPDIR:-/tmp}/ai-learning-notification-ws.XXXXXX")"
diagnostics_stop_file="$diagnostics_directory/stop"
diagnostics_samples_file="$diagnostics_directory/samples.tsv"
export DIAGNOSTICS_WORKLOAD_KIND='notification-websocket'
export DIAGNOSTICS_APP_PORT="$application_port"
export DIAGNOSTICS_APP_CONTAINER="$app_container"
export DIAGNOSTICS_POSTGRES_CONTAINER="$postgres_container"
export DIAGNOSTICS_REDIS_CONTAINER="$redis_container"
export DIAGNOSTICS_KAFKA_CONTAINER="$kafka_container"
export DIAGNOSTICS_DATABASE_NAME="$database_name"
export DIAGNOSTICS_DATABASE_USER="$database_user"
export DIAGNOSTICS_BEARER_TOKEN="$diagnostics_token"
export DIAGNOSTICS_APP_IMAGE_ID="$(docker image inspect --format '{{.Id}}' "$app_image")"
export DIAGNOSTICS_STOP_FILE="$diagnostics_stop_file"
export DIAGNOSTICS_SAMPLES_FILE="$diagnostics_samples_file"
export DIAGNOSTICS_OUTPUT_FILE="$resource_summary_file"
"$script_directory/collect-resource-diagnostics.sh" &
collector_pid="$!"
unset diagnostics_token DIAGNOSTICS_BEARER_TOKEN
sleep 2
kill -0 "$collector_pid" >/dev/null 2>&1 \
  || fail 'resource collector exited before the workload started'

if docker run --rm \
  --name "$k6_container" --network "$network_name" \
  --label ai-learning.performance=notification-websocket \
  "${k6_user_arguments[@]}" \
  --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  --pids-limit 256 --memory 512m --cpus 2 \
  --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --mount "type=bind,source=$performance_mount_source,target=/workload,readonly" \
  --mount "type=bind,source=$results_mount_source,target=/results" \
  --env BASE_URL="http://$app_container:8080" \
  --env WS_URL="ws://$app_container:8080" \
  --env FRONTEND_ORIGIN="$frontend_origin" \
  --env NOTIFICATION_WEBSOCKET_RUN_ID --env NOTIFICATION_WEBSOCKET_PASSWORD \
  "$k6_image" run --quiet /workload/notification-websocket.js; then
  :
else
  fail 'k6 WebSocket workload or a regression threshold failed'
fi

[[ -s "$client_summary_file" ]] || fail 'k6 client summary file is missing or empty'
grep --quiet '"format": "ai-learning-notification-websocket-v1"' "$client_summary_file" \
  || fail 'k6 client summary format marker is missing'
for result_name in \
  checksRate droppedIterations httpFailureRate upgradeSuccessRate \
  stompConnected subscriptionsReady completionRequests completionFailureRate \
  messagesReceived messageValidityRate deliveryLatencyP95Ms deliveryLatencyP99Ms \
  unexpectedMessages prematureDisconnects socketTimeouts socketErrors protocolErrors; do
  grep --quiet "\"$result_name\":" "$client_summary_file" \
    || fail "k6 client summary result $result_name is missing"
done

client_completions="$(json_number "$client_summary_file" completionRequests)"
client_sessions="$(json_number "$client_summary_file" messagesReceived)"
numeric_equals "$client_completions" "$expected_completions" \
  || fail "client completion count is $client_completions instead of $expected_completions"
numeric_equals "$client_sessions" "$expected_sessions" \
  || fail "client delivery count is $client_sessions instead of $expected_sessions"

pipeline_state=''
source_offset='unavailable'
notifications_lag='unavailable'
notifications_dlt_offset='unavailable'
drain_started_at="$(date +%s)"
for _ in $(seq 1 90); do
  pipeline_state="$(database_scalar "
    SELECT concat_ws(':',
      (SELECT count(*) FROM lesson_progress progress
        JOIN enrollments enrollment ON enrollment.id = progress.enrollment_id
        WHERE enrollment.course_id = '13000000-0000-0000-0000-000000000001'
          AND progress.completed),
      (SELECT count(*) FROM learning_event_outbox),
      (SELECT count(*) FROM learning_event_outbox WHERE published_at IS NOT NULL),
      (SELECT count(*) FROM learning_event_outbox WHERE published_at IS NULL),
      (SELECT COALESCE(sum(attempts), 0) FROM learning_event_outbox),
      (SELECT count(*) FROM learning_event_outbox
        WHERE locked_by IS NOT NULL OR locked_until IS NOT NULL),
      (SELECT count(*) FROM learning_event_outbox WHERE last_failure_code IS NOT NULL),
      (SELECT count(*) FROM user_notifications),
      (SELECT count(*) FROM learning_completion_facts),
      (SELECT count(*) FROM learning_event_outbox event
        LEFT JOIN user_notifications notification ON notification.id = event.event_id
        WHERE notification.id IS NULL),
      (SELECT count(*) FROM user_notifications notification
        LEFT JOIN learning_event_outbox event ON event.event_id = notification.id
        WHERE event.event_id IS NULL),
      (SELECT count(*) FROM users
        WHERE email LIKE 'notification-ws-$resource_prefix-%@example.invalid'),
      (SELECT count(*) FROM enrollments
        WHERE course_id = '13000000-0000-0000-0000-000000000001')
    );
  ")"
  IFS=: read -r completed_count outbox_count published_count pending_count \
    attempts_count lock_count failure_count notification_count analytics_count \
    outbox_missing_notification notification_missing_outbox identity_count \
    enrollment_count <<<"$pipeline_state"
  if [[ "$completed_count" == "$expected_completions" \
    && "$outbox_count" == "$expected_completions" \
    && "$published_count" == "$expected_completions" \
    && "$pending_count" == '0' \
    && "$attempts_count" == '0' \
    && "$lock_count" == '0' \
    && "$failure_count" == '0' \
    && "$notification_count" == "$expected_completions" \
    && "$analytics_count" == '0' \
    && "$outbox_missing_notification" == '0' \
    && "$notification_missing_outbox" == '0' ]]; then
    break
  fi
  sleep 1
done
drain_seconds="$(( $(date +%s) - drain_started_at ))"

source_offset="$(topic_offset_sum "$source_topic" 2>/dev/null || printf 'unavailable')"
notifications_dlt_offset="$(topic_offset_sum "$notifications_dlt" 2>/dev/null || printf 'unavailable')"
notifications_lag="$(consumer_group_lag "$notifications_group" 2>/dev/null || printf 'unavailable')"

[[ "$completed_count" == "$expected_completions" ]] \
  || fail "completed progress count is $completed_count instead of $expected_completions"
[[ "$outbox_count:$published_count:$pending_count" == "$expected_completions:$expected_completions:0" ]] \
  || fail "outbox total:published:pending is $outbox_count:$published_count:$pending_count"
[[ "$attempts_count:$lock_count:$failure_count" == '0:0:0' ]] \
  || fail "outbox attempts:locks:failure-codes is $attempts_count:$lock_count:$failure_count"
[[ "$notification_count:$analytics_count" == "$expected_completions:0" ]] \
  || fail "notifications:analytics count is $notification_count:$analytics_count"
[[ "$outbox_missing_notification:$notification_missing_outbox" == '0:0' ]] \
  || fail 'event-id reconciliation found missing notification projections'
[[ "$source_offset" == "$expected_completions" ]] \
  || fail "source topic offset is $source_offset instead of $expected_completions"
[[ "$notifications_lag" == '0' ]] \
  || fail "notification consumer lag is $notifications_lag instead of zero"
[[ "$notifications_dlt_offset" == '0' ]] \
  || fail "notification dead-letter offset is $notifications_dlt_offset instead of zero"
[[ "$identity_count:$enrollment_count" == '40:40' ]] \
  || fail "identity:enrollment count is $identity_count:$enrollment_count instead of 40:40"

sleep 3
: >"$diagnostics_stop_file"
wait "$collector_pid" || fail 'resource collector failed'
collector_pid=''

[[ -s "$resource_summary_file" ]] || fail 'resource summary file is missing or empty'
grep --quiet '"format": "ai-learning-notification-websocket-resource-v1"' "$resource_summary_file" \
  || fail 'resource summary format marker is missing'
for resource_name in \
  applicationImageId maxJvmUsedBytes maxHikariActive maxHikariPending \
  maxOutboxPending maxOutboxOldestAgeSeconds maxActiveWebSocketSessions \
  dispatchPublished dispatchFailed notificationsProjected notificationsDuplicate \
  notificationsRejected notificationsDeadLetter realtimeSent realtimeFailed \
  deadlocks keyspaceHits usedMemoryPeakBytes maxPids; do
  grep --extended-regexp --quiet "\"$resource_name\"[[:space:]]*:" "$resource_summary_file" \
    || fail "resource summary field $resource_name is missing"
done

for exact_metric in dispatchPublished notificationsProjected realtimeSent; do
  metric_value="$(json_number "$resource_summary_file" "$exact_metric")"
  numeric_equals "$metric_value" "$expected_completions" \
    || fail "$exact_metric is $metric_value instead of $expected_completions"
done
max_active_sessions="$(json_number "$resource_summary_file" maxActiveWebSocketSessions)"
numeric_equals "$max_active_sessions" "$expected_sessions" \
  || fail "maxActiveWebSocketSessions is $max_active_sessions instead of $expected_sessions"
for zero_metric in \
  dispatchFailed notificationsDuplicate notificationsRejected \
  notificationsDeadLetter realtimeFailed; do
  metric_value="$(json_number "$resource_summary_file" "$zero_metric")"
  numeric_equals "$metric_value" 0 \
    || fail "$zero_metric is $metric_value instead of zero"
done

application_image_id="$(docker image inspect --format '{{.Id}}' "$app_image")"
generated_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
printf '{\n' >"$pipeline_summary_file"
printf '  "format": "ai-learning-notification-websocket-pipeline-v1",\n' >>"$pipeline_summary_file"
printf '  "generatedAtUtc": "%s",\n' "$generated_at" >>"$pipeline_summary_file"
printf '  "applicationImageId": "%s",\n' "$application_image_id" >>"$pipeline_summary_file"
printf '  "expectedCompletions": %s,\n' "$expected_completions" >>"$pipeline_summary_file"
printf '  "expectedSessions": %s,\n' "$expected_sessions" >>"$pipeline_summary_file"
printf '  "drainSeconds": %s,\n' "$drain_seconds" >>"$pipeline_summary_file"
printf '  "database": {"completedProgress": %s, "outboxTotal": %s, "outboxPublished": %s, "outboxPending": %s, "outboxAttempts": %s, "outboxLocks": %s, "outboxFailureCodes": %s, "notifications": %s, "analyticsFacts": %s},\n' \
  "$completed_count" "$outbox_count" "$published_count" "$pending_count" \
  "$attempts_count" "$lock_count" "$failure_count" "$notification_count" \
  "$analytics_count" >>"$pipeline_summary_file"
printf '  "eventIdReconciliation": {"outboxMissingNotification": %s, "notificationMissingOutbox": %s},\n' \
  "$outbox_missing_notification" "$notification_missing_outbox" \
  >>"$pipeline_summary_file"
printf '  "kafka": {"sourceTopicOffset": %s, "notificationsConsumerLag": %s, "notificationsDeadLetterOffset": %s},\n' \
  "$source_offset" "$notifications_lag" "$notifications_dlt_offset" \
  >>"$pipeline_summary_file"
printf '  "realtime": {"clientMessages": %s, "serverSendInvocations": %s}\n' \
  "$client_sessions" "$expected_completions" >>"$pipeline_summary_file"
printf '}\n' >>"$pipeline_summary_file"
test -s "$pipeline_summary_file" || fail 'pipeline summary file is missing or empty'

finished_at="$(date +%s)"
printf 'Notification WebSocket performance passed in %s seconds; summaries: %s, %s, %s\n' \
  "$((finished_at - started_at))" \
  "$client_summary_file" "$pipeline_summary_file" "$resource_summary_file"
