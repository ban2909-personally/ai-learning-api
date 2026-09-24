# ADR-018 — Authenticated media range bandwidth profile

Date: 2026-09-24

Status: Accepted

## Context

Lesson media is private. A browser authenticates with the media-scoped HttpOnly
cookie, the learning module verifies lesson access, the catalog module resolves the
published media asset, and the MinIO adapter opens only the requested byte range.
Existing integration tests prove HTTP range semantics and storage correctness, but
they do not exercise sustained concurrent transfer through the production image or
correlate application and storage resources.

Combining media transfer with catalog, event, WebSocket, or AI traffic would hide
the responsible subsystem. A portable CI profile also cannot represent a CDN,
internet path, production object size distribution, codec behavior, or production
capacity.

## Decision

### Isolated topology

Run the exact production application image with disposable PostgreSQL, Redis, and
MinIO containers on a generated private Docker network. Only the application binds
a random loopback port. A disposable k6 container drives the existing public API;
no test-only Spring bean, endpoint, storage adapter, or bypass is permitted.

Every image is pinned to an immutable digest. Containers have bounded resources,
drop capabilities, disallow privilege escalation, and use read-only roots or tmpfs
where their runtime permits it. Exact-name and label cleanup is mandatory on both
success and failure.

### Fixture and authentication

- Create one published free course with one lesson and complete media metadata
  outside Flyway.
- Upload one 32 MiB deterministic zero-filled `video/mp4` object through MinIO
  tooling, record its actual ETag, and reconcile the stored object size and ETag
  before traffic. The fixture represents byte delivery, not a playable codec.
- Register forty isolated students and enroll each through the existing HTTP
  contracts. Obtain authentication through the normal login response so k6 uses
  the existing media-scoped cookie path.
- Never retain passwords, JWTs, cookies, MinIO credentials, object bytes, email
  addresses, raw responses, or container logs in CI artifacts.

### Workload and correctness contract

Use a constant arrival rate of eight requests per second for thirty seconds. Each
iteration selects a deterministic aligned 1 MiB range within the 32 MiB object and
calls the existing lesson-media URL as one of the forty enrolled students.

The profile fails unless all of the following hold:

- at least 235 iterations complete, with zero dropped iteration and zero failed
  request;
- every response is HTTP `206`, has `Accept-Ranges: bytes`, the expected quoted
  ETag, `video/mp4`, exact `Content-Length`, and exact `Content-Range` values;
- every body is exactly 1 MiB and its SHA-256 equals the known hash of the fixture
  range;
- completed response count, response bytes, expected transferred bytes, fixture
  metadata, identities, and enrollments reconcile exactly;
- p95 request duration is below 2,000 ms and p99 below 4,000 ms.

These are regression budgets for the bounded CI runner, not service objectives.
They may only be changed with recorded evidence and review.

### Diagnostics and evidence

Extend the existing resource collector with one explicit `media-bandwidth` mode.
Capture correlated application/JVM/Hikari, PostgreSQL, Redis, MinIO, and container
samples against the exact application image ID. Do not introduce tuning solely from
one run; a change still requires a repeatable diagnosed bottleneck.

Retain three compact, versioned JSON summaries for fourteen days:

1. client workload and latency/correctness results;
2. correlated application, data-store, and MinIO resources;
3. exact fixture, enrollment, and byte reconciliation.

The orchestration script validates every summary, minimum sample count, image ID,
cleanup, and absence of secrets before succeeding. CI runs the profile as an
independent job after the production image and retains a single artifact containing
the three summaries.

### Asynchronous response lifecycle safety

Repeated pre-main verification exposed an intermittent malformed HTTP response:
Tomcat recycled its response headers while Spring Security's `HeaderWriterFilter`
was still writing frame options after asynchronous media processing. The resulting
`MimeHeaders` null pointer produced either a malformed header line or an early EOF
in roughly one request per workload.

Security headers are therefore written eagerly, before the remaining filter chain
starts. This keeps all existing default security headers while removing the late
write from the asynchronous completion path; it does not change authentication,
authorization, REST, range, or database behavior. An integration regression test
rejects any security-header write after downstream processing starts. The workload
runner prints only a bounded application-log tail on failure so the lifecycle error
is diagnosable without retaining logs in artifacts.

This mitigation follows the public `HeaderWriterFilter` eager-write contract and
addresses the same `MimeHeaders` under-load failure shape reported by Spring
Security. The production-image workload remains the authoritative end-to-end gate
because servlet mocks cannot reproduce Tomcat response recycling.

## Consequences

The project gains repeatable evidence for authenticated single-range delivery,
stream closure, bounded JVM behavior, and MinIO transfer through the current
modular boundaries. The profile also protects the API headers and byte contract
used by responsive browser video clients.

The result does not establish production throughput, concurrent viewer capacity,
geographic latency, origin egress cost, CDN/cache-hit behavior, HLS/DASH segment
performance, upload capacity, transcoding capacity, spike/saturation/soak behavior,
or a production SLO. Those require an approved deployment topology, traffic model,
representative media corpus, downstream quotas, and business policy.

## References

- Spring Security `HeaderWriterFilter` API:
  https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/web/header/HeaderWriterFilter.html
- Spring Security issue 10835, `MimeHeaders` null pointer under load:
  https://github.com/spring-projects/spring-security/issues/10835
- Spring Framework issue 33439, asynchronous `StreamingResponseBody` error race:
  https://github.com/spring-projects/spring-framework/issues/33439
