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

- Authenticated lesson access and enrollment authorization.
- Transactional lesson progress and resume.
- Frontend learning experience integration.
