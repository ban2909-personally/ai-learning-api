#!/usr/bin/env bash
set -euo pipefail

required_value() {
  local name="$1"
  local value="${!name:-}"

  [[ -n "$value" ]] || {
    printf 'Catalog resource diagnostics failed: required environment variable %s is missing\n' "$name" >&2
    exit 1
  }
  printf '%s' "$value"
}

app_port="$(required_value DIAGNOSTICS_APP_PORT)"
app_container="$(required_value DIAGNOSTICS_APP_CONTAINER)"
postgres_container="$(required_value DIAGNOSTICS_POSTGRES_CONTAINER)"
redis_container="$(required_value DIAGNOSTICS_REDIS_CONTAINER)"
database_name="$(required_value DIAGNOSTICS_DATABASE_NAME)"
database_user="$(required_value DIAGNOSTICS_DATABASE_USER)"
bearer_token="$(required_value DIAGNOSTICS_BEARER_TOKEN)"
application_image_id="$(required_value DIAGNOSTICS_APP_IMAGE_ID)"
stop_file="$(required_value DIAGNOSTICS_STOP_FILE)"
samples_file="$(required_value DIAGNOSTICS_SAMPLES_FILE)"
output_file="$(required_value DIAGNOSTICS_OUTPUT_FILE)"

fail() {
  printf 'Catalog resource diagnostics failed: %s\n' "$1" >&2
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
[[ -n "${REDISCLI_AUTH:-}" ]] || fail 'REDISCLI_AUTH is missing'
[[ "$application_image_id" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail 'DIAGNOSTICS_APP_IMAGE_ID must be a Docker sha256 content identifier'
[[ ! -e "$stop_file" ]] || fail 'stop file already exists before sampling'
umask 077
printf 'epoch\tapp_cpu\tapp_memory\tapp_pids\tpostgres_cpu\tpostgres_memory\tredis_cpu\tredis_memory\tjvm_used_bytes\thikari_active\thikari_pending\n' \
  >"$samples_file"

while [[ ! -e "$stop_file" ]]; do
  stats_snapshot="$(docker stats --no-stream \
    --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemPerc}}\t{{.PIDs}}' \
    "$app_container" "$postgres_container" "$redis_container")" \
    || fail 'Docker resource snapshot failed'
  app_stats="$(container_stats_from_snapshot "$stats_snapshot" "$app_container")"
  postgres_stats="$(container_stats_from_snapshot "$stats_snapshot" "$postgres_container")"
  redis_stats="$(container_stats_from_snapshot "$stats_snapshot" "$redis_container")"
  jvm_used="$(metric_value jvm.memory.used)"
  hikari_active="$(metric_value hikaricp.connections.active)"
  hikari_pending="$(metric_value hikaricp.connections.pending)"

  IFS=$'\t' read -r app_cpu app_memory app_pids <<<"$app_stats"
  IFS=$'\t' read -r postgres_cpu postgres_memory _ <<<"$postgres_stats"
  IFS=$'\t' read -r redis_cpu redis_memory _ <<<"$redis_stats"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(date +%s)" \
    "$app_cpu" "$app_memory" "$app_pids" \
    "$postgres_cpu" "$postgres_memory" \
    "$redis_cpu" "$redis_memory" \
    "$jvm_used" "$hikari_active" "$hikari_pending" \
    >>"$samples_file"
done

read -r sample_count max_app_cpu max_app_memory max_app_pids \
  max_postgres_cpu max_postgres_memory max_redis_cpu max_redis_memory \
  max_jvm_used max_hikari_active max_hikari_pending observed_span <<<"$(
    awk -F '\t' '
      NR == 1 { next }
      {
        if (count == 0) first_epoch = $1
        last_epoch = $1
        count++
        for (column = 2; column <= 11; column++) {
          if (count == 1 || $column + 0 > maximum[column]) maximum[column] = $column + 0
        }
      }
      END {
        printf "%d %.6f %.6f %d %.6f %.6f %.6f %.6f %.6f %.6f %.6f %d", count,
          maximum[2], maximum[3], maximum[4], maximum[5], maximum[6],
          maximum[7], maximum[8], maximum[9], maximum[10], maximum[11],
          last_epoch - first_epoch
      }
    ' "$samples_file"
  )"
[[ "$sample_count" =~ ^[0-9]+$ ]] && ((sample_count >= 10)) \
  || fail "only $sample_count resource samples were captured; at least 10 are required"

cache_hits="$(metric_value catalog.cache.access 'result:hit')"
cache_misses="$(metric_value catalog.cache.access 'result:miss')"
cache_failures="$(metric_value catalog.cache.access 'result:failure')"
awk -v value="$cache_hits" 'BEGIN { exit !(value + 0 > 0) }' \
  || fail 'catalog cache hit counter did not increase'
awk -v value="$cache_failures" 'BEGIN { exit !(value + 0 == 0) }' \
  || fail "catalog cache failure counter is $cache_failures instead of zero"

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
awk -v application="$cache_hits" -v server="$redis_hits" \
  'BEGIN { exit !(application + 0 == server + 0) }' \
  || fail "application cache hits $cache_hits do not match Redis hits $redis_hits"
awk -v application="$cache_misses" -v server="$redis_misses" \
  'BEGIN { exit !(application + 0 == server + 0) }' \
  || fail "application cache misses $cache_misses do not match Redis misses $redis_misses"

generated_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
printf '{\n' >"$output_file"
printf '  "format": "ai-learning-catalog-resource-v1",\n' >>"$output_file"
printf '  "generatedAtUtc": "%s",\n' "$generated_at" >>"$output_file"
printf '  "applicationImageId": "%s",\n' "$application_image_id" >>"$output_file"
printf '  "workload": {"datasetPublishedCourses": 5000, "ratePerSecond": 25, "durationSeconds": 30},\n' >>"$output_file"
printf '  "samples": {"count": %s, "minimumRequired": 10, "observedSpanSeconds": %s},\n' \
  "$sample_count" "$observed_span" >>"$output_file"
printf '  "application": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "maxPids": %s, "maxJvmUsedBytes": %s, "maxHikariActive": %s, "maxHikariPending": %s, "catalogCacheHits": %s, "catalogCacheMisses": %s, "catalogCacheFailures": %s},\n' \
  "$max_app_cpu" "$max_app_memory" "$max_app_pids" "$max_jvm_used" \
  "$max_hikari_active" "$max_hikari_pending" \
  "$cache_hits" "$cache_misses" "$cache_failures" >>"$output_file"
printf '  "postgres": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "database": %s},\n' \
  "$max_postgres_cpu" "$max_postgres_memory" "$postgres_json" >>"$output_file"
printf '  "redis": {"maxCpuPercent": %s, "maxMemoryPercent": %s, "keyspaceHits": %s, "keyspaceMisses": %s, "evictedKeys": %s, "usedMemoryPeakBytes": %s}\n' \
  "$max_redis_cpu" "$max_redis_memory" "$redis_hits" "$redis_misses" \
  "$redis_evictions" "$redis_peak_memory" >>"$output_file"
printf '}\n' >>"$output_file"

test -s "$output_file" || fail 'resource summary is missing or empty'
printf 'Catalog resource diagnostics captured %s samples; summary: %s\n' \
  "$sample_count" "$output_file"
