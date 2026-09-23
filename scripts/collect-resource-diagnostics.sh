#!/usr/bin/env bash
set -euo pipefail

required_value() {
  local name="$1"
  local value="${!name:-}"

  [[ -n "$value" ]] || {
    printf 'Resource diagnostics failed: required environment variable %s is missing\n' "$name" >&2
    exit 1
  }
  printf '%s' "$value"
}

workload_kind="$(required_value DIAGNOSTICS_WORKLOAD_KIND)"
app_port="$(required_value DIAGNOSTICS_APP_PORT)"
app_container="$(required_value DIAGNOSTICS_APP_CONTAINER)"
postgres_container="$(required_value DIAGNOSTICS_POSTGRES_CONTAINER)"
redis_container="$(required_value DIAGNOSTICS_REDIS_CONTAINER)"
kafka_container=''
database_name="$(required_value DIAGNOSTICS_DATABASE_NAME)"
database_user="$(required_value DIAGNOSTICS_DATABASE_USER)"
bearer_token="$(required_value DIAGNOSTICS_BEARER_TOKEN)"
application_image_id="$(required_value DIAGNOSTICS_APP_IMAGE_ID)"
stop_file="$(required_value DIAGNOSTICS_STOP_FILE)"
samples_file="$(required_value DIAGNOSTICS_SAMPLES_FILE)"
output_file="$(required_value DIAGNOSTICS_OUTPUT_FILE)"
minimum_samples=10

fail() {
  printf 'Resource diagnostics failed: %s\n' "$1" >&2
  exit 1
}

is_number() {
  [[ "$1" =~ ^-?[0-9]+([.][0-9]+)?([eE][+-]?[0-9]+)?$ ]]
}

metric_value() {
  local metric="$1"
  local tag="${2:-}"
  local response
  local value
  local url="http://127.0.0.1:$app_port/actuator/metrics/$metric"

  [[ -z "$tag" ]] || url="$url?tag=$tag"
  response="$({
    printf 'header = "Authorization: Bearer %s"\n' "$bearer_token"
  } | curl --config - --fail --silent --show-error --max-time 3 "$url")" \
    || fail "metric $metric could not be read"
  value="$(printf '%s' "$response" \
    | sed -n 's/.*"measurements":\[[^]]*"value":\([-+0-9.eE]*\)[^]]*\].*/\1/p')"
  is_number "$value" || fail "metric $metric is missing a numeric measurement"
  printf '%s' "$value"
}

container_stats_from_snapshot() {
  local snapshot="$1"
  local container="$2"
  local values
  local cpu
  local memory
  local pids

  values="$(printf '%s\n' "$snapshot" \
    | awk -F '\t' -v container="$container" \
      '$1 == container {print $2 "\t" $3 "\t" $4; exit}')"
  [[ -n "$values" ]] || fail "Docker stats snapshot is missing $container"
  IFS=$'\t' read -r cpu memory pids <<<"$values"
  cpu="${cpu%%%}"
  memory="${memory%%%}"
  is_number "$cpu" || fail "CPU sample for $container is not numeric"
  is_number "$memory" || fail "memory sample for $container is not numeric"
  [[ "$pids" =~ ^[0-9]+$ ]] || fail "PID sample for $container is not numeric"
  printf '%s\t%s\t%s' "$cpu" "$memory" "$pids"
}

redis_info_value() {
  local section="$1"
  local key="$2"
  local value

  value="$(docker exec --env REDISCLI_AUTH "$redis_container" \
    redis-cli --no-auth-warning --raw INFO "$section" \
    | awk -F: -v key="$key" '$1 == key {gsub(/\r/, "", $2); print $2; exit}')"
  [[ "$value" =~ ^[0-9]+$ ]] || fail "Redis $key is missing or non-numeric"
  printf '%s' "$value"
}

for command_name in awk curl date docker sed; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "required command $command_name is unavailable"
done
[[ "$workload_kind" == 'catalog' \
  || "$workload_kind" == 'authenticated-learning' \
  || "$workload_kind" == 'learning-event' \
  || "$workload_kind" == 'notification-websocket' ]] \
  || fail 'DIAGNOSTICS_WORKLOAD_KIND must be catalog, authenticated-learning, learning-event, or notification-websocket'
[[ -n "${REDISCLI_AUTH:-}" ]] || fail 'REDISCLI_AUTH is missing'
[[ "$application_image_id" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail 'DIAGNOSTICS_APP_IMAGE_ID must be a Docker sha256 content identifier'
[[ ! -e "$stop_file" ]] || fail 'stop file already exists before sampling'

learning_profile=''
if [[ "$workload_kind" == 'authenticated-learning' ]]; then
  learning_profile="$(required_value DIAGNOSTICS_LEARNING_PROFILE)"
  [[ "$learning_profile" == 'read' || "$learning_profile" == 'write' ]] \
    || fail 'DIAGNOSTICS_LEARNING_PROFILE must be read or write'
fi
if [[ "$workload_kind" == 'learning-event' || "$workload_kind" == 'notification-websocket' ]]; then
  kafka_container="$(required_value DIAGNOSTICS_KAFKA_CONTAINER)"
fi

umask 077
printf 'epoch\tapp_cpu\tapp_memory\tapp_pids\tpostgres_cpu\tpostgres_memory\tredis_cpu\tredis_memory\tjvm_used_bytes\thikari_active\thikari_pending\tkafka_cpu\tkafka_memory\tkafka_pids\toutbox_pending\toutbox_oldest_age\tactive_websocket_sessions\n' \
  >"$samples_file"

captured_samples=0
while [[ ! -e "$stop_file" || "$captured_samples" -lt "$minimum_samples" ]]; do
  stats_containers=("$app_container" "$postgres_container" "$redis_container")
  [[ -z "$kafka_container" ]] || stats_containers+=("$kafka_container")
  stats_snapshot="$(docker stats --no-stream \
    --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemPerc}}\t{{.PIDs}}' \
    "${stats_containers[@]}")" || fail 'Docker resource snapshot failed'
  app_stats="$(container_stats_from_snapshot "$stats_snapshot" "$app_container")"
  postgres_stats="$(container_stats_from_snapshot "$stats_snapshot" "$postgres_container")"
  redis_stats="$(container_stats_from_snapshot "$stats_snapshot" "$redis_container")"
  jvm_used="$(metric_value jvm.memory.used)"
  hikari_active="$(metric_value hikaricp.connections.active)"
  hikari_pending="$(metric_value hikaricp.connections.pending)"
  kafka_cpu='0'
  kafka_memory='0'
  kafka_pids='0'
  outbox_pending='0'
  outbox_oldest_age='0'
  active_websocket_sessions='0'
  if [[ "$workload_kind" == 'learning-event' || "$workload_kind" == 'notification-websocket' ]]; then
    kafka_stats="$(container_stats_from_snapshot "$stats_snapshot" "$kafka_container")"
    IFS=$'\t' read -r kafka_cpu kafka_memory kafka_pids <<<"$kafka_stats"
    outbox_pending="$(metric_value learning.events.outbox.pending)"
    outbox_oldest_age="$(metric_value learning.events.outbox.oldest.age.seconds)"
  fi
  if [[ "$workload_kind" == 'notification-websocket' ]]; then
    active_websocket_sessions="$(metric_value notifications.websocket.sessions.active)"
  fi

  IFS=$'\t' read -r app_cpu app_memory app_pids <<<"$app_stats"
  IFS=$'\t' read -r postgres_cpu postgres_memory _ <<<"$postgres_stats"
  IFS=$'\t' read -r redis_cpu redis_memory _ <<<"$redis_stats"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date +%s)" \
    "$app_cpu" "$app_memory" "$app_pids" \
    "$postgres_cpu" "$postgres_memory" \
    "$redis_cpu" "$redis_memory" \
    "$jvm_used" "$hikari_active" "$hikari_pending" \
    "$kafka_cpu" "$kafka_memory" "$kafka_pids" \
    "$outbox_pending" "$outbox_oldest_age" "$active_websocket_sessions" \
    >>"$samples_file"
  captured_samples=$((captured_samples + 1))
done

read -r sample_count max_app_cpu max_app_memory max_app_pids \
  max_postgres_cpu max_postgres_memory max_redis_cpu max_redis_memory \
  max_jvm_used max_hikari_active max_hikari_pending \
  max_kafka_cpu max_kafka_memory max_kafka_pids \
  max_outbox_pending max_outbox_oldest_age max_active_websocket_sessions observed_span <<<"$(
    awk -F '\t' '
      NR == 1 { next }
      {
        if (count == 0) first_epoch = $1
        last_epoch = $1
        count++
        for (column = 2; column <= 17; column++) {
          if (count == 1 || $column + 0 > maximum[column]) maximum[column] = $column + 0
        }
      }
      END {
        printf "%d %.6f %.6f %d %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %d %.6f %.6f %.6f %d", count,
          maximum[2], maximum[3], maximum[4], maximum[5], maximum[6],
          maximum[7], maximum[8], maximum[9], maximum[10], maximum[11],
          maximum[12], maximum[13], maximum[14], maximum[15], maximum[16], maximum[17],
          last_epoch - first_epoch
      }
    ' "$samples_file"
  )"
[[ "$sample_count" =~ ^[0-9]+$ ]] && ((sample_count >= minimum_samples)) \
  || fail "only $sample_count resource samples were captured; at least $minimum_samples are required"

postgres_json="$(docker exec "$postgres_container" \
  psql --no-psqlrc --quiet --tuples-only --no-align \
  --username "$database_user" --dbname "$database_name" \
  --command "
    SELECT json_build_object(
      'connections', numbackends,
      'commits', xact_commit,
      'rollbacks', xact_rollback,
      'blocksRead', blks_read,
      'blocksHit', blks_hit,
      'tempFiles', temp_files,
      'tempBytes', temp_bytes,
      'deadlocks', deadlocks
    )::text
    FROM pg_stat_database
    WHERE datname = current_database();
  ")" || fail 'PostgreSQL diagnostic counters could not be read'
[[ "$postgres_json" == \{*\} ]] || fail 'PostgreSQL diagnostics did not return a JSON object'

redis_hits="$(redis_info_value stats keyspace_hits)"
redis_misses="$(redis_info_value stats keyspace_misses)"
redis_evictions="$(redis_info_value stats evicted_keys)"
redis_peak_memory="$(redis_info_value memory used_memory_peak)"

application_extension=''
if [[ "$workload_kind" == 'catalog' ]]; then
  cache_hits="$(metric_value catalog.cache.access 'result:hit')"
  cache_misses="$(metric_value catalog.cache.access 'result:miss')"
  cache_failures="$(metric_value catalog.cache.access 'result:failure')"
  awk -v value="$cache_hits" 'BEGIN { exit !(value + 0 > 0) }' \
    || fail 'catalog cache hit counter did not increase'
  awk -v value="$cache_failures" 'BEGIN { exit !(value + 0 == 0) }' \
    || fail "catalog cache failure counter is $cache_failures instead of zero"
  awk -v application="$cache_hits" -v server="$redis_hits" \
    'BEGIN { exit !(application + 0 == server + 0) }' \
    || fail "application cache hits $cache_hits do not match Redis hits $redis_hits"
  awk -v application="$cache_misses" -v server="$redis_misses" \
    'BEGIN { exit !(application + 0 == server + 0) }' \
    || fail "application cache misses $cache_misses do not match Redis misses $redis_misses"
  application_extension=", \"catalogCacheHits\": $cache_hits, \"catalogCacheMisses\": $cache_misses, \"catalogCacheFailures\": $cache_failures"
  summary_format='ai-learning-catalog-resource-v1'
  workload_json='{"datasetPublishedCourses": 5000, "ratePerSecond": 25, "durationSeconds": 30}'
elif [[ "$workload_kind" == 'authenticated-learning' ]]; then
  summary_format='ai-learning-authenticated-learning-resource-v1'
  learning_rate=10
  [[ "$learning_profile" == 'write' ]] || learning_rate=20
  workload_json="{\"profile\": \"$learning_profile\", \"identities\": 40, \"ratePerSecond\": $learning_rate, \"durationSeconds\": 30, \"courseSlug\": \"authenticated-learning-performance\", \"lessons\": 4}"
elif [[ "$workload_kind" == 'learning-event' ]]; then
  dispatch_published="$(metric_value learning.events.dispatch 'outcome:published')"
  dispatch_failed="$(metric_value learning.events.dispatch 'outcome:failed')"
  analytics_projected="$(metric_value analytics.kafka.processing 'outcome:projected')"
  analytics_duplicate="$(metric_value analytics.kafka.processing 'outcome:duplicate')"
  analytics_rejected="$(metric_value analytics.kafka.processing 'outcome:rejected')"
  analytics_dead_letter="$(metric_value analytics.kafka.dead.letter)"
  notifications_projected="$(metric_value notifications.kafka.processing 'outcome:projected')"
  notifications_duplicate="$(metric_value notifications.kafka.processing 'outcome:duplicate')"
  notifications_rejected="$(metric_value notifications.kafka.processing 'outcome:rejected')"
  notifications_dead_letter="$(metric_value notifications.kafka.dead.letter)"
  application_extension=", \"maxOutboxPending\": $max_outbox_pending, \"maxOutboxOldestAgeSeconds\": $max_outbox_oldest_age, \"dispatchPublished\": $dispatch_published, \"dispatchFailed\": $dispatch_failed, \"analyticsProjected\": $analytics_projected, \"analyticsDuplicate\": $analytics_duplicate, \"analyticsRejected\": $analytics_rejected, \"analyticsDeadLetter\": $analytics_dead_letter, \"notificationsProjected\": $notifications_projected, \"notificationsDuplicate\": $notifications_duplicate, \"notificationsRejected\": $notifications_rejected, \"notificationsDeadLetter\": $notifications_dead_letter"
  summary_format='ai-learning-event-resource-v1'
  workload_json='{"identities": 40, "ratePerSecond": 8, "durationSeconds": 30, "courseSlug": "learning-event-performance", "lessons": 8}'
else
  dispatch_published="$(metric_value learning.events.dispatch 'outcome:published')"
  dispatch_failed="$(metric_value learning.events.dispatch 'outcome:failed')"
  notifications_projected="$(metric_value notifications.kafka.processing 'outcome:projected')"
  notifications_duplicate="$(metric_value notifications.kafka.processing 'outcome:duplicate')"
  notifications_rejected="$(metric_value notifications.kafka.processing 'outcome:rejected')"
  notifications_dead_letter="$(metric_value notifications.kafka.dead.letter)"
  realtime_sent="$(metric_value notifications.realtime.delivery 'outcome:sent')"
  realtime_failed="$(metric_value notifications.realtime.delivery 'outcome:failed')"
  application_extension=", \"maxOutboxPending\": $max_outbox_pending, \"maxOutboxOldestAgeSeconds\": $max_outbox_oldest_age, \"maxActiveWebSocketSessions\": $max_active_websocket_sessions, \"dispatchPublished\": $dispatch_published, \"dispatchFailed\": $dispatch_failed, \"notificationsProjected\": $notifications_projected, \"notificationsDuplicate\": $notifications_duplicate, \"notificationsRejected\": $notifications_rejected, \"notificationsDeadLetter\": $notifications_dead_letter, \"realtimeSent\": $realtime_sent, \"realtimeFailed\": $realtime_failed"
  summary_format='ai-learning-notification-websocket-resource-v1'
  workload_json='{"identities": 40, "sessionsPerIdentity": 2, "expectedSessions": 80, "completionWorkers": 8, "expectedCompletions": 40, "connectionWindowSeconds": 10, "courseSlug": "notification-websocket-performance"}'
fi

generated_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
printf '{\n' >"$output_file"
printf '  "format": "%s",\n' "$summary_format" >>"$output_file"
printf '  "generatedAtUtc": "%s",\n' "$generated_at" >>"$output_file"
printf '  "applicationImageId": "%s",\n' "$application_image_id" >>"$output_file"
printf '  "workload": %s,\n' "$workload_json" >>"$output_file"
printf '  "samples": {"count": %s, "minimumRequired": %s, "observedSpanSeconds": %s},\n' \
  "$sample_count" "$minimum_samples" "$observed_span" >>"$output_file"
printf '  "application": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "maxPids": %s, "maxJvmUsedBytes": %s, "maxHikariActive": %s, "maxHikariPending": %s%s},\n' \
  "$max_app_cpu" "$max_app_memory" "$max_app_pids" "$max_jvm_used" \
  "$max_hikari_active" "$max_hikari_pending" "$application_extension" >>"$output_file"
printf '  "postgres": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "database": %s},\n' \
  "$max_postgres_cpu" "$max_postgres_memory" "$postgres_json" >>"$output_file"
redis_suffix=''
[[ "$workload_kind" != 'learning-event' && "$workload_kind" != 'notification-websocket' ]] \
  || redis_suffix=','
printf '  "redis": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "keyspaceHits": %s, "keyspaceMisses": %s, "evictedKeys": %s, "usedMemoryPeakBytes": %s}%s\n' \
  "$max_redis_cpu" "$max_redis_memory" "$redis_hits" "$redis_misses" \
  "$redis_evictions" "$redis_peak_memory" "$redis_suffix" >>"$output_file"
if [[ "$workload_kind" == 'learning-event' || "$workload_kind" == 'notification-websocket' ]]; then
  printf '  "kafka": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "maxPids": %s}\n' \
    "$max_kafka_cpu" "$max_kafka_memory" "$max_kafka_pids" >>"$output_file"
fi
printf '}\n' >>"$output_file"

test -s "$output_file" || fail 'resource summary is missing or empty'
printf 'Resource diagnostics captured %s samples for %s; summary: %s\n' \
  "$sample_count" "$workload_kind" "$output_file"
