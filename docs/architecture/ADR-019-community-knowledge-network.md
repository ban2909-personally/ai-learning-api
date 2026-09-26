# ADR-019: Community knowledge feed and member-owned spaces

Status: implemented, 2026-09-26.

## Context and outcome

The current root route is a course marketing page for visitors and a role-specific shortcut page
after login. The product owner wants one interactive knowledge-sharing homepage for every role,
without removing the existing course, learning, authoring or administration navigation.

Create a `community` bounded context for posts, reactions, comments, shares and member-owned
spaces. It may depend on the named `identity::access` API, not identity persistence internals.
The React community pages load as route-level lazy chunks. The feed loads more results when its
sentinel enters the viewport; the API remains paginated and indexed.

## Access and behavior

- Anonymous visitors may read the public feed, public spaces and public comments. They must
  sign in before writing. Authenticated GUEST accounts can post, react, comment, share and join.
- Login and registration land on `/`, while the existing header options and deep links remain.
- A space is a GROUP or PAGE. A page is public; a group may be public or private. Its creator is
  its immutable OWNER and active member. OWNER may appoint ADMIN members. OWNER and ADMIN may
  invite existing accounts, approve/reject requests and remove ordinary members. No manager may
  remove or demote the OWNER.
- Public spaces are searchable. Public-group joins activate immediately; private-group joins
  wait for approval. Invitations require acceptance. Private posts and comments are visible only
  to active members. Group posting requires membership. Only PAGE managers publish page posts;
  active accounts may react/comment on public page posts.
- A personal feed post is public. A share is a new post referencing a public original; private
  posts cannot be shared into the public feed. Likes use explicit add/remove endpoints for safe
  retries. Replies have one level of nesting for predictable moderation and rendering.
- Post/comment bodies are plain text, length-limited and rendered as text. This phase does not
  ingest user media or copy Facebook assets/branding. Any future media path must obey the
  existing strict <10 MB upload rule and have content scanning/moderation design.

## Data and safety

Flyway V15 adds only new tables: `community_spaces`, `community_members`,
`community_posts`, `community_post_likes`, `community_comments`. Foreign keys, unique
memberships/reactions and feed/space indexes protect integrity and bounded reads. Private-space
visibility and current account status are rechecked in the service on every write/read that
requires authorization. Deletion is soft for posts/comments and preserves reply context.

The feature is a maintainable social foundation, not a claim of full Facebook parity. Algorithmic
ranking, messaging, notifications, media feeds and large-scale anti-spam tooling require later
decisions and independent gates. Public feed uses chronological order.

## Migration and verification checklist

- [x] Inspect Git diff in both repositories, `pom.xml`, routes, existing identity boundary and tests.
- [x] Preserve the unrelated uncommitted `performance/ai-mentor.js` change.
- [x] Add V15 migration, policy, use cases, persistence adapters and API within `community`.
- [x] Add public feed, text posts, like/unlike, share, comment/reply and moderation rules.
- [x] Add GROUP/PAGE creation, search, join, invite, approval, removal and role management.
- [x] Route all roles to a responsive, lazy-loaded homepage while retaining current navigation.
- [x] Seed the isolated local preview with knowledge posts and spaces; never seed production.
- [x] Verify policy, REST authorization, PostgreSQL/Flyway, ArchUnit/Modulith and frontend tests.
- [x] Verify browser flows and mobile/tablet/desktop layout; document gaps honestly.

Release procedure after this ADR: push only after local verification, inspect feature CI, then
merge/push `main` only when both repositories' gates are green. Do not stage unrelated work.

## Reference boundary

Meta's public group help describes membership, requests and admin responsibilities. Those
functional patterns inform the workflow, but this implementation is original to the learning
platform and deliberately does not reproduce Meta's interface or policies verbatim.
