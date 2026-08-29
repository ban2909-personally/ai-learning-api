# Overnight development report — 2026-08-30

This report records autonomous work completed after Phase 2.5. It is updated after each functional
slice so that branches, commits, database changes, API contracts, security decisions, and verification
results remain auditable.

## Delivery convention

Each slice is developed on a function-named branch, compiled, committed, pushed to `origin`, merged
into `main`, and then `main` is pushed. Full regression and expanded test work are deferred at the
owner's request; a production-code compile/package check remains mandatory before integration.

## Slice 1 — Public course curriculum

- Branch: `feature/course-curriculum`
- Database: Flyway V4 adds ordered `course_sections` and `lessons`, cascade cleanup, uniqueness and
  non-negative duration/order constraints, and query indexes.
- API: `GET /api/v1/courses/{slug}/curriculum` returns ordered sections and lessons for published
  courses.
- Security: public curriculum metadata exposes `contentUrl` only for preview lessons. Locked lessons
  return a null URL, preventing the catalog endpoint from disclosing protected learning content.
- Architecture: curriculum persistence stays inside Catalog's outbound adapter and is exposed through
  a named public use-case contract. JPA entities remain isolated from domain/application consumers.
- Verification: `mvn -Dmaven.test.skip=true package` passed with Java 21; full tests intentionally
  deferred.

## Pending slices

- Frontend learning experience integration.

## CI correction — module isolation dependencies

- Branch: `fix/learning-module-test-boundaries`
- Root cause: new Learning services consumed Catalog's `CourseLearningContentLookup` and introduced a
  lesson-progress repository, but the isolated Spring Modulith test did not provide those boundary
  doubles. Production compilation passed while the module ApplicationContext failed during `verify`.
- Correction: mock both new boundaries in `LearningModuleIntegrationTest`.
- Process correction: all remaining slices must pass the complete existing test suite and production
  build before push/merge; skipping new test development no longer means skipping regression execution.

## Test hardening — learning content and progress

- Branch: `test/learning-content-progress`
- Added unit coverage for preview access, locked lesson authorization, default resume state, duration
  validation, and timestamped completion persistence.
- Verification gate: complete Maven verify locally, followed by GitHub Actions before accepting `main`.

## Phase 3 quality gate

- Branch: `build/coverage-quality-gate`
- JaCoCo is attached to the Maven test lifecycle, publishes HTML/XML/CSV reports during `verify`, and
  blocks regression below the 70% whole-application line baseline. The first full Docker-backed run
  measured 73.89% line coverage.
- The baseline is intentionally incremental: generated/persistence/configuration code remains visible
  in the denominator, and future phases should raise the threshold alongside business logic coverage.

## Slice 2 — Authenticated lesson access

- Branch: `feature/lesson-access`
- API: `GET /api/v1/me/courses/{courseSlug}/lessons/{lessonId}` returns player-ready lesson content.
- Authorization: JWT identity is scoped to the request. Preview lessons are accessible to authenticated
  users; locked lessons require an ACTIVE or COMPLETED enrollment and reject cancelled/missing
  enrollments with 403.
- Module boundary: Learning consumes Catalog only through the named `learning-content` interface;
  Catalog persistence entities are not exposed.
- Verification: production package compile with tests skipped.

## Slice 3 — Transactional lesson progress and resume

- Branch: `feature/lesson-progress`
- Database: Flyway V5 adds one progress row per enrollment/lesson, non-negative position constraints,
  cascade cleanup, and a recent-progress index.
- API: authenticated GET/PUT progress endpoints support resume position and completion state.
- Consistency: PostgreSQL upsert makes repeated saves idempotent; completion is monotonic and cannot be
  accidentally reverted. The application rejects negative positions and positions beyond lesson duration.
- Security: progress reads/writes derive ownership from the JWT user and require a non-cancelled enrollment.
- Transactionality: the use case is wrapped by an inbound transaction adapter; application code remains
  independent of Spring transaction APIs.
- Verification: production package compile with tests skipped.
