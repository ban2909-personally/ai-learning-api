# ADR-026 — Community discovery and free English resources

Status: Accepted for incremental implementation, 2026-09-29.

## Scope and decisions

- Homepage search starts at one non-whitespace character, case/accent insensitive, literal substring matching. Exact/prefix matches rank before other matches; stable name/UUID ordering. Empty input does not enumerate people.
- Debounce 300 ms, cancel obsolete requests, ignore late responses, explicit error/retry/empty states. Search returns up to 20 spaces and 20 public profiles per page, at most 100 pages. Group/page labels remain explicit. Private space results reveal only existing directory metadata, never posts/members/chat.
- Identity owns public profile lookup/search (UUID and display name only). Community consumes identity's named access interface, never its table/entity from a community adapter. Disabled/deleted users are excluded. Public profile page displays only public posts, never private-group posts even to an approved viewer.
- PostgreSQL V26 adds trusted unaccent/pg_trgm extensions, immutable search-key function with fixed dictionary, and expression indexes owned by the respective tables. Literal LIKE escaping handles percent, underscore and backslash; all parameters bound. One/two-character searches may scan: do not claim trigram acceleration for these. Production requires DB owner extension privileges and later representative-load validation.
- A lazy frontend `/resources` route holds a small reviewed link catalogue with source, skill, access notes and verification date. No new business module/database table or scraper for a static curated list. Original summaries only; no rehosting, iframe, autoplay, third-party thumbnails or copied tests. Free resource sections are distinguished from paid offerings on the same sites. Sources: British Council, Cambridge, ETS, BBC and VOA, verified against their official pages.
- Existing authentication, public/private approval, media retention, exam scoring and database content remain unchanged. Speaking and movies remain out of scope.

## Migration and workflow

1. Preserve user-owned `performance/ai-mentor.js`; inspect complete diffs including pom; no dependency upgrades.
2. Create `feature/community-search-learning-resources` from the delivered feature branch, without modifying main.
3. Implement identity public boundary, community discovery and public-author feed filter, then REST validation/security and database migration.
4. Implement responsive homepage discovery, public profile and free resource catalogue with tests.
5. Run targeted tests, full Maven verify, frontend tests/build, browser smoke; backup preview DB before V26, refresh localhost safely and verify real API/UI.
6. Commit coherent tested increments, push feature branches, inspect exact-SHA CI. Do not merge/push main without fresh approval.

## Acceptance checklist

- [x] One-character substring, progressively narrowing results, case/accent folding, exact/prefix priority, stable pagination.
- [x] Literal `%`, `_`, backslash; bounded query/page; blank input; invalid UUID; no email/role/status leaks.
- [x] Inactive users excluded; private-group content excluded even for owner. Existing pending/expiry predicates retained; existing regression gates pass.
- [x] Debounce/cancellation, keyboard/touch links, empty/error/retry states; 320/768/1440 px without overflow and visually inspected screenshots.
- [x] Free English sources/filter/search with origin links and explicit paid/registration caveats.
- [x] Local regression gates, architecture/Modulith, Flyway and actual localhost checks pass.
- [ ] Report changes, commit SHAs and CI evidence; preserve user's uncommitted file and main refs.

Remote CI is pending at document creation. Final delivery evidence is recorded separately in the local receipt to avoid changing the verified commit SHA just to update CI status.

Technical references: [PostgreSQL unaccent](https://www.postgresql.org/docs/current/unaccent.html), [PostgreSQL pg_trgm](https://www.postgresql.org/docs/current/pgtrgm.html). Short patterns without extractable trigrams do not benefit from the index; query timeout is 2 seconds per directory query. Rate limiting and representative production load testing remain deployment work, not a claimed result of local smoke tests. Expression indexes in V26 are transactional, not concurrent; plan index-build locking for large production tables.
