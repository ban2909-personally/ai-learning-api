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

- Transactional lesson progress and resume.
- Frontend learning experience integration.

## Slice 2 — Authenticated lesson access

- Branch: `feature/lesson-access`
- API: `GET /api/v1/me/courses/{courseSlug}/lessons/{lessonId}` returns player-ready lesson content.
- Authorization: JWT identity is scoped to the request. Preview lessons are accessible to authenticated
  users; locked lessons require an ACTIVE or COMPLETED enrollment and reject cancelled/missing
  enrollments with 403.
- Module boundary: Learning consumes Catalog only through the named `learning-content` interface;
  Catalog persistence entities are not exposed.
- Verification: production package compile with tests skipped.
