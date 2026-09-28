# ADR-024: Community social spaces, moderation, media and chat

Status: Implemented and locally verified, 2026-09-28. GitHub delivery gates remain pending until checked for the exact feature/main SHA.

## Product rules

The homepage remains an English-learning community with the existing navigation. Use familiar social layouts, not Facebook branding, assets or unrelated stories. Authenticated users of every active system role, including GUEST, may post; anonymous visitors may only read public content.

Public GROUP/PAGE content is readable immediately; joining creates an ACTIVE membership without approval. PRIVATE GROUP/PAGE exposes only discovery metadata until membership is ACTIVE. Accepting an invitation issued by a space manager is approval. System ADMIN is not automatically a space administrator: the creator is OWNER and delegates space ADMIN explicitly.

Member posts in a space require review by default. Managers publish directly; only that space's managers may approve or reject pending posts. Pending/rejected content never enters feeds, counters, comment/like/share APIs or public media delivery. Existing ACTIVE posts remain unchanged. Revoked authors cannot have a pending post approved. Private posts cannot be shared outside the space.

Posts show the space name above the author; personal posts have no redundant space label. Counters come from persisted interactions. Feed responses include at most two active top-level comment previews per post, fetched in one batch rather than one query per card. Cursor feed pages remain bounded and indexed; media loads only near the viewport, videos never autoplay.

Media milestone: initially one immutable image or video attachment per post; strict maximum 9,999,999 bytes per file, validated type and content, no SVG, no arbitrary object keys/URLs. Upload requires login; read authorization follows the attached post, including private/pending/removed states. Use community-owned output ports and MinIO adapter, shared platform configuration only, opaque IDs and existing media-cookie GET/HEAD security. Preserve bounded Range delivery and cleanup failed uploads.

Chat milestone: durable space-scoped text messages, ACTIVE members only (including public spaces), private pending/invited users cannot read history or send. Bounded chronological cursor/history, idempotent client message IDs, author/manager removal. Initial delivery may use visible/open-box polling with cleanup/backoff; do not claim WebSocket fanout until tested. No global broadcast, tokens in URLs or unbounded history reads.

## Migration and delivery

Never edit an applied Flyway migration, reset the DB, delete volumes or stage unrelated user changes. Separate migrations for moderation, media and chat. Existing API fields and published content remain compatible. JDBC remains an outbound persistence adapter; no JPA entities, empty Impl classes or speculative generic utilities are added.

- [x] Membership/private PAGE and pending-post policies, PostgreSQL migration and authorization tests.
- [x] Admin review queue and member pending-post feedback; cursor feed, counters, comment previews and responsive card hierarchy.
- [x] Size/MIME/signature-checked image/video upload, authorized MinIO streaming, frontend upload/progress/lazy media and boundary/IDOR tests. Full decode/scanning/transcoding is not delivered.
- [x] Space chat storage, authorization, retry/idempotency and responsive box (visible/open polling).
- [x] Full backend verify, frontend test/build and responsive browser checks before feature push.
- [ ] Exact branch CI green before main merge, then main CI checked independently.
- [x] Preview backup before migrations; factual report with unfinished work: docs/reports/community-social-spaces-2026-09-28.md.

After community: movie viewing for English learning (source/licensing, subtitles and streaming design must be specified first). Do not silently increase the 10 MB user-upload limit or seed pirated movies. Managed Listening audio (ADR-023), mentor prompt correction, server exam timing/resume and learning analytics remain tracked work; Speaking stays out of scope.
