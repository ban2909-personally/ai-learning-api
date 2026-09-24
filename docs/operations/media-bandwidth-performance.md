# Authenticated media range bandwidth operations

## Purpose and boundary

This profile detects regressions across the existing private lesson-media path:
media-scoped cookie authentication, enrollment access, the catalog media contract,
single-range HTTP delivery, and MinIO streaming through the production image.

The result applies only to the isolated single-instance topology and synthetic
fixture below. It is not a production capacity claim, SLO, CDN/cache evaluation,
internet-bandwidth measurement, origin-cost estimate, upload/transcoding test, or
HLS/DASH benchmark.

## Run the isolated profile

Build the production image, validate shell syntax, and run the harness:

```bash
docker build --tag ai-learning-api:ci .
bash -n scripts/collect-resource-diagnostics.sh scripts/test-media-bandwidth-performance.sh
bash scripts/test-media-bandwidth-performance.sh
```

Set `PERFORMANCE_APP_IMAGE` only to verify another image already present on the
local Docker daemon. The harness requires Docker, curl, OpenSSL, and the commands it
checks before allocating resources. It creates a generated private network and
disposable PostgreSQL, Redis, MinIO, application, client-tool, and k6 containers.
Never point it at a shared or production service.

The MinIO server and client-tool images use Chainguard's public repositories and
are pinned to the immutable digests recorded in the harness. The readable
`latest` labels do not float past those digests. Both images run without root
privileges; they are isolated test dependencies, not a production provider choice.
The server is capped at one CPU and 512 MiB memory. Its 2 GiB tmpfs is a logical
ceiling required by MinIO's free-drive reserve for the 32 MiB fixture and is not
preallocated; `MINIO_CI_CD=1` keeps the server on its CI memory path.

## Fixture and regression contract

The fixture is outside Flyway. It contains one published free course, one protected
lesson, and one 32 MiB zero-filled object stored under the production object-key
convention. The object is intentionally synthetic byte content, not a playable
video. The runner reads the actual MinIO ETag and stores the same value in the
lesson metadata before traffic.

k6 registers and enrolls forty students through the existing HTTP contracts. It
uses the access token delivered through the normal media-scoped cookie path, never
an authorization bypass. The workload schedules eight requests per second for
thirty seconds. Each request selects an aligned 1 MiB byte range.

The client gate requires at least 235 completed responses, zero dropped iteration,
zero failed request, HTTP `206`, exact `Accept-Ranges`, `Content-Type`, ETag,
`Content-Length`, and `Content-Range` headers, and exact body length/SHA-256. p95
must remain below 2,000 ms and p99 below 4,000 ms on the bounded CI runner.

The pipeline gate requires exact agreement between iterations, valid responses,
transferred bytes, forty isolated identities, forty enrollments, database media
metadata, and the final MinIO object size/ETag. Media reads must not create lesson
progress or learning-event outbox rows.

These thresholds are portable regression budgets. Change them only with recorded
repeatable evidence and review; never weaken authentication or correctness to make
a run pass.

## Evidence

Successful runs create three compact JSON files:

```text
target/performance/media-bandwidth-client-summary.json
target/performance/media-bandwidth-pipeline-summary.json
target/performance/media-bandwidth-resource-summary.json
```

Their formats are `ai-learning-media-bandwidth-v1`,
`ai-learning-media-bandwidth-pipeline-v1`, and
`ai-learning-media-bandwidth-resource-v1`. Pipeline and resource evidence contain
the exact production image ID. CI retains only these summaries for fourteen days.

Resource evidence requires at least ten correlated samples and records application,
JVM, Hikari, PostgreSQL, Redis, MinIO, and container maxima. CPU can exceed 100%
when a container uses more than one core. These maxima are diagnostic evidence,
not production resource limits.

## Security and cleanup

- Only the application binds a random loopback port. PostgreSQL, Redis, and MinIO
  remain private to the generated Docker network.
- The unauthenticated actuator probe must return HTTP 401 before a disposable
  diagnostics JWT is created.
- PostgreSQL, Redis, JWT, MinIO, diagnostics, and student credentials are generated
  per run and checked against retained summaries.
- Artifacts exclude tokens, cookies, passwords, emails, raw response bytes,
  container logs, and MinIO client configuration.
- PostgreSQL, Redis, and MinIO data is tmpfs-backed. Containers drop capabilities,
  disallow privilege escalation, and use bounded CPU, memory, and PIDs.
- Cleanup targets only generated names. After interruption, inspect resources with
  label `ai-learning.performance=media-bandwidth` and remove only those exact
  resources; never use broad Docker cleanup.

Treat any wrong header/hash/count, changed object, missing artifact/field, wrong
image ID, insufficient resource samples, latency breach, unauthorized metrics
access, secret retention, or leftover resource as a failed run. Diagnose all three
summaries together.

On failure, the harness prints the last 200 application-log lines before cleanup.
This bounded diagnostic is console-only and must not be added to retained CI
artifacts because it may contain request or infrastructure context.
