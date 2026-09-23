\set ON_ERROR_STOP on

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '93000000-0000-0000-0000-000000000001',
    'notification-websocket-performance-instructor@example.invalid',
    '$2a$12$notificationwebsockethashisnotusedforlogin000000000000',
    'Notification WebSocket Performance Instructor',
    'ACTIVE'
);

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description,
    description, level, language, price, currency,
    estimated_duration_minutes, status, published_at
) VALUES (
    '13000000-0000-0000-0000-000000000001',
    '93000000-0000-0000-0000-000000000001',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'notification-websocket-performance',
    'Notification WebSocket Performance',
    'Isolated fixture for authenticated notification fan-out.',
    'Synthetic content used only by the isolated WebSocket performance drill.',
    'INTERMEDIATE', 'vi', 0, 'VND', 30, 'PUBLISHED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO course_sections (id, course_id, title, display_order)
VALUES (
    '23000000-0000-0000-0000-000000000001',
    '13000000-0000-0000-0000-000000000001',
    'Notification fan-out section',
    0
);

INSERT INTO lessons (
    id, section_id, title, content_url, duration_seconds, preview, display_order
) VALUES (
    '33000000-0000-0000-0000-000000000001',
    '23000000-0000-0000-0000-000000000001',
    'Notification fan-out lesson',
    'https://media.example.invalid/notification-websocket/lesson.mp4',
    1800,
    FALSE,
    0
);

ANALYZE users;
ANALYZE courses;
ANALYZE course_sections;
ANALYZE lessons;
