# ADR-014 — Isolated authenticated learning performance profiles

Status: accepted for Phase 8.4b2

Date: 2026-09-16

## Context

The public catalog regression and its resource diagnostics cover anonymous reads.
They do not exercise JWT verification, authorization, enrollment lookup, lesson
access, progress reads, or progress upserts. Combining those paths with catalog,
Kafka, WebSocket, AI-provider, and media traffic would produce a number that cannot
identify which subsystem regressed.

The existing learning API already exposes the business paths needed for a focused
profile. No endpoint, application interface, persistence abstraction, migration,
index, cache, pool, or runtime setting is required to generate this evidence.

## Decision

- Add two independently selected k6 profiles for the existing learning-progress
  contract: `read` and `write`. Never report them as one blended latency number.
- Give each profile its own disposable PostgreSQL, Redis, application, network, and
  k6 containers. Bind the application only to a random loopback host port and use
  the same immutable production image/runtime restrictions as the catalog harness.
- Seed one published free course, one section, and deterministic lessons outside
  Flyway. Create forty disposable students through `POST /api/v1/auth/register`,
  enroll each through `POST /api/v1/courses/{slug}/enrollments`, and retain their
  JWTs only inside k6 process memory for that run.
- Assign one token to each possible virtual user. A VU executes requests serially,
  so a progress row is never concurrently mutated by two identities. All identities,
  enrollments, progress, credentials, and tokens disappear with the isolated
  containers; no cleanup query is allowed to target a shared database.
- The read profile uses 20 iterations/second for 30 seconds and mixes protected
  lesson-player and lesson-progress GET requests. The write profile uses 10
  iterations/second for 30 seconds and performs idempotent progress PUT requests.
  Writes keep `completed=false` so this profile does not measure outbox/Kafka event
  throughput; event throughput remains a separate Phase 8.4b scenario.
- Validate HTTP status, response identity, authorization-derived content, position,
  completion state, achieved iterations, dropped work, failures, and profile latency.
  Use workload-only custom metrics so password hashing and enrollment setup do not
  contaminate steady-state latency while setup correctness still fails the run.
- Capture application/JVM/Hikari, PostgreSQL, Redis, and container evidence per
  profile without arbitrary CPU, memory, or pool ceilings. Retain versioned compact
  summaries tied to the exact production image; never retain credentials, JWTs,
  raw response bodies, raw samples, or database contents.
- Run both profiles in one independent CI job after the production-image gate.
  A failure in either profile or any missing/invalid artifact fails the job.
- Add no Java production source, dependency, API change, authorization exception,
  Flyway migration, index, speculative tuning, or cross-module dependency.

## Consequences

The project gains repeatable regression evidence for protected learning reads and
writes while preserving a clear diagnosis boundary. Registration and enrollment
setup prove the real security/business entry path, but their cost is reported as
setup behavior rather than steady-state request latency.

The results remain synthetic CI evidence, not production capacity, SLOs, instance
sizing, or autoscaling policy. Completion events, Kafka, WebSocket fan-out,
AI-provider concurrency/cost, media bandwidth, spike, saturation, recovery, and
soak behavior remain separate profiles.
