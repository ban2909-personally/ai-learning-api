# Hồ sơ xã hội, kết bạn và chat từ hồ sơ — 29/09/2026

## Kết quả sử dụng

- Mở hồ sơ từ kết quả tìm kiếm, tên/avatar tác giả bài viết hoặc tên người bình luận.
- Hồ sơ có ảnh bìa theo chủ đề, avatar chữ cái, tên, giới thiệu, nơi sống, website,
  số bạn bè/bạn chung thực tế, tab bài viết/giới thiệu. Chủ tài khoản chỉnh thông tin của mình.
- Kết bạn: gửi, hủy lời mời; người nhận chấp nhận/từ chối; hai bên có thể hủy kết bạn.
  Trang `/community/friends` phân trang bạn bè, lời mời đã nhận/đã gửi.
- Nút Nhắn tin mở đoạn chat đã tồn tại hoặc composer theo ID người nhận, không cần lộ email.
  Mở hồ sơ/composer không tự tạo cuộc trò chuyện hoặc gửi tin nhắn.
- Trang chủ chỉ còn các lối tắt mạng xã hội: bảng tin, nhóm/trang, hồ sơ, bạn bè.
  Các mục học tập vẫn nguyên trên global header.
- Bộ lọc khóa học dùng danh mục có khóa PUBLISHED. Bốn danh mục lập trình trong
  preview đều có 0 khóa xuất bản nên không còn hiện; không tạo khóa lập trình giả,
  không xóa danh mục/course. Link cũ `?category=backend` tự bỏ bộ lọc không còn hợp lệ.

## Kiến trúc và convention

ADR-027 được viết trước sửa source. Làm trực tiếp ở E:\\ai-learning-api và
E:\\ai-learning-web trên nhánh `feature/social-profiles-friendships`.

Identity sở hữu profile và validation value object, xuất named boundary
`identity::access` / `identity::contract`. Community sở hữu friendship state
machine và bảng quan hệ. Controller chỉ gọi use case; application sử dụng input
contracts/output ports; không truy cập entity hoặc infrastructure của module khác.
Domain không phụ thuộc Spring/JPA/HTTP. Không thêm package dependency, thư viện,
repository Impl rỗng, shared business model hoặc utility chung.

Persistence JDBC cho profile/friendships; danh mục sử dụng Spring Data query EXISTS.
Google Java Format AOSP cho Java vừa chạm; Prettier theo convention single-quote/no-semi
cho FE. Các file catalog vốn một dòng được định dạng khi bổ sung query/use case.
FE có lazy routes, component riêng cho action kết bạn/editor, context mở chat dùng chung
với hộp thư, scoped CSS; xóa CSS profile/shortcut cũ đã không còn dùng.
Đọc async có abort/stale-response guard; giữ client ID khi gửi chat retry.

## Security và hành vi

- Public directory ID/name và endpoint profile tối giản cũ không đổi. Profile mở rộng
  có endpoint mới; không trả email, roles, trạng thái account, credentials hoặc private posts.
- Chỉ actor JWT active được chỉnh profile của chính mình. Không có update theo ID người khác.
- Anonymous chỉ xem nội dung công khai. Tất cả role active, kể cả GUEST đăng nhập,
  được kết nối/chat. Role ADMIN không thay actor hoặc bypass consent.
- Danh sách bạn bè/lời mời riêng tư theo actor; profile công khai chỉ có số lượng.
  Friend và mutual counts loại tài khoản disabled.
- Cặp UUID canonical có PK/FK/CHECK; khóa cặp account theo cùng thứ tự trong transaction.
  Duplicate request cùng chiều idempotent; request ngược chiều trả conflict, không auto-accept.
  Chỉ recipient chấp nhận; delete chỉ tác động cặp chứa actor.
- Chat theo ID giữ nguyên một opening message, recipient acceptance, DECLINED,
  participant checks và client-ID idempotency. API theo email cũ vẫn tương thích.
- Website chỉ HTTP(S), không credentials/relative/javascript; không fetch website từ server.
  React render text, không HTML; link ngoài có noopener/noreferrer.
- Mọi danh sách mới giới hạn 20 + sentinel, page 0..100; truy vấn không mở rộng graph
  không giới hạn hoặc gọi remote lookup cho từng thẻ.
- Upload bài viết ảnh/video <10 MB và xóa bài có media sau 14 ngày không đổi.
  Upload avatar/ảnh bìa chưa triển khai: bản này dùng chữ cái và bốn CSS themes.
  Không mô phỏng ảnh, thông tin cá nhân, bạn bè hoặc điểm thi không có dữ liệu.
- Chưa tuyên bố benchmark/khả năng chịu tải cho workload friendship mới; rate limiting,
  anti-spam/report/block và load test riêng là việc chuẩn bị production tiếp theo.

## Migration, backup và dữ liệu

Append V27, không sửa migration cũ: `identity_social_profiles`,
`community_friendships` với constraints và hai index lookup hai phía.

Trước migration đã pg_dump PostgreSQL preview V26 và kiểm tra pg_restore --list.
Backup durable: `.data/backups/social-profiles-before-v27-20260929.dump`,
162821 bytes, SHA256
`823E3C77142ACECF6F9D3A8FAF6AB561F60FDC66A963CD4FCC52A2FE4861D30F`.

Preview ở `ai_learning_preview`, PostgreSQL Docker host 5434. Chỉ dừng API preview
đã đối chiếu command line/PID/port và chạy artifact đã verify. API PID 6692 health UP,
Flyway V27. Không xóa/tạo lại volume hoặc dừng dịch vụ/container SQL ngoài scope.
6 users, 6 courses, 5 practice_attempts giữ nguyên trước/sau. Live test đã khôi phục
bio/theme tài khoản demo và xóa test friendship; còn 0 friendship kiểm thử.
Profile row của demo guest được khởi tạo với thông tin rỗng/theme mặc định.

## Kiểm thử và bằng chứng

- `mvn -q verify`: exit 0; 339 tests / 110 suites, failures 0, errors 0, skipped 0.
  Bao gồm JUnit/Mockito, MockMvc/Spring Security, PostgreSQL/Flyway Testcontainers,
  Spring Modulith/ArchUnit. 17 test mới, toàn bộ 322 test trước vẫn qua.
- JaCoCo line coverage 4970/5429 = 91,55%, qua gate 70%.
- Test đồng thời hai cross requests: một 200, một 409, đúng một PENDING row,
  không duplicate hoặc tự thành friends.
- FE `pnpm test`: 102 tests / 36 suites PASS; `pnpm build` PASS.
  11 test tăng thêm, toàn bộ regression trước vẫn được chạy.
- `pnpm test:e2e`: Selenium smoke PASS public/member/instructor ở 320/768/1440px.
- Browser thật localhost: hai session guest/student, không mock API. Form chỉnh profile,
  request/accept/remove, incoming list, counted state, profile chat, author navigation,
  social-only local links, legacy catalog deep-link và published-only options PASS.
  3 viewport không horizontal overflow; đã đọc ảnh mobile/desktop để QA layout.
- Bằng chứng local: `target/social-profiles-evidence/live-acceptance.json` và 6 ảnh
  profile/friends; log unit/build/e2e ở repo FE, log verify ở target BE.
- Một lần FE test catalog timeout do host chạy cùng backend integration; chạy riêng qua,
  bổ sung async wait và regression legacy category rồi chạy lại toàn bộ 102 test/build/
  Selenium đều qua. Không skip hoặc chỉ chọn test dễ.
- Verify có warning Hikari/Testcontainers và Surefire fork shutdown sau System.exit(0)
  đã tồn tại ở baseline; không coi các warning này là đã sửa. Exit/XML/coverage qua.

## Git và CI

Chia commit riêng spec, backend social boundary, catalog fix, frontend social UI,
frontend catalog fix và báo cáo. Chỉ stage file thuộc task, không commit
`performance/ai-mentor.js`; SHA256 file này vẫn là
`D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336`.

Báo cáo này được ghi trước push nên CI remote còn chờ ở thời điểm commit tài liệu.
Chỉ báo hoàn thành delivery sau khi kiểm tra mọi job cho exact final SHA:
BE 11 jobs (verify/recovery/image/security/performance baseline), FE verify.
Receipt cuối lưu ở `.data/reports/social-profiles-delivery-2026-09-29.md`;
không commit thêm chỉ để cập nhật SHA, tránh vòng lặp tạo run mới.

Không pull/merge/stash/reset/force. Main không cập nhật khi chưa có chấp thuận mới.
BE main `d931910a6577a60b8e0698884898b71314a6b59c`,
FE main `fe9ddb99522535e4338e590d4f852683cf9b657e`.

## Files thay đổi

Backend (42 files thuộc task; gốc E:\\ai-learning-api):

- `docs/architecture/ADR-027-social-profiles-friendships.md`
- `docs/reports/social-profiles-friendships-2026-09-29.md`
- `src/main/java/com/ailearning/platform/catalog/adapter/in/web/controller/CatalogController.java`
- `src/main/java/com/ailearning/platform/catalog/adapter/out/persistence/CatalogPersistenceAdapter.java`
- `src/main/java/com/ailearning/platform/catalog/adapter/out/persistence/jpa/repository/CategoryJpaRepository.java`
- `src/main/java/com/ailearning/platform/catalog/api/usecase/CatalogUseCase.java`
- `src/main/java/com/ailearning/platform/catalog/application/port/out/CatalogStore.java`
- `src/main/java/com/ailearning/platform/catalog/application/service/impl/CatalogService.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/DirectChatController.java`
- `src/main/java/com/ailearning/platform/community/adapter/in/web/controller/SocialProfileController.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcDirectChatStore.java`
- `src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcFriendshipStore.java`
- `src/main/java/com/ailearning/platform/community/api/contract/FriendConnection.java`
- `src/main/java/com/ailearning/platform/community/api/contract/FriendPage.java`
- `src/main/java/com/ailearning/platform/community/api/contract/FriendshipSummary.java`
- `src/main/java/com/ailearning/platform/community/api/contract/SocialProfileView.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/DirectChatUseCase.java`
- `src/main/java/com/ailearning/platform/community/api/usecase/SocialProfileUseCase.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/DirectChatStore.java`
- `src/main/java/com/ailearning/platform/community/application/port/out/FriendshipStore.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/DirectChatService.java`
- `src/main/java/com/ailearning/platform/community/application/service/impl/SocialProfileService.java`
- `src/main/java/com/ailearning/platform/community/config/CommunityConfig.java`
- `src/main/java/com/ailearning/platform/community/domain/model/Friendship.java`
- `src/main/java/com/ailearning/platform/community/domain/policy/FriendshipPolicy.java`
- `src/main/java/com/ailearning/platform/identity/adapter/out/persistence/JdbcSocialProfileStore.java`
- `src/main/java/com/ailearning/platform/identity/api/contract/ProfileUpdate.java`
- `src/main/java/com/ailearning/platform/identity/api/contract/SocialProfileDetails.java`
- `src/main/java/com/ailearning/platform/identity/api/usecase/access/SocialProfileAccess.java`
- `src/main/java/com/ailearning/platform/identity/application/port/out/SocialProfileStore.java`
- `src/main/java/com/ailearning/platform/identity/application/service/impl/SocialProfileService.java`
- `src/main/java/com/ailearning/platform/identity/config/AccountManagementConfig.java`
- `src/main/java/com/ailearning/platform/identity/domain/valueobject/ProfileInformation.java`
- `src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java`
- `src/main/resources/db/migration/V27__add_social_profiles_and_friendships.sql`
- `src/test/java/com/ailearning/platform/catalog/api/CatalogApiIntegrationTest.java`
- `src/test/java/com/ailearning/platform/community/api/CommunityApiIntegrationTest.java`
- `src/test/java/com/ailearning/platform/community/application/service/impl/DirectChatServiceTest.java`
- `src/test/java/com/ailearning/platform/community/application/service/impl/SocialProfileServiceTest.java`
- `src/test/java/com/ailearning/platform/community/domain/policy/FriendshipPolicyTest.java`
- `src/test/java/com/ailearning/platform/identity/application/service/impl/SocialProfileServiceTest.java`
- `src/test/java/com/ailearning/platform/identity/domain/valueobject/ProfileInformationTest.java`

Frontend (21 files; gốc E:\\ai-learning-web):

- `scripts/selenium-smoke.mjs`
- `src/app/App.tsx`
- `src/discovery.css`
- `src/features/catalog/CourseCatalogPage.test.tsx`
- `src/features/catalog/CourseCatalogPage.tsx`
- `src/features/community/CommunityHomePage.test.tsx`
- `src/features/community/CommunityHomePage.tsx`
- `src/features/community/FriendshipActions.tsx`
- `src/features/community/FriendsPage.test.tsx`
- `src/features/community/FriendsPage.tsx`
- `src/features/community/PostCard.tsx`
- `src/features/community/ProfileEditor.tsx`
- `src/features/community/PublicProfilePage.test.tsx`
- `src/features/community/PublicProfilePage.tsx`
- `src/features/community/types.ts`
- `src/features/messaging/DirectChat.test.tsx`
- `src/features/messaging/DirectChatContext.tsx`
- `src/features/messaging/DirectChatLaunch.test.tsx`
- `src/features/messaging/DirectChatMenu.tsx`
- `src/main.tsx`
- `src/social-profile.css`

