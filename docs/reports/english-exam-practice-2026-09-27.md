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

## Writing review increment

- Added Flyway V17 and a one-time, human Writing review for submitted responses. A lecturer, instructor, leader or admin scores task response, coherence, vocabulary and grammar (each 0–5) and leaves required feedback. A reviewer cannot grade their own answer.
- Added a reviewer queue and rubric page, restricted in both Spring Security and the use case. The learner sees the review in their own result; Listening/Reading objective score remains separate and unchanged.
- The review insert is atomic and duplicate grading fails. The queue is paged at 30 items; assignment, moderation, score corrections and audit history remain future work.
- This is a local practice rubric, not an official TOEIC or IELTS score. Speaking remains out of scope.
- Retired the frontend's unreferenced programming-focused landing component and its test; the active community homepage is retained. Architecture decision uses ADR-020 to avoid an existing ADR-016 identifier.

### Final local verification (2026-09-28)

- Exact final backend `mvn verify`: 240 tests, 0 failures, 0 errors, 0 skipped; packaging and the coverage gate passed. An earlier packaging attempt failed because the running preview held the target JAR open, not because an assertion failed. The preview now runs a copy under `target/preview-runtime`.
- Frontend build and all 47 tests (22 suites) passed. Selenium smoke passed at 320/768/1440 px, including the role-restricted Writing reviewer queue and rubric layout.
- Preview health is UP and Flyway is at V17. Student submission `ed19cedf-9994-466a-a285-c8ef0fd00bc3` was reviewed through the frontend form as `lecture@demo.local`; the student browser result showed 16/20 and the persisted feedback. Objective score stayed 0/4.
- Headless Chrome 154/WebDriver did not consistently dispatch pointer/keyboard input to the lower form. Acceptance used browser-native text insertion and validated `requestSubmit`, then a fresh student browser session. This proves form handling, persistence and owner feedback, but is not evidence of a complete pointer-only acceptance test. No application workaround or disabled validation was added.
- Demo accounts and existing user/community/course data remain intact. The user's unrelated `performance/ai-mentor.js` remains uncommitted and excluded from this delivery.
- Added explicit authenticated-GUEST HTTP regression coverage and participant-role validation at the use-case boundary. The existing shared HTTP matcher already restricted participation; no duplicate matcher was added. Guests discover exams but cannot start/read attempts or enter the review queue.

## Main changed files

- Backend: `assessment/**` (use cases, contracts, domain grading, JDBC persistence and web adapter), `platform/security/SecurityConfig.java`, Flyway V16/V17, assessment unit/module/HTTP tests.
- Frontend: `features/practice/**`, lazy routes in `app/App.tsx`, role-aware `components/AppHeader.tsx`, English community/catalog copy and `scripts/selenium-smoke.mjs`.
- Data/docs: isolated English preview correction and seed scripts, ADR-020, migration checklist and this report. No production business records or Docker volumes were removed.

## Known limits / next steps

- Writing is graded by a human reviewer, not automatically. The initial queue has no assignment workflow or override of a saved review.
- The sample has 4 objective questions and 1 Writing prompt. It is a functional vertical slice, not a full TOEIC/IELTS test or official scoring system.
- Existing built-in programming categories remain in the schema for backward compatibility; they are not deleted or silently remapped. Authoring, content licensing and category retirement need separate migration decisions.
- Foundation and human review are delivered. Authoring, managed audio, exam timing/history and reviewer assignment remain on the migration checklist; the whole platform is not complete.

## Git and remote delivery evidence (2026-09-28)

Both repositories used `feature/english-exam-practice`; feature CI passed before merge. The merge trees were identical to their verified feature trees before pushing main. No pull, stash, force-push or unrelated commit was used.

| Repository | Feature commits | Main merge | Feature CI | Main CI |
| --- | --- | --- | --- | --- |
| Backend | `0d80fbe`, `582b4f5`, `5e8cbcd` | `c05c901` | [36368915472](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36368915472), 11/11 successful | [36369487328](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36369487328), 11/11 successful |
| Frontend | `e72f578`, `10dfb35` | `f78a48c` | [36368274928](https://github.com/ban2909-personally/ai-learning-web/actions/runs/36368274928), successful | [36369488182](https://github.com/ban2909-personally/ai-learning-web/actions/runs/36369488182), successful |

Backend gates include verification/SBOM, production image security, PostgreSQL and MinIO recovery, and all seven existing performance profiles. Passing these regression gates is not a production capacity or zero-vulnerability claim. The SHA-256 of the untouched user performance file was identical before and after merge.
