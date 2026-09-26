# Community homepage — implementation record

Date: 2026-09-26. Repositories: `E:\ai-learning-api` and `E:\ai-learning-web`.

## Product behavior

The root route is now a knowledge-sharing homepage for every role and anonymous visitors.
Login and registration return to `/` unless the user was following a protected deep link.
The existing header links for courses, flashcards, learning, authoring and administration remain.
The homepage offers a public chronological feed, text composer, like/unlike, comment and
one-level reply, repost with an optional caption, and links to groups/pages. Posts and comments
render as text. Existing learning features and API contracts remain in place.

Any active account, including one with the `GUEST` role, can post to the personal feed and create
a group or page. Anonymous visitors can read public content but must sign in before writing;
this gives each write a stable owner and supports moderation. Public-group joins are immediate.
Private-group joins await manager approval, and private posts/comments are hidden from
nonmembers. A page is public and only its OWNER/ADMIN may publish page posts. Active accounts
can react and comment on page posts. The creator is its permanent OWNER; OWNER may promote
members to ADMIN. Managers can invite existing accounts, approve/reject pending membership,
remove members and moderate content; neither an ADMIN nor OWNER can remove the OWNER.

## Architecture and storage

ADR-019 defines the `community` bounded context and migration plan. The module contains pure
domain policy, an application use case, an output port, REST input adapters and a JDBC persistence
adapter. It reaches identity through the named `identity::access` boundary; it does not import
identity entities or repositories. Spring Modulith and ArchUnit tests include the new package.

Flyway V15 adds only `community_spaces`, `community_members`, `community_posts`,
`community_post_likes` and `community_comments`, with foreign keys, uniqueness, state checks
and feed/space/member indexes. Feed queries use a bounded page and a `(created_at,id)` cursor.
Comments and manager member lists are paged. Post/comment removal is soft. No existing course,
flashcard or account table was changed. `pom.xml` was inspected and left unchanged.

The web routes and existing large pages are lazy-loaded. Feed pages are fetched as the user
scrolls, with a manual **Xem thêm** fallback; comments fetch only when expanded. Group/page
directory and member management offer explicit pagination. Layout was checked at 320, 390,
768 and 1440 px; the shared header is unchanged apart from adding a Hội nhóm link.

## Local preview

The optional `scripts/seed-community-preview.sql` checks that the selected database is
`ai_learning_preview` and is not a Flyway migration. It inserts five sample posts, four spaces,
six reactions and three comments/replies using the pre-existing local demo accounts. The
preview API applied V15 and returned five public posts and four spaces; health was `UP`.
Four posts created during an exploratory browser test were soft-removed by exact ID, leaving
the five sample posts visible. Preview is at `http://localhost:5173/`, API at
`http://localhost:8080/` (bound to 127.0.0.1). All five role-specific demo logins from the
prior preview remain available. This is example content, not completed teaching material.

## Verification and security

- Backend `mvn verify`: 230 tests, zero failures/errors/skips, including community policy and
  REST/PostgreSQL/Flyway scenarios. JaCoCo instruction coverage: 85.61%; application SBOM
  generated. The application also passed a Docker image build.
- Frontend test suite and production TypeScript/Vite build passed after adding community UI
  tests for anonymous viewing and authenticated Guest publishing, liking and commenting.
- Frontend CI-equivalent `pnpm test:e2e` responsive Selenium smoke passed with the new lazy
  homepage and the existing learning dashboard fixture.
- Browser smoke passed for all five role logins via the homepage and their legacy workspaces.
  A separate community browser smoke passed public feed, group/page directory and detail,
  Guest login and 320/390/768/1440 px no-overflow checks. A direct authenticated Guest API
  post/delete check passed. Interactive headless ChromeDriver clicks were inconsistent, so
  write behavior is gated by unit/integration tests and not claimed as browser-verified.

The new endpoints check current account activity through `AccountAccess` and re-evaluate
private-space visibility and member/manager authorization in the use case. React does not
inject HTML from posts or comments. Only public originals may be reposted to the public feed.
The existing strict `< 10,000,000`-byte MinIO upload policy is unchanged; social posts in this
slice are text-only, so no new media ingestion path was opened.

## Change map

- API: `src/main/java/com/ailearning/platform/community/**`,
  `src/main/java/com/ailearning/platform/identity/api/usecase/access/AccountAccess.java`,
  `src/main/java/com/ailearning/platform/identity/application/service/impl/AccountAccessService.java`,
  `src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java`,
  `src/main/resources/db/migration/V15__create_community_network.sql`,
  `src/test/java/com/ailearning/platform/community/**`,
  `scripts/seed-community-preview.sql`, ADR-019 and this report.
- Web: `src/features/community/**`, `src/app/App.tsx`, `src/components/AppHeader.tsx`,
  `src/features/auth/AuthPage.tsx`, `src/styles.css`,
  `scripts/selenium-community-smoke.mjs`, `scripts/selenium-workspace-smoke.mjs`
  and `scripts/selenium-smoke.mjs`.

The pre-existing uncommitted `performance/ai-mentor.js` change belongs to another task and
must not be staged, formatted, committed or overwritten by this delivery.

## Deliberate limits and next gates

This is a social foundation, not full Facebook parity. It does not yet include media
attachments, direct messages, notifications for invitations/replies, content reports,
automated spam/rate controls, ranking, ownership transfer, group deletion or production-scale
search tuning. Before a broad public launch, these need product decisions, abuse prevention,
load testing and operational moderation. The local demo password `123456` is never production
seeding or a relaxation of normal account policy.
