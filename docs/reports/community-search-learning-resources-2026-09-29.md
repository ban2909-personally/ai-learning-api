# Bàn giao — tìm cộng đồng/cá nhân và học liệu tiếng Anh miễn phí

Ngày: 29/09/2026. Nhánh hai repo: `feature/community-search-learning-resources`.

## Những gì đã triển khai

- Thanh tìm kiếm trên đầu trang chủ, nhận từ 1 ký tự trong tên; không phân biệt hoa/thường và dấu tiếng Việt. Gõ thêm chữ để thu hẹp kết quả. Ưu tiên tên trùng chính xác, tiếp đến đầu tên, rồi vị trí khác.
- Nhãn Hội nhóm / Page / Cá nhân và Công khai / Riêng tư rõ ràng; lọc loại kết quả, phân trang, trạng thái đang tìm, rỗng, lỗi và thử lại. Debounce 300 ms; AbortController và kiểm tra scope ngăn kết quả cũ ghi đè khi gõ nhanh. Escape/nút xóa để xóa từ khóa.
- Hồ sơ cá nhân công khai gồm ID/tên và feed bài viết công khai theo tác giả, dùng lazy loading có sẵn. Không hiện email/vai trò/trạng thái. Không hiện bài nhóm riêng tư kể cả khi người đang xem là owner; không tự chèn bài share của người khác vào feed cá nhân đang xem.
- Menu “Học liệu miễn phí” cho cả guest, trang `/resources` lazy-loaded với 10 liên kết gốc, lọc kỹ năng/loại/từ khóa có hoặc không dấu. Có ghi nguồn, thời điểm kiểm tra, lưu ý đăng ký/trả phí/khu vực và quyền nội dung.

## Nguồn đã xác minh

1. [British Council — Listening](https://learnenglish.britishcouncil.org/free-resources/listening): bài nghe theo trình độ.
2. [British Council — Reading](https://learnenglish.britishcouncil.org/free-resources/reading): bài đọc theo trình độ.
3. [British Council — Writing](https://learnenglish.britishcouncil.org/free-resources/writing): bài viết/mẫu và bài tập.
4. [British Council — Grammar](https://learnenglish.britishcouncil.org/free-resources/grammar): ngữ pháp và bài tập.
5. [Cambridge — Activities for learners](https://www.cambridgeenglish.org/learning-english/activities-for-learners/): hoạt động miễn phí theo kỹ năng.
6. [Cambridge — Write & Improve](https://writeandimprove.com/): công cụ cơ bản miễn phí; Test Zone/Class View không được gắn nhãn miễn phí.
7. [ETS — TOEIC preparation](https://www.ets.org/toeic/test-takers/prepare.html): Sample Tests/Handbook; không gọi khóa Official Learning and Preparation Course là miễn phí.
8. [BBC Learning English — Learning a new food culture](https://www.youtube.com/watch?v=5kr5ADrMeYU): bài học nghe trên kênh chính thức.
9. [BBC Learning English — YouTube](https://www.youtube.com/@bbclearningenglish): liên kết kênh gốc.
10. [VOA — Let's Learn English Level 1](https://learningenglish.voanews.com/p/5644.html): chuỗi bài học công khai cho người mới.

Không tải lại video/PDF, sao chép đề thi, embed nội dung hay scrape website. Nội dung/điểm học bên ngoài không nhập tự động vào DB hoặc hệ thống chấm thi; miễn phí xem không đồng nghĩa giấy phép sao chép. Speaking/phim ngoài phạm vi lần này.

## Kiến trúc, convention và security

- Identity sở hữu `PublicProfileLookup` / `PublicProfileService` / `PublicProfileStore` / JDBC adapter; chỉ trả `PublicProfile(id, displayName)` cho tài khoản ACTIVE. Community dùng named interface `identity::access` và `identity::contract`, không đọc bảng/entity identity qua adapter tìm kiếm riêng của community.
- Controller gọi use case; application không dùng Spring/JPA/HTTP/JDBC. Boundary input/output có ý nghĩa; không tạo Impl rỗng, common utils hay business model trong platform/sharedkernel.
- V26: trusted PostgreSQL extensions unaccent/pg_trgm, hàm search key với dictionary cố định và GIN expression indexes. Reindex nếu đổi dictionary. Tham số SQL bind; `%`, `_`, backslash là ký tự thật. 20 người + 20 cộng đồng/trang; sentinel 21, page 0–100, tối đa 120 ký tự, timeout 2 giây/query. Tìm 1–2 ký tự có thể scan, không tuyên bố GIN tăng tốc cho mọi query.
- Chỉ thêm public GET search/profile; các mutation vẫn cần xác thực. Tài khoản disabled bị loại; điều kiện private/approval/pending/media expiry hiện có giữ nguyên.
- CSS `community-discovery-*` tách khỏi form `community-search` cũ; API types đặt trong `community/types.ts`, không gắn type hồ sơ vào component UI tìm kiếm. Java theo formatter AOSP hiện có, TS theo convention single quote/no semicolon, CSS được định dạng đầy đủ. Không đổi pom/package/lockfile/dependency.
- Outbound links HTTPS, `noopener noreferrer`; không third-party thumbnails, iframe, autoplay hay server-side URL fetching.
- Chưa bổ sung quota/rate limit riêng cho endpoint tìm kiếm hoặc production benchmark dữ liệu lớn. Cần cân nhắc quyền CREATE extension, khóa khi build index transactionally, rate limit ở gateway và load test thực tế khi triển khai production.

## Kiểm thử và kiểm chứng

- `mvn -q verify`: exit 0, **322 tests / 106 suites**, 0 failure/error/skips; JaCoCo LINE 4722 covered / 452 missed = **91.26%**, qua gate 70%. Bao gồm JUnit/Mockito, MockMvc/Security, PostgreSQL/Flyway Testcontainers, ArchUnit và Spring Modulith cùng toàn bộ regression.
- `pnpm test`: **91 tests / 34 suites** đạt. `pnpm build`: TypeScript/production build đạt. `pnpm test:e2e`: browser regression đạt ở 320/768/1440 px, có assertion chiều cao/chiều rộng ô tìm kiếm, profile và lọc học liệu.
- Lần browser chạy đầu mất kết nối Chrome renderer; chạy lại đầy đủ đạt. Visual QA phát hiện xung đột class CSS form cũ dù test overflow đạt; đã đổi namespace, thêm regression hình dạng ô tìm kiếm, chạy lại test/build/smoke và xem ảnh sau sửa.
- API/trình duyệt thật không mock: `h` trả 5 spaces và 4 people; `MINH ANH` còn 1 người. Public profile chỉ có 2 field id/displayName. Trang chủ/profile/resources không tràn ngang 320/768/1440. Lọc TOEIC mở đúng nguồn ETS. Không tạo/xóa dữ liệu demo mới.
- Artifacts local: `target/community-search-verify.log`, frontend `community-search-tests.log`, `community-search-build.log`, `community-search-e2e.log`, `target/community-discovery-live-result.json` và ảnh `target/community-discovery-{home,resources}-{320,768,1440}.png`.
- Maven vẫn có cảnh báo baseline khi context/Testcontainers đóng connection và Surefire fork exit sau 30 giây; exit 0 và XML tests đạt. Không coi cảnh báo này đã được sửa trong feature này.

## Dữ liệu, preview và git

- Backup trước V26: `target/preview-backups/community-before-v26-20260929.dump`, 159697 bytes, pg_restore list hợp lệ; SHA256 `A66F09E1BA1C207000C9DF8C24C52E768B44264C8043341CF4A2095E3D2FD2A9`.
- Trước/sau migration: **6 users, 6 courses, 5 practice_attempts**; Flyway V25 → V26. Không đổi mật khẩu/role/dữ liệu khóa học. Không xóa container/volume, không tạo container preview mới. API mới PID28700 health UP, frontend Vite cũ localhost:5173 được giữ nguyên.
- Jar cũ giữ tại `target/preview-runtime/ai-learning-api-before-v26.jar`; chỉ dừng PID31924 sau khi kiểm tra đúng command line dự án để khởi động bản đã verify.
- File của bạn `performance/ai-mentor.js` giữ nguyên, chưa stage/commit; SHA256 `D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336`.
- Commits: BE `5aea357` spec, `6946a20` discovery/profile + tests; FE `7cdccbc` search/profile/resources + responsive/tests. Refactor contract types và báo cáo được ghi vào các commit riêng sau gate; full SHA/CI cuối trong receipt local `target/community-search-delivery-2026-09-29.md`.
- CI remote pending khi tạo báo cáo, sẽ kiểm tra đúng SHA sau push nhánh tính năng. Không sửa report commit chỉ để đổi trạng thái CI; receipt ghi kết quả cuối.
- Main giữ nguyên: BE `d931910a6577a60b8e0698884898b71314a6b59c`, FE `fe9ddb99522535e4338e590d4f852683cf9b657e`. Không merge/push main, pull, stash hoặc force push; chờ phê duyệt mới cho main.

## Files thay đổi trong lần này

Backend: 21 files code/test bên dưới + ADR-026 và báo cáo này. Root: `E:/ai-learning-api`.

```text
src/main/java/com/ailearning/platform/community/adapter/in/web/controller/CommunityDiscoveryController.java
src/main/java/com/ailearning/platform/community/adapter/in/web/controller/FeedController.java
src/main/java/com/ailearning/platform/community/adapter/out/persistence/JdbcCommunityStore.java
src/main/java/com/ailearning/platform/community/api/contract/DiscoveryPage.java
src/main/java/com/ailearning/platform/community/api/usecase/CommunityDiscoveryUseCase.java
src/main/java/com/ailearning/platform/community/api/usecase/CommunityUseCase.java
src/main/java/com/ailearning/platform/community/application/port/out/CommunityStore.java
src/main/java/com/ailearning/platform/community/application/service/impl/CommunityDiscoveryService.java
src/main/java/com/ailearning/platform/community/application/service/impl/CommunityService.java
src/main/java/com/ailearning/platform/community/config/CommunityConfig.java
src/main/java/com/ailearning/platform/identity/adapter/out/persistence/JdbcPublicProfileStore.java
src/main/java/com/ailearning/platform/identity/api/contract/PublicProfile.java
src/main/java/com/ailearning/platform/identity/api/usecase/access/PublicProfileLookup.java
src/main/java/com/ailearning/platform/identity/application/port/out/PublicProfileStore.java
src/main/java/com/ailearning/platform/identity/application/service/impl/PublicProfileService.java
src/main/java/com/ailearning/platform/identity/config/AccountManagementConfig.java
src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java
src/main/resources/db/migration/V26__index_public_community_discovery.sql
src/test/java/com/ailearning/platform/community/api/CommunityApiIntegrationTest.java
src/test/java/com/ailearning/platform/community/application/service/impl/CommunityDiscoveryServiceTest.java
src/test/java/com/ailearning/platform/identity/application/service/impl/PublicProfileServiceTest.java
```

Frontend: 15 files, root `E:/ai-learning-web`.

```text
scripts/selenium-smoke.mjs
src/app/App.tsx
src/components/AppHeader.tsx
src/discovery.css
src/features/community/CommunityFeed.tsx
src/features/community/CommunityHomePage.tsx
src/features/community/CommunitySearch.test.tsx
src/features/community/CommunitySearch.tsx
src/features/community/PublicProfilePage.test.tsx
src/features/community/PublicProfilePage.tsx
src/features/community/types.ts
src/features/resources/EnglishResourcesPage.test.tsx
src/features/resources/EnglishResourcesPage.tsx
src/features/resources/englishResources.ts
src/main.tsx
```
