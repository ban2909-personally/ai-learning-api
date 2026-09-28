# Community social spaces — delivery report, 2026-09-28

## Scope delivered locally

Homepage remains an English-learning community and preserves existing header options. Every active signed-in system role, including authenticated GUEST, can publish personal posts with one image/video. Anonymous visitors read public content only.

- Public GROUP/PAGE: immediate ACTIVE join; public reading does not require approval. Private GROUP/PAGE: discovery metadata only until ACTIVE membership; pending/invited users cannot read posts, media or chat.
- Space creators are OWNER; delegated space ADMIN can moderate. A system ADMIN receives no automatic rights in someone else's space. Member posts default to PENDING; only space managers approve/reject. Managers publish directly. Approval rechecks the author's ACTIVE membership.
- Feed uses bounded indexed cursor pagination with viewport-triggered loading and manual fallback. PostCard is extracted from the feed; space name appears above author, personal posts omit it. Persisted like/comment/share counters and two top-level active comment previews are returned; previews survive like-response card replacement.
- Image/video: JPEG/PNG/WebP/MP4/WebM; one attachment per post; strict size 1–9,999,999 bytes, including video. FE, domain and SQL enforce the boundary. Type/signature validation rejects SVG/HTML and mismatched headers. Media-only posts are allowed.
- MinIO stores immutable opaque objects. Public/pending/private/removed media authorization follows the post. GET/HEAD, single byte Range 200/206/416, nosniff and private no-store; no bearer token in URLs. Existing HttpOnly media cookie is GET/HEAD-only; it cannot authorize an upload.
- Space text chat: ACTIVE members only, 2,000-character limit, bounded 50-message keyset history, idempotent client IDs, author/space-manager soft removal. The responsive box polls only while open/visible, backs off on error and aborts on unmount. Private state is discarded when account or space changes.

## Architecture and conventions

Community owns its business policy, use cases/contracts, output ports, JDBC persistence and MinIO adapter. Domain has no Spring/JPA/HTTP/MinIO dependency. Controllers invoke use cases rather than repositories. Application returns API contracts, not REST response DTOs. Only meaningful media/chat boundary interfaces were added; no speculative generic utility, empty repository Impl or cross-module business entity.

Shared platform supplies MinIO client/configuration through its named storage interface; identity is consulted through AccountAccess. Spring Modulith and ArchUnit remain enforced. Existing migrations were not edited; V19 moderation, V20 media and V21 chat are additive. Space-lock and SQL membership checks protect concurrent create/review/member changes. Batched comment previews avoid per-card preview queries.

## Verification evidence

Final local commands all exited 0; no tests skipped or coverage gates lowered:

- Backend: mvn -q verify — **292 tests / 99 suites**, 0 failures/errors/skips. JaCoCo LINE **4,088 covered / 4,527 total (~90.30%)**. Includes domain/Mockito ports, MockMvc/Spring Security, PostgreSQL/Flyway and real MinIO Testcontainers, Modulith/ArchUnit, package and application SBOM.
- Frontend: pnpm test — **69 tests / 27 suites**; pnpm build/type-check; pnpm test:e2e responsive Selenium smoke at 320/768/1440 px.
- Real preview API acceptance (not mocked): public join, private PAGE approval boundary, pending media hidden until approval, MinIO upload/byte-exact Range/HEAD, idempotent likes and chat, comment/reply/share counters, previews and moderated message masking.
- Actual localhost browser acceptance: anonymous private access boundary, authenticated GUEST manager review/chat UI, native MinIO image decoding, space-above-author hierarchy and no horizontal overflow at all three widths.
- Original Canvas poster and software VP8/WebM demo: **84,343 bytes / 15 frames**, decoded before upload; MinIO full response matched bytes exactly; native video playback advanced to ~0.50 s, preload none and no autoplay. Post: 6ab13129-9381-4f30-882c-2b0dcf0d5ff6. Poster post: db4f6616-5f47-42a6-bdbd-7d214299ec54.
- Browser's first MediaRecorder fixture was only a header/no frames and could not play. It was not counted as a pass. The exact 110-byte fixture was soft-removed after a valid WebCodecs clip passed. Tiny image/share fixtures from this acceptance run were also soft-removed; existing user posts were not changed.

Local full verify logged Hikari connection-closed warnings during old Testcontainers context shutdown and a Surefire fork shutdown timeout after System.exit(0). Maven still exited 0; XML tests and coverage gate passed. This teardown debt is recorded, not hidden as a production fault or suppressed by skipping tests. Final GitHub CI must independently pass.

## Data and preview preservation

Before V19, custom-format PostgreSQL backup:
target/preview-backups/community-before-v19-20260928-1603.dump — 122,984 bytes; pg_restore inventory checked (226 lines).
SHA-256: 3CA2551AECF64ABD928C69A2A4871437D80F523E4944F0938EAF0E86F17ED803.

API preview uses ai_learning_preview in the existing project PostgreSQL container (host port 5434). V19–V21 applied successfully. **6 users, 6 courses and 5 practice attempts were preserved**. Acceptance added explicitly labeled local demo spaces/posts/media; removed fixtures retain soft-deleted history and referenced objects, no hard data deletion. No volumes or existing project containers removed/recreated.

UI: http://localhost:5173 ; API: http://localhost:8080 . Verified jar was redeployed after the final preview-preservation regression fix. Runtime logs/screenshots/scripts remain ignored under target rather than committed as source.

Existing user diff performance/ai-mentor.js is untouched and unstaged; SHA-256:
D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336.
pom.xml, dependency lockfiles and applied migrations unchanged.

## Git delivery gate

Branches in both repositories: feature/community-social-spaces (no codex prefix).
Specification was committed before source changes. Full local verification precedes feature push. Exact feature SHA and all jobs must be green before normal main merge/push; main CI must then be checked separately. No pull, stash or force push; unrelated performance script is never staged.

At report creation: local implementation/verification complete; feature/main GitHub CI and merge are pending. Delivery receipt will be added only after verified results.

## Explicit limits and next work

This increment is not a complete Facebook clone or production-scale social platform:

- One attachment/post; MIME + signature validation, not full media decoding, antivirus, transcoding, image dimension/metadata sanitation or storage quota enforcement. A syntactically plausible corrupted container can be accepted; decode/scanning/quarantine is a future hardening gate.
- Native codec availability varies by browser; no media transcoder or thumbnail pipeline. Soft-deleted media remains retained; retention/orphan recovery jobs need their own approved policy.
- Space manager control is default review + approve/reject/remove. No configurable posting-rule UI, permanent ban workflow, pending-history/withdraw UI, reporting/appeals or content-safety service.
- Chat is bounded visible-box polling, not WebSocket fanout; no direct messages, typing, unread badge, push notifications or production load claim. Existing notification WebSocket is separate.
- Counter/preview correctness and authorization were tested; no community high-concurrency benchmark was run. Existing performance CI is regression protection, not proof of a new community capacity target.
- Next requested feature: **watch English-learning movies**. Specify authorized/licensed sources, subtitle rights, catalog/permissions, streaming and subtitle player before implementation. Do not import pirated movies or increase the user's 10 MB upload limit silently.
- Managed Listening audio (ADR-023), mentor prompt correction, server exam timing/resume and analytics remain on the roadmap. Speaking remains out of scope. No official TOEIC/IELTS score-equivalence claim.

## Files changed in this increment

Backend (E:/ai-learning-api):

- docs/architecture/ADR-024-community-social-spaces.md
- docs/reports/community-social-spaces-2026-09-28.md
- src/main/java/com/ailearning/platform/community/adapter/in/web/controller/CommunityMediaController.java
- src/main/java/com/ailearning/platform/community/adapter/in/web/controller/PostModerationController.java
- src/main/java/com/ailearning/platform/community/adapter/in/web/controller/SpaceChatController.java
- src/main/java/com/ailearning/platform/community/adapter/in/web/dto/request/SpaceMessageRequest.java
- src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcCommunityStore.java
- src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcSpaceChatStore.java
- src/main/java/com/ailearning/platform/community/adapter/out/storage/minio/MinioCommunityMediaStorage.java
- src/main/java/com/ailearning/platform/community/api/contract/CommunityMediaRead.java
- src/main/java/com/ailearning/platform/community/api/contract/CommunityMediaUpload.java
- src/main/java/com/ailearning/platform/community/api/contract/MediaView.java
- src/main/java/com/ailearning/platform/community/api/contract/PostView.java
- src/main/java/com/ailearning/platform/community/api/contract/SpaceChatPage.java
- src/main/java/com/ailearning/platform/community/api/contract/SpaceMessageView.java
- src/main/java/com/ailearning/platform/community/api/usecase/CommunityMediaUseCase.java
- src/main/java/com/ailearning/platform/community/api/usecase/CommunityUseCase.java
- src/main/java/com/ailearning/platform/community/api/usecase/SpaceChatUseCase.java
- src/main/java/com/ailearning/platform/community/application/port/out/CommunityMediaStorage.java
- src/main/java/com/ailearning/platform/community/application/port/out/CommunityStore.java
- src/main/java/com/ailearning/platform/community/application/port/out/SpaceChatStore.java
- src/main/java/com/ailearning/platform/community/application/service/impl/CommunityMediaService.java
- src/main/java/com/ailearning/platform/community/application/service/impl/CommunityService.java
- src/main/java/com/ailearning/platform/community/application/service/impl/SpaceChatService.java
- src/main/java/com/ailearning/platform/community/config/CommunityConfig.java
- src/main/java/com/ailearning/platform/community/domain/model/MediaAsset.java
- src/main/java/com/ailearning/platform/community/domain/model/MediaByteRange.java
- src/main/java/com/ailearning/platform/community/domain/model/Post.java
- src/main/java/com/ailearning/platform/community/domain/model/PostStatus.java
- src/main/java/com/ailearning/platform/community/domain/policy/CommunityMediaPolicy.java
- src/main/java/com/ailearning/platform/community/domain/policy/CommunityPolicy.java
- src/main/java/com/ailearning/platform/community/package-info.java
- src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java
- src/main/resources/db/migration/V19__add_community_post_moderation.sql
- src/main/resources/db/migration/V20__add_community_post_media.sql
- src/main/resources/db/migration/V21__add_community_space_chat.sql
- src/test/java/com/ailearning/platform/community/adapter/out/storage/minio/MinioCommunityMediaStorageIntegrationTest.java
- src/test/java/com/ailearning/platform/community/api/CommunityApiIntegrationTest.java
- src/test/java/com/ailearning/platform/community/application/service/impl/CommunityMediaServiceTest.java
- src/test/java/com/ailearning/platform/community/domain/policy/CommunityMediaPolicyTest.java
- src/test/java/com/ailearning/platform/community/domain/policy/CommunityPolicyTest.java

Frontend (E:/ai-learning-web):

- scripts/selenium-smoke.mjs
- src/features/community/CommunityFeed.tsx
- src/features/community/CommunityHomePage.test.tsx
- src/features/community/CommunityHomePage.tsx
- src/features/community/PostCard.tsx
- src/features/community/PostMedia.test.tsx
- src/features/community/PostMedia.tsx
- src/features/community/SpaceChat.test.tsx
- src/features/community/SpaceChat.tsx
- src/features/community/SpaceDetailPage.tsx
- src/features/community/SpaceDirectoryPage.tsx
- src/features/community/SpacePostModeration.test.tsx
- src/features/community/SpacePostModeration.tsx
- src/features/community/types.ts
- src/features/community/useCommunityApi.ts
- src/styles.css
