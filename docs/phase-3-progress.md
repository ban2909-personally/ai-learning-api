# Phase 3 — Learning Experience (progress)

## Trạng thái

Vertical slice 1 `Enrollment -> My Learning` đã hoàn tất code và kiểm thử trên branch
`feature/learning-experience`. Chưa commit/push; chờ chủ dự án duyệt.

## Phạm vi đã triển khai

- Flyway V3 tạo `enrollments`, unique `(user_id, course_id)` và index cho truy vấn học viên/khóa học.
- Module `learning` đã được nâng cấp theo hexagonal architecture: public use cases/contracts,
  framework-free domain/application, inbound web/transaction adapters và outbound persistence adapter.
- `POST /api/v1/courses/{slug}/enrollments`: yêu cầu JWT và idempotent.
- `GET /api/v1/me/enrollments`: chỉ lấy dữ liệu của subject hiện tại.
- `DirectEnrollmentPolicy`: khóa miễn phí được ghi danh; khóa trả phí trả `payment_required`
  cho tới khi Commerce xử lý thanh toán.

## Kiểm thử

- `DirectEnrollmentPolicyTest`: 2 unit tests.
- `EnrollmentApiIntegrationTest`: 3 integration tests với PostgreSQL Testcontainers.
- Full backend regression: 16 tests, 0 failure, 0 error, 0 skipped.
- Flyway V1-V3 và Hibernate `ddl-auto=validate`: PASS.

## File chính

- `src/main/resources/db/migration/V3__create_learning_schema.sql`
- `src/main/java/com/ailearning/platform/learning/**` (package layout mới của Phase 2.5)
- `src/test/java/com/ailearning/platform/learning/**`
- `src/test/resources/learning-test-*.sql`

## Việc còn lại của Phase 3

- Curriculum: sections/lessons, preview và access control.
- Lesson player và lưu/resume progress theo transaction.
- Coverage report/gate và Selenium smoke cho luồng quan trọng.
- Cập nhật báo cáo hoàn thành Phase 3 sau khi toàn bộ scope đạt DoD.
