# Development report — AI Mentor concurrency and token-cost performance

Date: 2026-09-24

Branch: feature/ai-mentor-performance

## Outcome

Phase 8.4b5 adds a production-image-bound regression profile for the complete
authenticated AI Mentor path: lesson access, atomic Redis quota, conversation and
message persistence, the existing OpenAI Responses HTTP/SSE adapter, application
SSE delivery, persisted token usage, executor behavior, and correlated resources.

The profile uses a private deterministic provider simulator and never calls or
stores credentials for a commercial AI provider. REST, SSE, and database contracts
remain unchanged. The workload exposed a real executor queueing bottleneck, which
was corrected with validated bounded runtime configuration rather than by weakening
the latency gate.

## Architecture and maintainability

- ADR-017 fixes the topology, workload, exact reconciliation rules, latency
  thresholds, cost-evidence boundary, isolation, and non-goals before delivery.
- Forty students are registered and enrolled through existing HTTP contracts. k6
  schedules eight turns per second for thirty seconds against one protected lesson.
- A separate private simulator validates the actual Responses payload and bearer
  credential, emits the actual SSE protocol, waits 500 ms, and exposes aggregate
  counters only. No alternate Spring adapter or test-only endpoint was added.
- Provider input/output usage is fixed at 128/64 tokens. Persisted totals and the
  application output ceiling are reconciled exactly; no unstable currency constant
  or model price was introduced.
- The shared diagnostics collector gained one explicit AI Mentor mode instead of a
  parallel utility. It correlates application/JVM/Hikari, PostgreSQL, Redis, and
  provider resources with the exact production image.
- CI runs the profile as an independent job and retains only three compact,
  versioned, non-secret summaries for fourteen days.

## Performance defect found and corrected

The first profile completed 238 requests without HTTP failure but dropped two
scheduled iterations and measured p95 at 2,126 ms, above the fixed 2,000 ms budget.
The executor had four core threads, a maximum of 32, and queue capacity 100.
`ThreadPoolTaskExecutor` queued work after the core threads were occupied, so the
maximum pool did not provide headroom at the target rate.

`MentorExecutorProperties` now provides validated, environment-overridable bounds:
eight core workers, 32 maximum workers, and queue capacity 32 by default. Core size
must not exceed maximum size; each pool dimension is capped. Graceful shutdown waits
up to thirty seconds for accepted turns. The change preserves the use-case and
adapter boundaries and does not introduce a redundant interface.

The passing pre-push run completed 241 turns with zero failure or drop, p95 at
548.75 ms, p99 at 1,008.12 ms, and thirteen resource samples. Provider maximum
concurrency was bounded, all active requests drained, and every client/provider/
database/quota/metric count reconciled.

## Security and data lifecycle

- Only the application binds a random loopback port. PostgreSQL, Redis, and the
  simulator remain private to the generated Docker network.
- Unauthenticated metrics must return HTTP 401. A disposable diagnostics identity
  receives the only metrics JWT, which is cleared after the collector starts.
- Database, Redis, JWT, provider, diagnostics, and student credentials are generated
  per run and never retained in summaries, commands, URLs, or logs.
- The simulator does not log or retain prompts or answers. Artifacts exclude raw SSE,
  database contents, tokens, email addresses, and container logs.
- Containers drop capabilities, disallow privilege escalation, use read-only roots
  where applicable, keep state on tmpfs, and have bounded CPU, memory, or PID use.
- Exact-name cleanup ran on success and failure. Final inspection found no container
  or network with the performance labels.

## Local pre-push gates

- Maven clean verify passed 203 tests with zero failure, error, or skip. All twelve
  Flyway migrations, Spring Modulith boundaries, ArchUnit dependency rules, JaCoCo
  checks, and the CycloneDX 1.6 application SBOM with 138 components passed.
- The production image ID was
  `sha256:81d22c62c55b06ea23fdbc68a892082d9597c3753610e67f26188378f20a6e0a`.
  It runs as `65532:65532`, activates the production profile, and retains the fixed
  `/usr/bin/java -jar /app/app.jar` entrypoint.
- The image CycloneDX 1.7 SBOM contains 153 components. Source/image secret scans
  were clean. The complete vulnerability inventory contains 67 known findings:
  41 MEDIUM, 18 LOW, and eight currently unfixed HIGH findings; zero fixable
  HIGH/CRITICAL findings crossed the release gate.
- PostgreSQL recovery passed confirmation, non-empty-target, and checksum guards
  before exact restore in 26 seconds. MinIO recovery passed confirmation, prefix
  reuse, non-empty-target, inventory checksum/count, and missing/extra/different
  object guards before exact verification in 66 seconds.
- Catalog baseline passed 751 iterations at p95 10.56 ms. Catalog diagnostics
  passed 750 iterations at p95 11.60 ms with ten samples.
- Authenticated read passed 601 iterations at p95 11.83 ms with fourteen samples;
  progress write passed 300 iterations at p95 18.80 ms with fourteen samples.
- Learning-event throughput passed 240 iterations at p95 20.55 ms with eighteen
  samples and exact database/outbox/Kafka/projection reconciliation.
- Notification WebSocket fan-out passed 80 authenticated sessions, 40 completion
  writes, exactly 80 valid messages, p95 624.15 ms, and twelve samples.
- AI Mentor passed 241 complete turns at p95 548.75 ms and p99 1,008.12 ms with
  thirteen samples, zero request failure/drop/rejection, and exact provider,
  message, token, quota, and metric reconciliation.

## Cohesive commits before delivery gates

- `61cc699` — define ADR-017 and the Phase 8.4b5 boundary.
- `fff0cb1` — add the deterministic AI Mentor workload and provider simulator.
- `c635286` — scale and validate the bounded AI Mentor stream executor.
- `8378255` — orchestrate fail-closed pipeline and resource diagnostics.
- `745cb6b` — add the independent CI gate, artifact contract, and runbook.
- `dbca972` — record complete local implementation and pre-push evidence.
- `98d471e` — record the final feature-CI evidence used by the delivery gate.

## Feature CI evidence

- Final feature CI run `35953716170` passed all ten jobs for exact SHA
  `98d471eb744f10e733dd1c54bbf1c59ee8fce86c`: Maven verification,
  production image/security, PostgreSQL recovery, MinIO recovery, catalog baseline,
  catalog diagnostics, authenticated learning, learning-event throughput,
  notification WebSocket fan-out, and AI Mentor concurrency/token bounds.
- All eight retained artifacts are non-empty and unexpired: application SBOM
  `10789541913` (75,092 bytes), image security `10789152885` (71,452 bytes),
  catalog performance `10790091265` (464 bytes), catalog resource `10789462518`
  (1,145 bytes), authenticated learning `10789294252` (2,350 bytes), learning event
  `10789996702` (1,823 bytes), notification WebSocket `10789314107` (2,043 bytes),
  and AI Mentor `10789294121` (1,759 bytes).

## Merge and main CI evidence

- No-fast-forward merge `7102f5eb7a9abf52f46dd35825691938299b7f37`
  passed repeated local Maven verification with 203 tests, all twelve Flyway
  migrations, Spring Modulith, ArchUnit, JaCoCo, and application SBOM checks.
- The exact merge production image was
  `sha256:8f134ebda4b647e4fa9196ee6e748daa8bd09b6c3a142de82ca5a03761a9c105`.
  Runtime identity/entrypoint, image SBOM, fixable HIGH/CRITICAL vulnerability,
  source/image secret, and cleanup gates passed. PostgreSQL recovery completed in
  28 seconds and MinIO recovery in 53 seconds.
- Catalog baseline completed 750 iterations at p95 16.02 ms; diagnostics completed
  750 at p95 18.01 ms with ten samples. Authenticated read completed 600 at p95
  17.01 ms with fourteen samples; write completed 301 at p95 25.99 ms with fourteen
  samples. Learning-event throughput completed 240 at p95 31.78 ms with nineteen
  samples. Notification WebSocket delivered exactly 80 valid messages across 80
  sessions at p95 807.10 ms with thirteen samples.
- AI Mentor completed 241 turns with zero request failure, drop, rejection, active
  provider request, or reconciliation mismatch. p95 was 581.22 ms, p99 was
  1,124.35 ms, provider concurrency peaked at eight, and fourteen resource samples
  were captured against the exact merge image.
- Main CI run `35956022199` passed all ten jobs for the exact merge SHA. Its eight
  non-empty, unexpired artifacts are application SBOM `10790402634` (75,092 bytes),
  image security `10790552298` (71,451 bytes), catalog performance `10790771291`
  (464 bytes), catalog resource `10790597523` (1,151 bytes), authenticated learning
  `10790905592` (2,358 bytes), learning event `10789868956` (1,826 bytes),
  notification WebSocket `10790786296` (2,039 bytes), and AI Mentor `10790651864`
  (1,752 bytes).

## Delivery status

Implementation, full local pre-push and repeated merge gates, exact feature CI,
no-fast-forward merge, exact main CI, artifact verification, and delivery reporting
are complete. This closes Phase 8.4b5 as a portable regression control only. No
production capacity, answer-quality, provider-latency, currency-cost, or SLO
conclusion is made.
