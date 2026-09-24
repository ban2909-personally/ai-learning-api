# Development report — authenticated media range bandwidth

Date: 2026-09-24

Branch: feature/media-bandwidth-performance

## Outcome

Phase 8.4b6 adds a production-image-bound regression profile for the existing
authenticated lesson-media path: media-scoped cookie authentication, enrollment
authorization, catalog media metadata, HTTP single-range delivery, and private
MinIO object streaming.

The profile preserves the REST and database contracts. It adds no application
endpoint, authentication bypass, production business fixture, duplicate storage
adapter, or speculative abstraction. The workload is a portable regression gate;
it is not a production capacity, CDN, HLS/DASH, internet-bandwidth, or SLO claim.

## Architecture and maintainability

- ADR-018 fixes the isolated topology, fixture, workload, integrity rules,
  latency budgets, resource evidence, cleanup behavior, and non-goals before
  delivery.
- One deterministic 32 MiB zero-filled object is created outside Flyway under the
  production object-key convention. The runner reads its actual MinIO ETag and
  stores the same value in the disposable lesson fixture before traffic.
- Forty students are registered and enrolled through existing HTTP contracts. k6
  schedules eight authenticated range requests per second for thirty seconds and
  uses only the normal media-scoped access-token cookie.
- Every response must be HTTP 206 with exact `Accept-Ranges`, `Content-Type`, ETag,
  `Content-Length`, and `Content-Range`. Each aligned 1 MiB body must match both
  the expected length and SHA-256.
- The existing shared diagnostics collector gained one explicit media-bandwidth
  mode and MinIO sampling. No parallel diagnostics utility was introduced.
- CI runs the workload as an independent job after the production-image gate and
  retains only three compact versioned summaries for fourteen days.

## Security and data lifecycle

- Only the application binds a random loopback port. PostgreSQL, Redis, MinIO,
  client tools, and k6 remain private to a generated Docker network.
- Unauthenticated actuator access must return HTTP 401 before a disposable
  diagnostics identity receives the only metrics JWT.
- PostgreSQL, Redis, JWT, MinIO, diagnostics, and student credentials are generated
  for each run. Summaries exclude tokens, cookies, passwords, email addresses,
  response bytes, container logs, and MinIO client configuration.
- Containers drop capabilities, disallow privilege escalation, use bounded CPU,
  memory, and PIDs, and keep PostgreSQL, Redis, and MinIO state on tmpfs.
- Exact-name cleanup ran on success and failure. Final label inspection found no
  workload container or network.

## Local pre-push gates

- The first Maven attempt was interrupted when the workstation slept for about 46
  minutes while Testcontainers was starting PostgreSQL. It ended with one
  container-start timeout after the runtime reported a matching clock leap; it was
  not accepted as evidence.
- A complete uninterrupted Maven clean verify rerun passed 203 tests with zero
  failure, error, or skip in 4 minutes 8 seconds. All twelve Flyway migrations,
  Spring Modulith boundaries, ArchUnit direction rules, JaCoCo checks, and the
  CycloneDX 1.6 application SBOM with 138 components passed.
- The exact production image for feature HEAD `8f061469a843046fc4c77fa85fa24de57e04712e`
  is `sha256:207e664fe6bcd0a3ca9e9dbab35e1a63230ca440514339ff0277f78c11d00ccf`.
  It runs as `65532:65532`, activates `prod`, and retains the fixed
  `/usr/bin/java -jar /app/app.jar` entrypoint.
- The image CycloneDX 1.7 SBOM contains 153 components. Source/image secret scans
  were clean. The current inventory contains 67 known findings: 41 MEDIUM, 18 LOW,
  and eight currently unfixed HIGH findings; zero fixable HIGH/CRITICAL finding
  crossed the release gate.
- PostgreSQL recovery passed confirmation, non-empty-target, and checksum guards
  before exact restore in 27 seconds. MinIO recovery passed confirmation, prefix
  reuse, non-empty-target, inventory checksum/count, and missing/extra/different
  object guards before exact verification in 39 seconds.
- Catalog baseline passed 751 iterations at p95 16.94 ms. Catalog diagnostics
  passed 751 iterations at p95 26.44 ms with ten samples.
- Authenticated read passed 600 iterations at p95 24.80 ms with fourteen samples;
  progress write passed 300 iterations at p95 36.12 ms with fourteen samples.
- Learning-event throughput passed 241 iterations at p95 47.94 ms with eighteen
  samples and exact database/outbox/Kafka/projection reconciliation.
- Notification WebSocket fan-out passed 80 authenticated sessions, 40 completion
  writes, exactly 80 valid messages, p95 1,680.90 ms, and twelve samples.
- AI Mentor passed 240 complete turns at p95 581.20 ms and p99 967.88 ms with
  fourteen samples, zero failure/drop/rejection, provider concurrency eight, and
  exact message, token, quota, and metric reconciliation.
- Media bandwidth passed 241 complete responses at p95 66.87 ms and p99 131.63 ms,
  transferred exactly 252,706,816 bytes, and captured fourteen correlated samples.
  All 40 identities/enrollments, the 32 MiB object and ETag, 1 MiB response hashes,
  and zero progress/outbox side effects reconciled against the exact image.

## Cohesive implementation commits

- `b18ef27` — define ADR-018 and the Phase 8.4b6 boundary.
- `db51c17` — add the deterministic fixture and authenticated range workload.
- `fbdb0b2` — add fail-closed orchestration and MinIO-aware diagnostics.
- `8f06146` — add the independent CI gate, artifact contract, and runbook.
- `cd79e6d` — record complete local implementation and pre-push evidence.

## Feature CI evidence

- Feature CI run `35989835206` passed all eleven jobs for exact pre-evidence SHA
  `cd79e6dc0d1607b916c8b969999c0d00bd768bfe`: Maven verification, production
  image/security, PostgreSQL recovery, MinIO recovery, catalog baseline, catalog
  diagnostics, authenticated learning, learning-event throughput, notification
  WebSocket fan-out, AI Mentor concurrency/token bounds, and authenticated media
  range bandwidth.
- All nine retained artifacts are non-empty and unexpired: application SBOM
  `10803897439` (73.3 KB), image security `10803034183` (69.8 KB), catalog
  performance `10804116267` (464 bytes), catalog resource `10803627770` (1.13 KB),
  authenticated learning `10803888224` (2.31 KB), learning event `10803803862`
  (1.78 KB), notification WebSocket `10803888134` (1.99 KB), AI Mentor
  `10803917827` (1.71 KB), and media bandwidth `10803738805` (1.82 KB).

## Delivery status

Implementation, all local pre-push gates, exact pre-evidence feature CI, and
artifact verification are complete. Exact CI for the evidence-only commit,
no-fast-forward merge, repeated merge gates, exact main CI, and final delivery
evidence remain pending and must complete before Phase 8.4b6 is closed.
