-- Explicit local-only demo seed. Never include this file in Flyway locations.
-- Run after migrations: psql -v ON_ERROR_STOP=1 -d ai_learning_preview -f scripts/seed-local-preview.sql
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'ai_learning_preview' THEN
        RAISE EXCEPTION 'Demo seed is restricted to ai_learning_preview';
    END IF;
END $$;
SELECT pg_advisory_xact_lock(20260925);
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users(id,email,password_hash,display_name,status)
SELECT md5('preview-account-' || role)::uuid, lower(role) || '@demo.local',
       crypt('123456',gen_salt('bf',12)), name, 'ACTIVE'
FROM (VALUES ('GUEST','Khách trải nghiệm'),('STUDENT','Minh Anh'),
             ('LECTURE','Giảng viên Hải Nam'),('LEADER','Trưởng bộ môn Thu Hà'),
             ('ADMIN','Quản trị viên')) AS demo(role,name)
ON CONFLICT(email) DO NOTHING;

INSERT INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u JOIN roles r ON lower(r.code) || '@demo.local'=u.email
WHERE u.email IN ('guest@demo.local','student@demo.local','lecture@demo.local','leader@demo.local','admin@demo.local')
ON CONFLICT DO NOTHING;

INSERT INTO courses(id,instructor_id,category_id,slug,title,short_description,description,
                    level,price,currency,status,estimated_duration_minutes)
SELECT md5('preview-course-' || n)::uuid,
       (SELECT id FROM users WHERE email='lecture@demo.local'),
       (SELECT id FROM categories WHERE slug=category_slug),
       slug,title,summary,
       summary || E'\n\nBạn sẽ học qua ví dụ thực tế, bài học ngắn và thực hành từng bước. '
       || 'Nội dung trong bản local là dữ liệu minh họa để kiểm tra luồng học, không phải khóa học hoàn chỉnh.',
       level,0,'VND','DRAFT',45
FROM (VALUES
 (1,'backend','java-tu-con-so-khong','Java từ con số không','Xây nền tảng Java vững chắc qua tư duy lập trình và thực hành hướng đối tượng.','BEGINNER'),
 (2,'frontend','react-va-typescript','React & TypeScript thực chiến','Tạo giao diện rõ ràng, quản lý trạng thái và kết nối API trong ứng dụng hiện đại.','INTERMEDIATE'),
 (3,'backend','spring-boot-api','Xây dựng API với Spring Boot','Từ REST API đến validation, bảo mật và kiến trúc ứng dụng dễ bảo trì.','INTERMEDIATE'),
 (4,'data-ai','ung-dung-ai-trong-lap-trinh','Ứng dụng AI trong lập trình','Hiểu cách làm việc với mô hình ngôn ngữ và xây trợ lý học tập có ngữ cảnh.','BEGINNER'),
 (5,'devops','docker-cho-lap-trinh-vien','Docker cho lập trình viên','Đóng gói ứng dụng, chạy môi trường local và xây pipeline triển khai.','BEGINNER'),
 (6,'backend','clean-architecture','Clean Architecture trong thực tế','Thiết kế module, kiểm soát dependency và bảo vệ nghiệp vụ bằng các boundary.','ADVANCED')
) AS demo(n,category_slug,slug,title,summary,level)
ON CONFLICT(slug) DO NOTHING;

INSERT INTO course_sections(id,course_id,title,display_order)
SELECT md5('preview-section-' || n)::uuid,md5('preview-course-' || n)::uuid,
       'Chương 1 · Làm quen và thực hành',0 FROM generate_series(1,6) n
ON CONFLICT DO NOTHING;

INSERT INTO lessons(id,section_id,title,content_url,duration_seconds,preview,display_order)
SELECT md5('preview-lesson-' || n || '-' || lesson)::uuid,md5('preview-section-' || n)::uuid,
       CASE lesson WHEN 1 THEN 'Giới thiệu lộ trình và cách học' ELSE 'Thực hành đầu tiên' END,
       '',120,lesson=1,lesson-1
FROM generate_series(1,6) n CROSS JOIN generate_series(1,2) lesson
ON CONFLICT DO NOTHING;

INSERT INTO flashcard_decks(id,owner_id,title,description)
SELECT md5('preview-deck-'||u.email)::uuid,u.id,'Lập trình · Kiến thức cốt lõi',
       'Ôn tập Java, HTTP và nguyên tắc thiết kế phần mềm.'
FROM users u WHERE u.email IN ('student@demo.local','lecture@demo.local','leader@demo.local','admin@demo.local')
ON CONFLICT DO NOTHING;

INSERT INTO flashcards(id,deck_id,front,back,display_order)
SELECT md5(d.id::text||'-'||n)::uuid,d.id,front,back,n-1
FROM flashcard_decks d CROSS JOIN (VALUES
 (1,'JVM là gì?','Java Virtual Machine thực thi Java bytecode và quản lý bộ nhớ trong quá trình chạy chương trình.'),
 (2,'HTTP 401 khác HTTP 403 thế nào?','401: chưa xác thực hoặc thông tin xác thực không hợp lệ. 403: đã xác định được yêu cầu nhưng không có quyền truy cập.'),
 (3,'Dependency Inversion là gì?','Nghiệp vụ cấp cao phụ thuộc abstraction; chi tiết triển khai phụ thuộc vào boundary do nghiệp vụ định nghĩa.')
) AS card(n,front,back)
WHERE d.id IN (SELECT md5('preview-deck-'||email)::uuid FROM users WHERE email LIKE '%@demo.local')
ON CONFLICT DO NOTHING;
COMMIT;
