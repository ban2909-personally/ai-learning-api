# ADR-017 — Isolated AI Mentor concurrency and token-cost profile

Status: Accepted

Date: 2026-09-24

## Context

The AI Mentor already enforces authenticated lesson access, an atomic Redis quota,
bounded prompt history and output, provider timeouts, asynchronous SSE delivery,
and persisted provider token usage. Existing tests prove those contracts in
isolation, but they do not correlate simultaneous mentor turns, executor behavior,
provider pressure, persisted usage, and application resources under repeatable
traffic.

Calling a commercial model from CI would make the result non-deterministic, expose
an external credential boundary, and create uncontrolled cost. A dedicated profile
must exercise the real application HTTP, quota, persistence, SSE, and OpenAI adapter
contracts against an isolated protocol simulator. It is a portable regression and
diagnostic control, not a production capacity, quality, latency, or pricing claim.

## Decision

### Boundary and topology

- Run the exact production application image with disposable PostgreSQL and Redis
  on a private Docker network. Only the application port is bound to a random
  loopback port.
- Run a separate deterministic provider simulator on the private network. Configure
  the existing OpenAI Responses adapter through `OPENAI_BASE_URL`; do not add a
  Spring profile, alternate application adapter, test-only endpoint, or direct
  service invocation.
- The simulator accepts only the configured bearer credential, validates the
  Responses request contract, never logs or retains prompts, emits bounded SSE
  deltas, and reports aggregate request/concurrency counters only.
- Keep Kafka and MinIO disabled with unreachable placeholders because they are not
  on the mentor request path.

### Deterministic workload

- Seed one free published course with one protected lesson outside Flyway.
- Register and enroll forty isolated students through the existing HTTP contracts.
  Credentials and JWTs remain process-local and are never retained.
- Drive mentor `POST` requests at eight arrivals per second for thirty seconds.
  Identities are selected deterministically so the default twenty-request user
  quota cannot be exhausted by the profile.
- Every provider response waits 500 ms, emits fixed safe text, identifies a synthetic
  model, and reports 128 input plus 64 output tokens. The application remains
  configured with an output ceiling above that fixed usage.
- Treat each complete SSE response as one iteration. The response must contain one
  accepted message, at least one delta, one completion, and no error event or
  provider details that the public contract intentionally omits.

### Correctness and regression gates

The profile fails unless all of the following hold:

- setup registration and enrollment succeed for all forty identities;
- every scheduled mentor turn completes with no HTTP failure, dropped iteration,
  capacity/quota/provider error event, malformed event sequence, or empty answer;
- at least 235 turns complete, with client-observed p95 below 2 seconds and p99
  below 4 seconds; these are regression budgets, not production SLOs;
- provider request and completion totals exactly equal client completions, provider
  active requests drain to zero, and observed maximum provider concurrency is at
  least two and no greater than the application executor maximum of 32;
- exactly one conversation exists per participating identity, user and assistant
  message counts match completed turns, and no incomplete assistant usage row
  exists;
- persisted input/output token totals equal completed turns multiplied by the fixed
  simulator usage, and each assistant row stays within the configured output-token
  ceiling;
- application accepted/completed counters match completed turns while rejected and
  failed counters remain zero;
- at least ten samples bind application/JVM/Hikari, PostgreSQL, Redis, provider, and
  container evidence to the exact production image; and
- all containers, networks, credentials, raw samples, and generated user data are
  removed on success or failure.

### Cost evidence boundary

Token counts are the stable provider-neutral cost proxy retained by this phase. No
currency estimate is calculated because model prices, cached-input rules, regional
pricing, and commercial agreements are external and time-varying. Production cost
alerts and budgets require an approved model/pricing source and named owner in a
later operational policy phase.

Retain only compact versioned summaries for the client workload, provider/database
reconciliation, and correlated resources. Do not retain prompts, answers, JWTs,
email addresses, API keys, raw SSE streams, database contents, or container logs.

### Security and runtime controls

- Pin PostgreSQL, Redis, k6, and provider simulator images by digest.
- Use generated database, Redis, JWT, user, and provider credentials. Pass secrets
  through environment values without printing them and clear exported values during
  cleanup.
- Run disposable containers with dropped capabilities, `no-new-privileges`,
  read-only filesystems where applicable, tmpfs state, and bounded CPU, memory, and
  PID resources.
- Keep probes, sampling, workload, and drain checks bounded. Missing metrics,
  summaries, provider stats, database invariants, or cleanup evidence are failures,
  never implicit zeroes.

## Alternatives rejected

- Calling OpenAI from CI would introduce credentials, spend, rate-limit variance,
  network variance, and non-repeatable output.
- Replacing `MentorAiClient` with a test Spring bean would bypass the real HTTP/SSE
  provider adapter and weaken the evidence boundary.
- Pricing the synthetic tokens in currency would create a stale business constant
  with no approved owner.
- Raising executor, connection-pool, or Redis limits before measurement would be
  speculative tuning.
- Mixing mentor traffic into catalog or learning profiles would hide which subsystem
  caused a regression.

## Consequences

The repository gains repeatable evidence for mentor concurrency, quota behavior,
provider protocol handling, persisted token usage, and correlated resources without
calling a commercial model. CI becomes longer and maintains one small external
provider simulator used only by the performance harness. The profile does not prove
answer quality, real-provider latency or limits, production cost, multi-instance
capacity, spike/saturation/recovery behavior, long-duration soak behavior, or a
production SLO.
