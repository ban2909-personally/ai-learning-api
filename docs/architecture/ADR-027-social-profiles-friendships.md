# ADR-027 — Social profiles, friendships and contextual messaging

Status: accepted for implementation, 2026-09-29.

## Context and scope

The public directory currently exposes only an active account's ID/name. Profile pages
show only public posts; direct chat requires an email. The homepage sidebar duplicates
learning navigation already available in the global header. Preview categories include
four legacy programming categories with zero published courses.

This increment adds a real public profile, editable introduction/location/website and
cover theme, friendship requests, a friends/request screen and messaging by account ID.
Cover artwork uses CSS themes and an initial avatar, not fabricated photographs or
personal information. Profile photo/cover uploads are a separate future increment;
existing post image/video uploads, the 10 MB limit and 14-day post retention stay intact.

## Boundaries and contracts

- Identity owns profile information in `identity_social_profiles` and exposes a named
  `identity::access` use-case boundary and `identity::contract` value contract.
- Community owns friendship pairs, request decisions, counts and viewer-relative state.
  Its application combines identity contracts with community output ports. Neither
  controller nor application accesses another module's persistence/entity.
- Directory ID/name and existing `/community/people/{id}` responses stay unchanged.
  A new `/community/people/{id}/profile` returns profile information, actual active
  friend/mutual counts and `SELF/NONE/OUTGOING/INCOMING/FRIENDS` relationship.
- Authenticated actors edit only their own profile through `/community/profile`; public
  fields are deliberately opt-in. Email, account roles/status and private posts are absent.
- All active roles may request/accept/cancel/remove friendships. Anonymous users can
  view public profiles but must log in to connect/chat. Requests and friend lists are
  private to their account; a profile exposes counts, not a downloadable public graph.
- The canonical UUID pair is unique, non-self and references users. Mutations serialize
  through database row locks. Only the recipient accepts; either participant may remove.
  Repeated same-direction requests are idempotent, reverse requests require explicit
  acceptance, and concurrent cross requests must not auto-accept.
- Chat gains an ID-based start endpoint; the existing email API remains compatible.
  Opening the chat composer does not create/send anything. Existing one-message request,
  recipient acceptance, declined state, IDOR protections and client-ID retries remain.
- Public catalog gains opt-in `publishedOnly=true` categories using a bounded EXISTS
  query. Authoring retains the complete category API; no category/course is deleted.

## UI convention

Lazy-loaded profile and friends routes; author/avatar links open profiles. Profile has
cover, avatar, actual counts, introduction, editable form, posts and about tabs. Chat
launcher is shared through a small React context, not DOM events or email exposure.
Homepage local navigation and calls to action are social-only. Global header keeps
learning routes. Scoped CSS must work at 320/768/1440 px, with keyboard labels, pending
states, cancellation of stale reads and clear errors. Render user text as text, never HTML.

## Migration and delivery checklist

- [x] Inspect both git worktrees and all initial diffs, including pom.xml. Preserve the
  unrelated `performance/ai-mentor.js` edit; create `feature/social-profiles-friendships`.
- [x] Record this ADR before source changes. No big-bang rewrites or new dependencies.
- [x] Append V27 only: profile table and constrained/indexed friendship pairs. Existing
  migrations, accounts, courses, attempts and MinIO/Redis configuration remain unchanged.
- [x] Implement identity profile boundary, community friendship policy/store/use case,
  ID-based chat and published-category filtering incrementally.
- [x] Implement responsive profile/friends/chat-entry UI and social-only home navigation.
- [x] JUnit/Mockito, MockMvc/Security/Testcontainers, Modulith/ArchUnit; all existing tests,
  coverage gate, FE unit/build and Selenium smoke must pass without skipped checks.
- [x] Back up preview PostgreSQL before V27; verify restore listing and durable backup copy.
  Restart only the validated project API process and verify migrations/data counts.
- [x] Exercise real accounts, request/accept/remove, private access and ID chat in browser;
  visually inspect responsive layouts, not just overflow assertions.
- [ ] Produce a Markdown change/security/test report. Commit only task-owned files, push
  the feature branches and verify all CI jobs for exact final SHAs. Main stays unchanged
  pending fresh approval; do not bypass previously denied main updates.

## Trade-offs

Friendship is not authorization to bypass chat consent or private group approval. Counts
exclude disabled accounts. Paginated friends/requests are bounded to 20 plus a sentinel;
no per-card remote lookup or unbounded graph expansion. Rate-limiting and load testing of
the new friendship workload remain production readiness work, not claimed guarantees.
