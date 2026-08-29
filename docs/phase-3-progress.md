# Phase 3 — Learning Experience (completed)

## Trạng thái

Phase 3 đã hoàn tất các vertical slice Enrollment, Curriculum, Lesson Player và Progress/Resume.

## Phạm vi đã triển khai

- Flyway V3 tạo `enrollments`, unique `(user_id, course_id)` và index cho truy vấn học viên/khóa học.
- Module `learning` đã được nâng cấp theo hexagonal architecture: public use cases/contracts,
  framework-free domain/application, inbound web/transaction adapters và outbound persistence adapter.
- `POST /api/v1/courses/{slug}/enrollments`: yêu cầu JWT và idempotent.
- `GET /api/v1/me/enrollments`: chỉ lấy dữ liệu của subject hiện tại.
- `DirectEnrollmentPolicy`: khóa miễn phí được ghi danh; khóa trả phí trả `payment_required`
  cho tới khi Commerce xử lý thanh toán.

## Kiểm thử và quality gate

- `DirectEnrollmentPolicyTest`: 2 unit tests.
- `EnrollmentApiIntegrationTest`: 3 integration tests với PostgreSQL Testcontainers.
- Full backend regression: 29 tests, 0 failure, 0 error, 0 skipped trên Docker-backed clean verify.
- Flyway V1-V5 và Hibernate `ddl-auto=validate`: PASS.
- Spring Modulith và ArchUnit boundary rules: PASS.
- JaCoCo line coverage: 73,89%; Maven gate tối thiểu 70%.

## File chính

- `src/main/resources/db/migration/V3__create_learning_schema.sql`
- `src/main/java/com/ailearning/platform/learning/**` (package layout mới của Phase 2.5)
- `src/test/java/com/ailearning/platform/learning/**`
- `src/test/resources/learning-test-*.sql`

## Definition of done

- [x] Enrollment idempotent và My Learning theo JWT subject.
- [x] Curriculum sections/lessons, preview và content access control.
- [x] Lesson player, transactional progress, completion và resume.
- [x] Flyway/Testcontainers, unit, security, Modulith và ArchUnit tests.
- [x] JaCoCo report/gate và frontend responsive Selenium smoke.
