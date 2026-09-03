INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '18111111-1111-4111-8111-111111111111',
    'mentor.student@example.com',
    '$2a$12$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuu',
    'Mentor Student',
    'ACTIVE'
);

INSERT INTO categories (id, slug, name, display_order)
VALUES ('28222222-2222-4222-8222-222222222222', 'mentor-test', 'Mentor Test', 99);

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description, description,
    level, language, price, currency, estimated_duration_minutes, status, published_at
) VALUES (
    '38333333-3333-4333-8333-333333333333',
    '18111111-1111-4111-8111-111111111111',
    '28222222-2222-4222-8222-222222222222',
    'mentor-test-course',
    'Mentor test course',
    'Course used by mentor integration tests',
    'Integration test fixture',
    'BEGINNER',
    'vi',
    0,
    'VND',
    10,
    'PUBLISHED',
    CURRENT_TIMESTAMP
);

INSERT INTO course_sections (id, course_id, title, display_order)
VALUES (
    '48444444-4444-4444-8444-444444444444',
    '38333333-3333-4333-8333-333333333333',
    'Mentor section',
    0
);

INSERT INTO lessons (id, section_id, title, content_url, duration_seconds, preview, display_order)
VALUES (
    '58555555-5555-4555-8555-555555555555',
    '48444444-4444-4444-8444-444444444444',
    'Mentor lesson',
    'https://video.example/mentor-test',
    600,
    FALSE,
    0
);

INSERT INTO enrollments (id, user_id, course_id, status, enrolled_at)
VALUES (
    '68666666-6666-4666-8666-666666666666',
    '18111111-1111-4111-8111-111111111111',
    '38333333-3333-4333-8333-333333333333',
    'ACTIVE',
    CURRENT_TIMESTAMP
);

