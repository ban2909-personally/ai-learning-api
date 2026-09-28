# ADR-020: English learning and practice assessment

Status: Accepted; foundation and Writing review delivered to CI-green main (2026-09-28).

## Context

The product teaches English. Existing programming/Korean preview content was a mistaken product assumption, not a domain requirement. Community, catalog, classes and flashcards remain useful, but their learning content and navigation must be oriented to English. The supplied PREP screenshots are interaction references, not a request to reproduce their branding, copyrighted material, or official scoring.

ETS treats TOEIC Listening & Reading separately from Speaking & Writing. We therefore model skill-specific practice results, not a fabricated official TOEIC score. Speaking is explicitly out of scope. Writing cannot be reliably graded by exact string comparison and requires a separate review workflow before any score is shown.

## Decision

- Add an `assessment` bounded context with its own exam, item, attempt and response tables. It exposes use cases and contracts; web and JDBC are adapters. No catalog/learning entity is shared or queried directly.
- Published English practice exams are visible to guests. Starting/submitting an attempt requires an active authenticated student, lecturer/instructor, leader or admin; an attempt belongs to exactly one user. The HTTP role gate and the participant use case both enforce this; an authenticated GUEST account can only discover exams.
- Listening and Reading items are objectively scored after submission. Answers and explanations are never returned in the take-exam payload. The result API returns only the owner’s submitted attempt.
- Writing is a response to a prompt, persisted with status `PENDING_REVIEW`; it is not silently auto-scored. Active lecturers, instructors, leaders and admins can review another user's submitted answer once, assigning 0–5 to task response, coherence, vocabulary and grammar with required feedback. The 20-point total is a local practice rubric, separate from objective Listening/Reading counts. A combined official TOEIC/IELTS band is not calculated.
- Media is a URL/reference from published content. A managed audio-upload workflow and integrity controls follow the existing MinIO adapter with the project’s sub-10 MB upload limit; arbitrary learner-supplied URLs are not accepted as answers.
- UI uses lazy-loaded routes, a responsive section navigator, audio playback where supplied, passage/question split, autosave and result review. The reference screenshots inform layout only.

## Boundaries and invariants

- Authoring/publishing is a separate use case from taking an exam; this first slice ships a reviewed sample fixture and leaves authoring API/UI for the next increment.
- A submitted attempt is immutable. A student may own several attempts but may not read another student’s answers/results.
- The reviewer queue excludes the reviewer's own answers. The database enforces one review per answer and review creation is atomic; conflicting second submissions fail. Only the attempt owner sees the reviewed rubric in their result. Review changes are not supported without an explicit audit/correction workflow.
- Server validates item membership, section, type and answer length; it computes objective results. Browser calculations are display-only.
- Empty Writing responses are recorded as unanswered, not graded. Reading/Listening unanswered items count as incorrect.
- All timestamps are UTC in storage. No external score labels or conversion tables without a licensed, validated specification.

## Migration / verification checklist

- [x] Preserve existing uncommitted work and create `feature/english-exam-practice` branches.
- [x] Record domain and score decisions before schema changes.
- [x] Flyway migration, sample English content, use cases, persistence and security boundaries.
- [x] Lazy frontend routes, responsive take-exam and review screens; replace misleading programming-focused copy.
- [x] Unit, repository/integration, HTTP security, Modulith/ArchUnit and frontend tests.
- [x] Flyway V17, reviewer-role boundary, human rubric, reviewer queue and learner feedback display.
- [x] Run full backend/frontend checks before push.
- [x] Confirm remote CI is green before merge to `main`; main CI also passed. Exact delivery evidence is in the development report.

Existing course or community records are not deleted or rewritten by this migration. Legacy preview seed data is revised separately, idempotently, after assessment is validated.

## Follow-up increments

1. Authoring/publishing workflow for Listening/Reading/Writing exams and managed audio in MinIO.
2. Reviewer assignment, moderation and audited score-correction workflow as the queue grows.
3. Larger licensed/original content bank, timed exam policy, analytics and validated scoring models.
