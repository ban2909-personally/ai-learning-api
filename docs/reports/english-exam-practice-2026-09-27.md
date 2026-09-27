# English assessment vertical slice · 2026-09-27

## Outcome

- Corrected product direction from programming/Korean sample content to English learning.
- Added an isolated `assessment` module and Flyway V16 with an original 3-skill practice exam.
- Guests can discover and inspect the exam; authenticated active users can start, save, submit and review only their own attempt.
- Listening/Reading are graded as raw correct answers with explanations shown after submission. Writing is persisted as `PENDING_REVIEW` and excluded from the objective score. Speaking is out of scope.
- Added lazy React routes for exam discovery, introduction, responsive take-exam workspace and result review. The demo listening passage uses browser speech synthesis; managed audio media and proctoring are later increments.
- Updated isolated preview seed and applied a guarded, idempotent correction to `ai_learning_preview`: 6 courses, 4 groups and seed flashcards now focus on English. No account, enrollment, post, comment or volume was deleted; 6 demo accounts and 10 posts remain.

## Verification

- Backend full `mvn verify`: 236 tests, 0 failures, 0 errors, 0 skipped; application SBOM generated.
- New Testcontainers PostgreSQL/MockMvc test proves guest discovery, no answer-key leak, ownership, submission lock and Writing pending status.
- Spring Modulith and ArchUnit architecture checks passed; isolated assessment module bootstrap passed.
- Frontend `pnpm build`, `pnpm test` (45 tests), `pnpm test:e2e` (320/768/1440 px smoke) passed.
- Local preview DB Flyway at V16; public exam endpoint returned the original English sample. Preview correction rerun changed zero rows.
- Real browser flow with `student@demo.local`: login → start attempt → answer Listening → open Reading/Writing → submit → view 1/4 result; checked desktop screenshots of catalog, attempt and result.

## Known limits / next steps

- Writing awaits a human review workflow and score rubric; do not present it as auto-graded.
- The sample has 4 objective questions and 1 Writing prompt. It is a functional vertical slice, not a full TOEIC/IELTS test or official scoring system.
- Existing built-in programming categories remain in the schema for backward compatibility; they are not deleted or silently remapped. Authoring, content licensing and category retirement need separate migration decisions.
- Remote CI is required before merging to `main`.
