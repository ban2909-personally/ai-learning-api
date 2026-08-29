INSERT INTO users (id, email, password_hash, display_name, status) VALUES
    ('8ec33d91-0cc4-445f-9266-5f44d7bca900', 'student.learning@example.com', 'not-used', 'Học viên Test', 'ACTIVE'),
    ('ac13c859-6dfd-48cd-934f-2c38ce26de68', 'instructor.learning@example.com', 'not-used', 'Giảng viên Learning', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id)
VALUES ('8ec33d91-0cc4-445f-9266-5f44d7bca900', '38f571c6-5713-4d82-9855-8ebc91a16516');

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description, description,
    level, language, price, currency, estimated_duration_minutes, status, published_at
) VALUES
    ('0f4c86cb-611d-4e22-8160-e89b60c66c72', 'ac13c859-6dfd-48cd-934f-2c38ce26de68',
     'a99d920d-87ae-40ea-aed0-678885c26bfa', 'java-free-learning-test',
     'Java miễn phí', 'Khóa học miễn phí để kiểm thử ghi danh.', 'Nội dung kiểm thử.',
     'BEGINNER', 'vi', 0, 'VND', 120, 'PUBLISHED', CURRENT_TIMESTAMP),
    ('e5bdc36d-552e-4ef5-b68a-665634d90ab3', 'ac13c859-6dfd-48cd-934f-2c38ce26de68',
     'a99d920d-87ae-40ea-aed0-678885c26bfa', 'java-paid-learning-test',
     'Java trả phí', 'Khóa học trả phí để kiểm thử policy.', 'Nội dung kiểm thử.',
     'BEGINNER', 'vi', 499000, 'VND', 180, 'PUBLISHED', CURRENT_TIMESTAMP);
