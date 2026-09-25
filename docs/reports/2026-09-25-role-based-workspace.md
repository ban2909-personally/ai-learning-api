# Role-based learning workspace — development record

Date: 2026-09-25. Repositories: `ai-learning-api` and `ai-learning-web`.
Scope: local preview and additive product functionality; this is not a production data migration.

## Why the first preview looked empty

The UI was backed by an isolated `ai_learning_preview` PostgreSQL database. Before this work it
contained one existing user and four categories, but no courses or lessons. The application
infrastructure was present; course authoring and the role-specific workspace were not yet exposed
in the web app. The separate `ai_learning` database in the same local PostgreSQL instance did not
have a `users` table. No other PostgreSQL instances were inspected.

The preview now contains six users (the original user was preserved), four categories, six
published example courses, six sections, twelve lessons with playable example video, one student
enrollment, and four private flashcard decks with twelve cards. These are programming-course
examples with generic test-pattern video, not finished teaching content. The seed is restricted
to the named preview database and is not in Flyway's normal migration path.

## Demo access and permissions

All five preview accounts use password `123456`:

| Account | Role | Workspace |
| --- | --- | --- |
| `guest@demo.local` | GUEST | Published course catalog only |
| `student@demo.local` | STUDENT | Enrollment, lessons, personal flashcards |
| `lecture@demo.local` | LECTURE | Own course drafts, curriculum and video upload |
| `leader@demo.local` | LEADER | Course review and publication |
| `admin@demo.local` | ADMIN | Dashboard, account management and course oversight |

`INSTRUCTOR` remains accepted for previously existing lecturer accounts. Anonymous visitors can
browse published courses. Backend authorization is authoritative, including current database
role/status checks for account administration, course authoring and media upload. Role or status
changes revoke refresh sessions; already-issued access tokens have their ordinary short lifetime,
so legacy endpoints relying only on JWT claims do not acquire immediate global revocation.
The last active administrator cannot be disabled or demoted. These weak passwords must never be
seeded in production; normal account password rules were not relaxed.

## Implemented changes

- Identity: Flyway V13 roles and permission mappings, account listing/statistics/create/update,
  status and role guardrails, current-account access boundary, administration REST endpoints.
- Catalog: draft creation, sections and lessons, author ownership, submit/review/publish/reject,
  transaction-safe cache invalidation, video upload authorization and strict size limit.
- Media: payload must be smaller than **10,000,000 bytes**. Exact-boundary and larger files are
  rejected by backend policy; the servlet and web client use the same threshold. Existing media
  remains readable. Example videos were uploaded through the real API into local MinIO.
- Flashcards: Flyway V14 private decks/cards, owner-scoped CRUD API, front/back study interaction.
- Web: responsive public home/catalog, role-aware navigation and landing pages, admin dashboard,
  account table, lecturer course studio and review queue, flashcard editor/study flow.

The supplied KSH images informed navigation density, sidebar layout, statistics and tables.
Academic semesters, subject codes, class administration, question bank and system-prompt settings
are **not** implemented. They are intentionally not shown as working controls. The current domain
remains programming courses rather than a copy of the screenshot's institution or data.

## Verification and changed-file map

Backend implementation is in `src/main/java/com/ailearning/platform/{identity,catalog,flashcard}`,
`platform/security/SecurityConfig.java`, Flyway V13–V14 and the local `scripts/` seed/publish helpers.
Frontend implementation is in `src/features/{admin,authoring,auth,catalog,flashcards,home,learning}`,
`src/components/`, `src/app/App.tsx` and `src/styles.css`. Architecture rationale and migration
checklist are in `docs/architecture/ADR-018-role-based-learning-workspace.md`.

The complete backend `mvn verify` gate passed: 222 tests, zero failures/errors/skips,
including unit, authorization, PostgreSQL/Flyway integration, Redis cache, Modulith and ArchUnit.
JaCoCo instruction coverage is 86.08%. Frontend 38 tests, TypeScript/Vite production build and
responsive browser smoke passed.
Real browser logins succeeded for all five roles; 390 px mobile and 320/768/1440 px responsive
checks found no horizontal overflow. The browser reached the lecturer's course-creation form
and flipped a student's flashcard. A student lesson-media range request returned HTTP 206.
Headless ChromeDriver did not dispatch pointer clicks for some interior controls, so the
browser smoke visited the form through its verified URL and exercised flashcard DOM click
handlers; native pointer interaction on a user device remains a manual acceptance check.

## Git delivery evidence

All code and documentation were committed on `feature/role-based-learning-workspace` and pushed
before merging. Backend feature commits: `7d2eea4` (implementation), `6bc5c6e` (ADR and report).
Frontend feature commit: `96d998b`. The product merge commits are `a28417e` (backend main) and
`0d9d9cb` (frontend main); the merge trees exactly matched their tested feature trees.

- Backend feature [CI run](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36150817393): success.
- Frontend feature [CI run](https://github.com/ban2909-personally/ai-learning-web/actions/runs/36150843888): success.
- Backend main [CI run](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36151937234): success, including recovery, container and performance gates.
- Frontend main [CI run](https://github.com/ban2909-personally/ai-learning-web/actions/runs/36152023242): success.

Backend main was already ten commits ahead of `origin/main` before this feature; those earlier
media-bandwidth commits were published with the product merge. The backend feature and main CI
runs validated their combined tree. The pre-existing uncommitted
`performance/ai-mentor.js` work remains local and was never staged or committed by this delivery.

## Local preview replay

After migrations, run `scripts/seed-local-preview.sql` only against `ai_learning_preview`.
Then run `node scripts/publish-local-preview.mjs --confirm-local-preview [video.mp4]` while the
local preview API is available at `http://localhost:8080`. The publisher requires a valid MP4
smaller than 10 MB (default: `target/preview-lesson.mp4`), uploads it through the lecturer API,
publishes via the leader API and enrolls the demo student. Both steps are designed to be
repeatable for the demo data. Do not run them against a shared or production environment.

The preview UI is served at `http://localhost:5173/`; the API health endpoint is
`http://localhost:8080/actuator/health`. Local screenshot evidence is kept under `target/`
and is not committed to Git.
