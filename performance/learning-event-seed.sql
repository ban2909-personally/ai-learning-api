\set ON_ERROR_STOP on

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '92000000-0000-0000-0000-000000000001',
    'learning-event-performance-instructor@example.invalid',
    '$2a$12$learningeventperformancehashisnotusedforlogin0000000000000',
    'Learning Event Performance Instructor',
    'ACTIVE'
);

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description,
    description, level, language, price, currency,
    estimated_duration_minutes, status, published_at
) VALUES (
    '12000000-0000-0000-0000-000000000001',
    '92000000-0000-0000-0000-000000000001',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'learning-event-performance',
    'Learning Event Performance',
    'Isolated fixture for the durable learning-event pipeline.',
    'Synthetic content used only by the isolated event throughput drill.',
    'INTERMEDIATE', 'vi', 0, 'VND', 240, 'PUBLISHED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO course_sections (id, course_id, title, display_order)
VALUES (
    '22000000-0000-0000-0000-000000000001',
    '12000000-0000-0000-0000-000000000001',
    'Durable event section',
    0
);

INSERT INTO lessons (
    id, section_id, title, content_url, duration_seconds, preview, display_order
)
SELECT
    ('32000000-0000-0000-0000-' || lpad(lesson_number::text, 12, '0'))::uuid,
    '22000000-0000-0000-0000-000000000001'::uuid,
    'Event lesson ' || lesson_number,
    'https://media.example.invalid/learning-events/lesson-' || lesson_number || '.mp4',
    1800,
    FALSE,
    lesson_number - 1
FROM generate_series(1, 8) AS generated(lesson_number);

ANALYZE users;
ANALYZE courses;
ANALYZE course_sections;
ANALYZE lessons;
