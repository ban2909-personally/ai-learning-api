# Platform storage configuration · 2026-09-28

Status: delivered. Exact feature CI and main CI both passed all 11 jobs; merged/pushed to main `d931910`.

## Phạm vi đã làm

- Tách cấu hình kết nối và singleton MinIO client khỏi catalog sang `platform/configuration/storage`.
- Spring Modulith named interface `platform::storage`; catalog khai báo dependency rõ ràng. ArchUnit giới hạn consumer vào configuration/outbound adapters, không cho domain/application/inbound adapters phụ thuộc cấu hình này.
- Giữ nguyên `app.storage.minio`, environment variables, bucket, object keys, API contracts và hành vi store/range/delete. Không sửa migration, database, Docker container/volume hoặc dependency pom.xml.
- `MinioStorageProperties.toString()` che access key/secret key để tránh rò rỉ khi debug. Getter/binding cần thiết cho SDK vẫn giữ đúng dữ liệu.
- Không thêm generic blob interface, business model dùng chung hoặc wrapper vô nghĩa. Ports/quyền media vẫn thuộc catalog; assessment audio sẽ có boundary nghiệp vụ riêng.
- Giới hạn upload hiện có vẫn **9,999,999 bytes**, dưới 10 MB; increment này không nới giới hạn.

## Quy trình và kiểm thử

ADR-022 được viết/commit trước source. Configuration test xác nhận bind prefix cũ, singleton client, không cần kết nối MinIO khi khởi tạo, từ chối credentials trống và không lộ chúng trong toString. Existing MinIO Testcontainers kiểm tra upload, byte range và delete; Modulith/ArchUnit qua focused run.

`mvn -q verify` exit 0: **266 tests / 96 suites**, 0 failures, 0 errors, 0 skipped. JaCoCo LINE 3,705 covered / 4,156 total (~89.15%); coverage gate, package và application SBOM qua. Không skip hoặc hạ gate để đạt xanh.

- Feature commit `e0a669f0c8bfd091d0da8ff22cc63b0bed107fcd`: [CI 36374966911](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36374966911), **11/11 jobs success**.
- Main merge `d931910a6577a60b8e0698884898b71314a6b59c`: [CI 36375557315](https://github.com/ban2909-personally/ai-learning-api/actions/runs/36375557315), **11/11 jobs success**, rechecked 2026-09-28.
- Main merge tree matches the verified feature tip exactly; remote main was checked before normal merge/push. No pull, stash or force push.

Local run có Hikari connection-closed warnings lúc các test context cũ giữ pool sau khi Testcontainers đã đóng, như ở baseline authoring. Hai warning khởi tạo cấu hình thất bại là negative tests cố ý truyền credentials trống; các assertion và XML test results đều qua. Lượt verify này không có thông báo Surefire kill-fork timeout được ghi nhận ở lượt authoring trước.

Frontend không đổi trong increment này: baseline main `fe9ddb9` đã qua CI với 57 tests, build và Selenium responsive smoke. Preview hiện chạy authoring release `5783d32`; không cần nâng database cho cấu hình-only refactor.

## Files

- Xóa vị trí cũ `src/main/java/com/ailearning/platform/catalog/config/MinioStorageProperties.java`.
- Thêm `src/main/java/com/ailearning/platform/platform/configuration/storage/MinioStorageProperties.java`, `MinioStorageConfiguration.java`, `package-info.java`.
- Chỉnh `catalog/config/CatalogModuleConfig.java`, `catalog/package-info.java`, `catalog/adapter/out/storage/minio/MinioLessonMediaStorage.java`.
- Thêm `src/test/java/com/ailearning/platform/platform/configuration/storage/MinioStorageConfigurationTest.java`.
- Chỉnh `src/test/java/com/ailearning/platform/architecture/HexagonalArchitectureTest.java`, MinIO integration test import. Các source/test được format 4 spaces theo formatter đang dùng; ngoài đổi import/cấu hình/rule, không thay logic media.
- Docs: ADR-022, proposed ADR-023 cho audio, report này; cập nhật report authoring và ADR-021 với kết quả delivery.

## Bảo toàn và phần chưa làm

`performance/ai-mentor.js` là diff có sẵn của bạn, không stage/sửa. SHA-256 giữ nguyên `D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336`.

Đây là prerequisite kỹ thuật, **chưa có upload audio đề thi**. ADR-023 là spec cho bước tiếp theo: file dưới 10 MB, opaque immutable asset, kiểm tra file, quyền author/read, transcript private khi thi, player responsive và Range/HEAD. Timer enforcement, resume/history, full question banks, analytics và official score conversion vẫn nằm trên roadmap; Speaking ngoài phạm vi.
