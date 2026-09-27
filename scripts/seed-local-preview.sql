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

INSERT INTO categories(id,slug,name,description,display_order)
SELECT md5('preview-english-category-' || slug)::uuid,slug,name,description,display_order
FROM (VALUES
 ('listening','Nghe tiếng Anh','Luyện nghe hội thoại và thông báo thực tế.',10),
 ('reading','Đọc tiếng Anh','Đọc hiểu email, thông báo và tài liệu.',20),
 ('writing','Viết tiếng Anh','Viết email và diễn đạt ý tưởng rõ ràng.',30),
 ('exam-prep','Luyện thi','Chiến lược và đề thi thử tiếng Anh.',40)
) AS demo(slug,name,description,display_order)
ON CONFLICT(slug) DO NOTHING;

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
                    level,price,currency,status,estimated_duration_minutes,published_at)
SELECT md5('preview-course-' || n)::uuid,
       (SELECT id FROM users WHERE email='lecture@demo.local'),
       (SELECT id FROM categories WHERE slug=category_slug),
       slug,title,summary,
       summary || E'\n\nBạn sẽ học qua ví dụ thực tế, bài học ngắn và thực hành từng bước. '
       || 'Nội dung trong bản local là dữ liệu minh họa để kiểm tra luồng học, không phải khóa học hoàn chỉnh.',
       level,0,'VND','PUBLISHED',45,CURRENT_TIMESTAMP
FROM (VALUES
 (1,'listening','luyen-nghe-tieng-anh-can-ban','Luyện nghe tiếng Anh căn bản','Làm quen với hội thoại ngắn, nhận biết thông tin chính và từ khóa.','BEGINNER'),
 (2,'reading','doc-hieu-email-cong-viec','Đọc hiểu email công việc','Rèn kỹ năng tìm ý chính và chi tiết trong email tiếng Anh.','BEGINNER'),
 (3,'listening','luyen-nghe-theo-dang-toeic','Luyện nghe theo dạng TOEIC','Thực hành nghe mô tả, hội thoại và thông báo trong môi trường công việc.','INTERMEDIATE'),
 (4,'reading','luyen-doc-theo-dang-toeic','Luyện đọc theo dạng TOEIC','Ôn ngữ pháp, từ vựng và đọc hiểu văn bản công việc.','INTERMEDIATE'),
 (5,'writing','viet-email-tieng-anh-cong-viec','Viết email tiếng Anh công việc','Viết email rõ mục đích, đúng ngữ cảnh và giọng điệu.','BEGINNER'),
 (6,'exam-prep','chien-luoc-lam-de-tieng-anh','Chiến lược làm đề tiếng Anh','Quản lý thời gian, phân tích lỗi sai và luyện thi thử hiệu quả.','ADVANCED')
) AS demo(n,category_slug,slug,title,summary,level)
ON CONFLICT(id) DO NOTHING;

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
SELECT md5('preview-deck-'||u.email)::uuid,u.id,'English · Workplace vocabulary',
       'Ôn tập từ vựng và mẫu câu tiếng Anh.'
FROM users u WHERE u.email IN ('student@demo.local','lecture@demo.local','leader@demo.local','admin@demo.local')
ON CONFLICT DO NOTHING;

INSERT INTO flashcards(id,deck_id,front,back,display_order)
SELECT md5(d.id::text||'-'||n)::uuid,d.id,front,back,n-1
FROM flashcard_decks d CROSS JOIN (VALUES
 (1,'schedule','Lịch trình; ví dụ: The meeting is on my schedule.'),
 (2,'Could you clarify that?','Bạn có thể làm rõ điều đó không? Dùng khi cần hỏi lại trong cuộc họp.'),
 (3,'deadline','Hạn chót; ví dụ: The deadline is Friday afternoon.')
) AS card(n,front,back)
WHERE d.id IN (SELECT md5('preview-deck-'||email)::uuid FROM users WHERE email LIKE '%@demo.local')
ON CONFLICT DO NOTHING;
COMMIT;
