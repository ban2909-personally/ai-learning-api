# Báo cáo triển khai: Community discussions & private messaging

Ngày: 28/09/2026. Workspace: `E:\ai-learning-api` và `E:\ai-learning-web`.
Nhánh ở cả hai repository: `feature/community-discussions-messaging`.
Báo cáo này ghi trạng thái tại lúc tạo: local gates đã PASS; CI của commit mới chưa được chạy cho đến khi push. Không xem nhánh feature là đã merge main.

## Những gì đã thực hiện

- Sửa lỗi thực tế khiến composer không đăng ảnh/video được: transport dùng PUT nhưng endpoint tạo post cần POST, dẫn tới 405. Upload method nay được truyền rõ; upload bài học cũ vẫn mặc định PUT. Bổ sung regression cho cả hai.
- Post cho phép một JPEG/PNG/WebP/MP4/WebM, dung lượng nghiêm ngặt **nhỏ hơn 10.000.000 bytes**. Hiển thị preview/progress/lỗi; video không autoplay, media ngoài màn hình không tải sớm.
- Bài có media hết hạn sau 14 ngày tính từ upload. API từ chối truy cập/tương tác ngay tại deadline; phần nội dung/media gốc trong bài chia sẻ được che đi. Scheduler chạy mỗi phút, claim tối đa 100 asset bằng lease 5 phút và SKIP LOCKED; MinIO lỗi được retry. Post chuyển REMOVED để giữ lịch sử/audit, chỉ object MinIO thuộc `community/{assetId}` bị xóa vật lý. Không đặt lifecycle cho cả bucket vì có file bài học dùng chung. Bài text/poll/link-only không tự hết hạn theo quy tắc này.
- Thêm link HTTP(S) cho bài cá nhân, GROUP và PAGE; giữ kiểm duyệt/private membership cũ. Không nhận HTML, javascript/data URL hoặc URL chứa credential; không fetch metadata từ server, tránh tạo đường SSRF.
- Background/font color có palette và color picker; validate hex và tương phản tối thiểu 4.5:1, không nhận CSS tùy ý.
- POLL và ELECTION: 2–8 lựa chọn khác nhau, mỗi tài khoản một phiếu, đổi/rút khi còn mở, tổng phiếu và lựa chọn cá nhân lưu DB. Đóng thủ công bởi tác giả/quản trị cộng đồng hoặc tới hạn (tối đa 30 ngày). ELECTION trong increment này là bầu chọn một ứng viên/phương án, **không phải quy trình bầu cử chính thức với danh sách cử tri**.
- Bảy reaction LIKE/LOVE/CARE/HAHA/WOW/SAD/ANGRY, icon SVG nguyên bản, picker dùng được bằng bàn phím/cảm ứng. Một reaction/user/post, đổi loại không tăng tổng giả. Endpoint like cũ vẫn hoạt động.
- Chat riêng cho mọi role đăng nhập đang ACTIVE. Khởi tạo bằng email chính xác, một tin mở đầu là lời mời; người nhận phải chấp nhận trước khi gửi thêm. Từ chối chặn gửi tiếp. Chỉ hai người được đọc/gửi/accept/decline/mark-read, ADMIN hệ thống không có quyền xem hộ.
- Hộp thư có tìm kiếm trong đoạn chat đã tải, tab tất cả/chưa đọc/lời mời, unread count từ DB. Tin nhắn cursor hai chiều, idempotency client UUID, read marker không được nhảy tới tương lai. Chỉ polling khi mở/visible, một request chain tại một thời điểm, abort/backoff; tối đa 100 conversations và 200 tin trong bộ đệm. Đây chưa phải realtime WebSocket.
- Chat overlays đặt qua React portal để không bị sticky blurred header làm lệch vị trí. Responsive 320/768/1440; giữ navigation hiện có và lazy-loaded feed.

## Kiến trúc, convention và bảo mật

Giữ modular monolith và bounded context community, chỉ dùng identity API boundary (AccountAccess).
Domain policies/value objects không có Spring/JPA/HTTP. Use cases và output ports có mục đích rõ; application không trả REST DTO hoặc gọi JDBC/MinIO trực tiếp.
Config wiring rõ imports; JDBC nằm outbound persistence adapter. Post + feature/poll tạo trong cùng transaction; vote/reaction/request lock và recheck quyền trước mutation. Preview/poll/reaction counts batch theo trang, không gọi repository từng card.
Không thêm dependency, không sửa pom.xml/lockfile, không tạo utils/base hoặc Impl rỗng. V22–V25 là migration additive; không sửa migration lịch sử.
JWT/active-account check, private/pending/expired guards, participant-only chat và retry không trùng được test. React render plaintext, link noopener/noreferrer.
Chưa khẳng định khả năng chịu tải production của chat bằng load test mới; polling có giới hạn và index nhưng vẫn cần capacity test khi chốt SLA.

## Kiểm thử đã hoàn thành

| Gate | Kết quả |
| --- | --- |
| Backend final `mvn -q verify` | Exit 0; 314 tests / 104 suites; 0 failure, 0 error, 0 skipped |
| JaCoCo line coverage | 4.644 / 5.098 lines = 91,09%, vượt gate 70% |
| JUnit/Mockito | Domain features/retention/chat, application ports và existing regression |
| MockMvc + Security + PostgreSQL/Flyway | Community API 23 tests; idempotent vote/reaction, moderation/private, direct IDOR/consent/decline/cursor/read-clamp, multipart features |
| Modulith / ArchUnit / JPA / MinIO / Kafka regression | Nằm trong full verify, không bỏ các gate hiện có |
| Frontend `pnpm test` | 84 tests / 31 suites PASS |
| Frontend `pnpm build` | TypeScript và production build PASS |
| `pnpm test:e2e` | Selenium smoke PASS ở 320/768/1440, gồm poll/reaction và inbox/thread |
| Actual localhost browser | Composer PNG + poll + link + colors, vote LOVE reaction, hai phiên tài khoản trao đổi chat PASS |
| Actual retention scheduler | Object fixture tồn tại trước expiry, sau deadline post REMOVED/404, storage_deleted_at được ghi, mc stat báo Object does not exist |

Log full verify vẫn có cảnh báo Hikari/testcontainer đã đóng và Surefire shutdown sau System.exit(0) của test lifecycle hiện hữu. Exit cuối là 0 và XML tất cả tests đều xanh; cảnh báo này được ghi nhận, không giấu hoặc gọi là failure đã sửa.

## Local demo / dữ liệu và bằng chứng

- Web: http://localhost:5173/; API health UP: http://localhost:8080/actuator/health.
- Database đúng là `ai_learning_preview` trong container dự án, host port 5434; Flyway đã lên V25.
- Sau migration và acceptance vẫn giữ 6 users, 6 courses, 5 practice_attempts. Không reset DB, không xóa container/volume hoặc SQL project khác.
- Backup schema V21 trước V22–V25: `target/preview-backups/community-before-v22-24-20260928-2140.dump`, 137.297 bytes, pg_restore inventory 245 lines, SHA256 `3FE5894FF33CCAE32192B7EAD9DF67406A06F1F550BD9C9EDDB4A46014B6E286`.
- Jar đã verify chạy local: SHA256 `E922B660437DD0212B485592DF74B5F8EFD737DEDDAC6BF39676FEAF5F2A43E5`; preview API PID 31924, frontend dev server hiện hữu được giữ nguyên.
- Actual composer upload trước feature extension: PNG 578.015 bytes và WebM hợp lệ 84.343 bytes qua XHR POST thật, không mock. Hai post IDs: `d01522f7-bf12-4725-86eb-fac0ca90ac01` / `69424fd6-0c3e-42a3-af42-f635048e8c39`.
- Demo discussion mới: `c4a1f9e8-156f-40b0-b98f-ccd1a5d1f48f`; chat `3f1e38d0-8b81-4f8f-9e79-75af4d19a4c5`.
- Đã cho hết hạn **duy nhất fixture mới** `cad44e30-5a2e-43c7-a187-8db875cb395d`, object `community/aa10a7a4-37c2-491d-9ef3-d6f5cfc4adbe`, để mô phỏng mốc 14 ngày. Job thật xóa object này; file nguồn vẫn ở `target/ui-upload-fixture.png` để có thể tải lại. Không giả vờ đã chờ đủ 14 ngày.
- Receipt/script/screenshot local nằm trong target (ignored), không đưa credential, backup hoặc media thử vào Git:
  `community-composer-upload-acceptance.mjs`, `community-discussions-live.mjs`, `community-discussions-live-result.json`, `community-discussions-{320,768,1440}.png`, `community-retention-live-result.json`, `community-discussions-final-verify.log`.
- Bảo toàn thay đổi của bạn trong `performance/ai-mentor.js`, không stage; hash giữ nguyên `D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336`.

## Cách dùng

Đăng nhập tài khoản demo (mật khẩu 123456 chỉ dùng local): guest@demo.local hoặc student@demo.local; các tài khoản lecture/leader/admin trước đó vẫn giữ.
Ở composer chọn ảnh/video, bình chọn/bầu chọn, link và màu rồi Đăng bài. Mở Chọn cảm xúc dưới card để thả reaction.
Nhấn icon chat trên header, Viết tin nhắn mới, nhập email người nhận. Người nhận mở Lời mời, chọn Chấp nhận để hai người chat tiếp.
Bài member trong GROUP/PAGE vẫn cần manager duyệt; private phải được duyệt tham gia trước khi xem nội dung.

## Git và giới hạn còn lại

Các commit chuẩn bị trước phần cuối: BE 227595a (spec), fd76148 (retention); FE ed94312 (upload transport).
Chỉ commit/push nhánh chức năng sau local gates. CI sẽ được kiểm tra theo exact SHA sau push; receipt cuối tại `target/community-discussions-delivery-2026-09-28.md`.
Main chưa thay đổi: lần trước thao tác main bị permission review từ chối; không thử lại hoặc đi đường vòng khi chưa có chấp thuận mới.
Không pull, merge, stash, reset, force push. Phim và Speaking không thuộc increment này; chat media/files, voter-register election và production capacity test chưa được triển khai.

## Files thay đổi trong increment

### Backend

- `docs/architecture/ADR-025-community-discussions-messaging.md`
- `src/main/java/com/ailearning/platform/community/adapter/in/scheduling/MediaRetentionJob.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/CommunityMediaController.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/DirectChatController.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/FeedController.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/PollController.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/dto/request/PostRequest.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcCommunityStore.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcDirectChatStore.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcMediaRetentionStore.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcPostFeatureStore.java`
- `src/main/java/com/ailearning/platform/community/api/contract/CreatePostCommand.java`
- `src/main/java/com/ailearning/platform/community/api/contract/DirectConversationView.java`
- `src/main/java/com/ailearning/platform/community/api/contract/DirectInboxPage.java`
- `src/main/java/com/ailearning/platform/community/api/contract/DirectMessagePage.java`
- `src/main/java/com/ailearning/platform/community/api/contract/DirectMessageView.java`
- `src/main/java/com/ailearning/platform/community/api/contract/PollOptionView.java`
- `src/main/java/com/ailearning/platform/community/api/contract/PollView.java`
- `src/main/java/com/ailearning/platform/community/api/contract/PostView.java`
- `src/main/java/com/ailearning/platform/community/api/contract/RetentionResult.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/CommunityMediaUseCase.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/CommunityUseCase.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/DirectChatUseCase.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/MediaRetentionUseCase.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/PollUseCase.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/CommunityStore.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/DirectChatStore.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/MediaRetentionStore.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/PostFeatureStore.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/CommunityMediaService.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/CommunityService.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/DirectChatService.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/MediaRetentionService.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/PollService.java`
- `src/main/java/com/ailearning/platform/community/config/CommunityConfig.java`
- `src/main/java/com/ailearning/platform/community/domain/model/ExpiredMedia.java`
- `src/main/java/com/ailearning/platform/community/domain/model/PollDefinition.java`
- `src/main/java/com/ailearning/platform/community/domain/model/PollKind.java`
- `src/main/java/com/ailearning/platform/community/domain/model/PostFeatures.java`
- `src/main/java/com/ailearning/platform/community/domain/model/ReactionKind.java`
- `src/main/java/com/ailearning/platform/community/domain/policy/DirectChatPolicy.java`
- `src/main/java/com/ailearning/platform/community/domain/policy/MediaRetentionPolicy.java`
- `src/main/java/com/ailearning/platform/community/domain/policy/PostFeaturesPolicy.java`
- `src/main/java/com/ailearning/platform/community/domain/valueobject/PostAppearance.java`
- `src/main/resources/db/migration/V22__add_community_media_retention.sql`
- `src/main/resources/db/migration/V23__add_community_discussions.sql`
- `src/main/resources/db/migration/V24__add_private_direct_chat.sql`
- `src/main/resources/db/migration/V25__add_community_reactions.sql`
- `src/test/java/com/ailearning/platform/community/api/CommunityApiIntegrationTest.java`
- `src/test/java/com/ailearning/platform/community/application/service/impl/CommunityMediaServiceTest.java`
- `src/test/java/com/ailearning/platform/community/application/service/impl/DirectChatServiceTest.java`
- `src/test/java/com/ailearning/platform/community/application/service/impl/MediaRetentionServiceTest.java`
- `src/test/java/com/ailearning/platform/community/domain/policy/DirectChatPolicyTest.java`
- `src/test/java/com/ailearning/platform/community/domain/policy/MediaRetentionPolicyTest.java`
- `src/test/java/com/ailearning/platform/community/domain/policy/PostFeaturesPolicyTest.java`
- `docs/reports/community-discussions-messaging-2026-09-28.md`

### Frontend

- `scripts/selenium-smoke.mjs`
- `src/components/AppHeader.tsx`
- `src/features/auth/AuthContext.tsx`
- `src/features/community/CommunityFeed.tsx`
- `src/features/community/CommunityIcon.tsx`
- `src/features/community/PostCard.tsx`
- `src/features/community/PostComposer.test.tsx`
- `src/features/community/PostComposer.tsx`
- `src/features/community/PostOptions.tsx`
- `src/features/community/PostPoll.test.tsx`
- `src/features/community/PostPoll.tsx`
- `src/features/community/PostReactions.test.tsx`
- `src/features/community/PostReactions.tsx`
- `src/features/community/types.ts`
- `src/features/messaging/DirectChat.test.tsx`
- `src/features/messaging/DirectChatMenu.tsx`
- `src/features/messaging/DirectThread.tsx`
- `src/features/messaging/types.ts`
- `src/lib/api.test.ts`
- `src/lib/api.ts`
- `src/styles.css`
