INSERT INTO users (id, email, password_hash, display_name, status)
VALUES ('d07ce7f9-e311-43a6-bc02-8c0d605ae955', 'instructor.catalog@example.com', 'not-used-in-test', 'Giảng viên Java', 'ACTIVE');

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description, description,
    level, language, price, currency, estimated_duration_minutes, status, published_at
) VALUES
    ('d38a1970-251b-4434-901c-d286102283a3', 'd07ce7f9-e311-43a6-bc02-8c0d605ae955',
     'a99d920d-87ae-40ea-aed0-678885c26bfa', 'spring-boot-api-thuc-chien',
     'Spring Boot API thực chiến', 'Xây REST API production-ready với Spring Boot.',
     'Học kiến trúc REST, transaction, security và testing qua dự án thực tế.',
     'INTERMEDIATE', 'vi', 799000, 'VND', 960, 'PUBLISHED', CURRENT_TIMESTAMP),
    ('2e5aaf09-9086-4a80-9a74-9aab1bf50747', 'd07ce7f9-e311-43a6-bc02-8c0d605ae955',
     '3f70cb5c-cbff-46bc-af34-923368212e7f', 'react-ban-nhap',
     'React bản nháp', 'Khóa học chưa được công khai.', 'Nội dung nháp.',
     'BEGINNER', 'vi', 399000, 'VND', 480, 'DRAFT', NULL);
