# Exam authoring and immutable revisions · 2026-09-28

Status: backend/frontend implemented and verified locally on `feature/exam-authoring-revisions`; feature/main CI delivery is the remaining gate.

## Đã triển khai

- Nền tảng học tiếng Anh, không phải học lập trình. Soạn đề Listening, Reading và Writing; Speaking vẫn ngoài phạm vi.
- Staff studio và editor: tạo nháp, thêm/xóa phần và câu hỏi, trắc nghiệm/điền đáp án/Writing, lưu, gửi duyệt, rút về nháp, duyệt/phát hành và tạo phiên bản mới.
- Giảng viên chỉ quản lý đề của mình; ADMIN quản lý mọi đề. LEADER/ADMIN duyệt nội dung đã gửi, không mở bản nháp riêng của người khác.
- Đề đã phát hành không sửa tại chỗ. Đề mới có ID phần/câu hỏi mới, trong khi bài làm và kết quả cũ vẫn trỏ vào phiên bản cũ.
- Optimistic version check và khóa series khi phát hành/clone. Hai lần lưu cùng phiên bản chỉ có một lần thành công; tối đa một bản editable và một publication cho mỗi series.
- Chấm Writing cho đề mới giới hạn theo tác giả, hoặc LEADER/ADMIN. Đề hệ thống cũ giữ behavior staff review trước đây. Không tự chấm bài của mình.
- Public catalog chỉ đọc summaries trong một SQL query, không tải toàn bộ câu hỏi/đáp án. HTTP công khai và payload làm bài vẫn không có answer key hoặc explanation.
- Lazy routes `/instructor/exams` và `/instructor/exams/:id`; menu staff nhóm dưới “Kho học liệu”. Responsive và trạng thái chưa lưu/xung đột rõ ràng.
- Domain/application framework-free, use-case/store ports có trách nhiệm thật; JDBC transaction ở adapter. Không thêm JPA entity, repository Impl rỗng hoặc dependency mới vào pom.xml.

## Spec và giới hạn

ADR-021 được viết/commit trước code. Giới hạn server: 20 phần, 250 câu/đề, 1–240 phút, giới hạn chiều dài cho mỗi field/options. Nháp có thể chưa đủ nội dung; gửi duyệt/phát hành yêu cầu câu hỏi hợp lệ, lựa chọn khác nhau, key thuộc lựa chọn và giải thích đáp án.

Điểm Nghe/Đọc là số câu đúng. Writing dùng rubric luyện tập 4 tiêu chí × 0–5, không cộng vào số câu đúng và không giả lập điểm TOEIC/IELTS chính thức.

## Verification

- `mvn -q verify`: **262 tests**, 0 failures, 0 errors, 0 skipped; package, SBOM và coverage gate qua. JaCoCo LINE: 3,698 covered / 4,149 total (~89.13%).
- JUnit/Mockito policy/use-case tests; MockMvc + Security Test; PostgreSQL Testcontainers; ArchUnit và isolated Spring Modulith assessment test.
- Migration regression: V17 chứa bài làm, đáp án và Writing rubric → V18 → phát hành phiên bản thay key → kết quả cũ không đổi.
- Concurrency: cùng expectedVersion chỉ một save thành công; cùng publication chỉ một clone thành công. Lỗi persistence save/publication rollback cả version, nội dung và archive trạng thái cũ.
- Frontend `pnpm build`, **55 tests / 24 suites**, `pnpm test:e2e` qua. Browser smoke có studio/editor/reviewer ở 320, 768, 1440px, không tràn ngang.
- API-backed preview browser: giảng viên lưu/gửi duyệt, leader thấy read-only keys và phát hành; public payload kiểm tra không lộ key/explanation. Bootstrap session dùng token thật từ local login, không mock assessment APIs. CDP nhập text và DOM activation dùng cho thao tác; đây không phải chứng nhận pointer-only hoặc cross-browser acceptance.
- Screenshot desktop/phone đã kiểm tra trực quan trong `target/preview-backups/`.
- Local full verify vẫn có warning cleanup Hikari/Testcontainers và thông báo Surefire đóng fork sau timeout khi JVM exit. Maven exit 0 và XML có 0 test errors; không che/skip test để né warning. Warning connection-closed cũng có ở lượt verify trước phase này. CI là gate độc lập trước merge.

## Preview database và backup

Trước nâng V18, preview có 6 users, 6 courses, 5 attempts, 2 answers và 1 Writing review. Sau Flyway V18, các số lượng này giữ nguyên; API health UP. Không xóa database, container hay volume.

Backup custom format: `target/preview-backups/ai-learning-preview-before-v18-20260928-1004.dump`, 115,623 bytes; `pg_restore -l` xác nhận schema/table-data inventory.

SHA-256: `D30DE9324BD7755D137D0BE5DB9BCD1BC0892C04AA88D4E352E9CCA6EE5B9701`.

Backup có dữ liệu local và chỉ nằm trong ignored target; không đưa lên Git. Runtime JAR riêng trong `target/preview-runtime/` để không khóa Maven package JAR.

Thêm một mini practice tự biên soạn trong preview qua authoring workflow:
- `workplace-team-training`, “Workplace English · Team Training”.
- Exam ID `391deeba-15da-406c-b774-50da0aedd9de`; 3 Listening + 4 Reading + 1 Writing; 35 phút.
- Tác giả `lecture@demo.local`, phát hành bởi `leader@demo.local`. Không sửa đề hệ thống cũ.
- Chỉ là mini practice 8 câu, không phải full-format TOEIC và không dùng tài liệu/branding PREP.

## Files thay đổi

Backend sources dưới `src/main/java/com/ailearning/platform/assessment/`:
- `api/contract/ExamDraftContent.java`
- `api/usecase/ExamAuthoringUseCase.java`, `PracticeUseCase.java`
- `application/port/out/ExamAuthoringStore.java`, `PracticeStore.java`
- `application/service/impl/ExamAuthoringService.java`, `PracticeService.java`
- `domain/enumtype/ExamRevisionStatus.java`, `PracticeSkill.java`, `QuestionKind.java`
- `domain/model/ExamRevision.java`, `ExamRevisionSummary.java`, `PracticeExamSummary.java`
- `domain/policy/ExamAuthoringPolicy.java`, `ExamContentPolicy.java`
- `adapter/in/web/controller/ExamAuthoringController.java`, `PracticeController.java`
- `adapter/in/web/dto/request/CreateExamRequest.java`, `SaveExamRequest.java`, `ExamVersionRequest.java`
- `adapter/out/persistence/PracticePersistenceAdapter.java`, `config/PracticeConfig.java`
- `domain/service/PracticeGrader.java` chỉ format, không đổi grading behavior.

Backend migration/security/tests:
- `src/main/resources/db/migration/V18__add_exam_authoring_revisions.sql`
- `src/main/java/com/ailearning/platform/platform/security/SecurityConfig.java`
- `src/test/java/com/ailearning/platform/assessment/api/ExamAuthoringApiIntegrationTest.java`
- `src/test/java/com/ailearning/platform/assessment/api/ExamAuthoringConcurrencyTest.java`
- `src/test/java/com/ailearning/platform/assessment/api/ExamRevisionMigrationTest.java`
- `src/test/java/com/ailearning/platform/assessment/application/service/impl/ExamAuthoringServiceTest.java`
- `src/test/java/com/ailearning/platform/assessment/domain/policy/ExamContentPolicyTest.java`
- Existing `PracticeApiIntegrationTest`, `PracticeServiceTest`, `PracticeGraderTest`, `AssessmentModuleIntegrationTest`: regression/setup adjustment or format.

Frontend dưới `E:/ai-learning-web/`:
- `src/features/exam-authoring/types.ts`, `exam-authoring.css`
- `ExamStudioPage.tsx`, `ExamEditorPage.tsx`, `ExamSectionEditor.tsx`
- `ExamStudioPage.test.tsx`, `ExamEditorPage.test.tsx` trong cùng feature folder.
- `src/app/App.tsx`, `src/components/AppHeader.tsx`, `src/styles.css`
- `scripts/selenium-smoke.mjs`

Docs: ADR-021, report này, migration plan; cập nhật ADR-020 và report phase trước để ghi delivery baseline CI-green main `c05c901` / `f78a48c`.

## Bảo toàn và việc còn lại

- `performance/ai-mentor.js` là thay đổi có sẵn của bạn: không sửa, không stage. SHA-256 giữ nguyên `D5ABE8938A956876305A98AE6F560246EF33BDFA45EA3457DF1AB5DCD5879336`.
- Không pull, stash, force push. Quy trình delivery: commit trên nhánh chức năng → push nhánh → exact CI xanh → merge/push main → kiểm tra main CI.
- Managed MinIO audio <10MB và transcript private khi thi là increment tiếp theo; hiện Listening vẫn dùng browser TTS demo.
- Timer có enforcement server, resume/history, question banks đầy đủ/original/licensed và analytics chưa hoàn thành.
- Draft chưa lưu chỉ nằm trong bộ nhớ. Có cảnh báo rời trang qua link/reload; chưa có offline autosave hoặc router-level back-navigation blocker.
- Assignment bổ sung reviewer và sửa điểm có audit chưa có; mỗi bài Writing hiện chỉ chấm một lần.
