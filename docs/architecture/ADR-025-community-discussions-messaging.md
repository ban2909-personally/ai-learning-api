# ADR-025: Community discussions, media retention and direct messaging

Status: Accepted incremental specification, 2026-09-28. Unchecked work is not delivered.

## Diagnosis and delivery order
The real browser upload transport currently hardcodes PUT, while community media creation requires POST. API-only acceptance missed this difference; the API logs confirm 405 PUT. Make the upload method explicit without breaking existing lesson-media PUT. Add transport regression and a real composer upload acceptance before claiming fixed.

1. Fix and verify upload transport.
2. Add scoped 14-day media retention with additive migration, deadline authorization and retryable MinIO cleanup.
3. Add post link attachment, accessible color customization, polls/elections and persisted votes.
4. Add private direct chat inbox/request/thread UI and participant-only persistence.
5. Full verify/tests/build/responsive and real-browser acceptance; feature commit/push CI. Main mutation remains blocked until fresh explicit permission; do not bypass review.

## Business rules
- Strict under 10,000,000 bytes remains; one image/video per post. Media posts expire exactly 14 days after upload, including pending/rejected/removed posts. Text-only/poll/link posts do not expire under this rule.
- At expiry, the original media post is no longer readable/interactable; shared original media/body is masked. Soft-remove posts/history, hard-delete only its community-owned MinIO object. No shared-bucket lifecycle rule (lesson files share the bucket). Bounded durable cleanup claims/leases and retries; failed storage deletion must not expose expired content.
- Media uploads created before migration inherit expires_at = asset.created_at + 14 days; no retroactive reset extending their lifetime. Back up preview before migration. Never delete unrelated objects/containers/volumes.
- One external HTTP(S) link attachment per post; no arbitrary HTML, javascript/data URLs, credentials in URLs, or server-side metadata fetch. Safe rel/noopener/noreferrer link display. Public/private and moderation rules are unchanged.
- Optional background/foreground colors validated as hex and readable contrast. Apply to text body only, not arbitrary CSS/HTML or media controls. Normal theme is the default.
- POLL and ELECTION are single-choice discussions in a post, 2–8 distinct options, bounded question/labels, optional future closing time (max 30 days). ELECTION initially means choosing one named candidate/option; no official voter register/electoral process. One vote/user, replace/retract allowed while open, counts and viewer choice persisted. Only ACTIVE authorized participants vote; private/pending/removed/expired posts deny voting. Author or space manager can close; no individual voter identities exposed.
- Direct chats: every active signed-in system role may initiate by exact recipient email. Only the two participants can read, send, accept/decline or mark-read. Unknown-contact first message is a request; one opening message until recipient accepts, preventing request spam. Decline blocks further send in that conversation. No global ADMIN override or participant enumeration endpoint.
- Conversation/message lists bounded and indexed; client UUID idempotency, read cursors/unread count and request/all/unread tabs. Initial delivery uses open/visible polling with abort/backoff, not an untested realtime claim. No chat media upload in this increment.
- Extend existing React SVG icon system (like/comment/share/photo/video/poll/chat/link/color/search/close/compose) rather than adding duplicate icon libraries or copying Facebook assets. Preserve existing navigation and responsive layouts.

## Architecture and verification
Domain pure policies/value objects; API/usecase meaningful input boundaries; application ports and adapters for JDBC/MinIO. No infrastructure imports in domain/application. Create post + features/poll atomically; votes and direct requests lock/recheck state. Aggregate counts/previews batched, no N+1 per feed card. JUnit/Mockito, MockMvc/Security, PostgreSQL/MinIO Testcontainers, Modulith/ArchUnit and existing regression gates remain.

- [ ] Upload transport + actual browser composer upload.
- [ ] Retention migration, scheduler/leases, failure retry and expiry/private-media tests.
- [ ] Links, colors, polls/elections, atomic votes and UI.
- [ ] Direct request/inbox/chat, IDOR and retry/read-cursor tests.
- [ ] Full backend/frontend/responsive verification and factual report.
- [ ] Feature CI green; main permission and CI verified independently.

Movies and Speaking are not part of this increment.

