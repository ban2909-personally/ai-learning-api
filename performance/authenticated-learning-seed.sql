\set ON_ERROR_STOP on

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '91000000-0000-0000-0000-000000000001',
    'learning-performance-instructor@example.invalid',
    '$2a$12$authenticatedlearninghashisnotusedforlogin0000000000000',
    'Authenticated Learning Instructor',
    'ACTIVE'
);

INSERT INTO courses (
    id,
    instructor_id,
    category_id,
    slug,
    title,
    short_description,
    description,
    level,
    language,
    price,
    currency,
    estimated_duration_minutes,
    status,
    published_at
) VALUES (
    '11000000-0000-0000-0000-000000000001',
    '91000000-0000-0000-0000-000000000001',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'authenticated-learning-performance',
    'Authenticated Learning Performance',
    'Isolated fixture for authenticated learning performance profiles.',
    'Synthetic content used only by the isolated authenticated learning drill.',
    'INTERMEDIATE',
    'vi',
    0,
    'VND',
    120,
    'PUBLISHED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO course_sections (id, course_id, title, display_order)
VALUES (
    '21000000-0000-0000-0000-000000000001',
    '11000000-0000-0000-0000-000000000001',
    'Performance section',
    0
);

INSERT INTO lessons (
    id,
    section_id,
    title,
    content_url,
    duration_seconds,
    preview,
    display_order
)
SELECT
    ('31000000-0000-0000-0000-' || lpad(lesson_number::text, 12, '0'))::uuid,
    '21000000-0000-0000-0000-000000000001'::uuid,
    'Authenticated lesson ' || lesson_number,
    'https://media.example.invalid/authenticated-learning/lesson-' || lesson_number || '.mp4',
    1800,
    FALSE,
    lesson_number - 1
FROM generate_series(1, 4) AS generated(lesson_number);

ANALYZE users;
ANALYZE courses;
ANALYZE course_sections;
ANALYZE lessons;

DO $$
DECLARE
    course_count bigint;
    lesson_count bigint;
BEGIN
    SELECT count(*) INTO course_count
    FROM courses
    WHERE slug = 'authenticated-learning-performance'
      AND status = 'PUBLISHED'
      AND price = 0;

    SELECT count(*) INTO lesson_count
    FROM lessons
    WHERE section_id = '21000000-0000-0000-0000-000000000001';

    IF course_count <> 1 THEN
        RAISE EXCEPTION 'expected one published free performance course, found %', course_count;
    END IF;
    IF lesson_count <> 4 THEN
        RAISE EXCEPTION 'expected four performance lessons, found %', lesson_count;
    END IF;
END
$$;
