\set ON_ERROR_STOP on

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '92000000-0000-0000-0000-000000000001',
    'mentor-performance-instructor@example.invalid',
    '$2a$12$aimentorperformancehashisnotusedforlogin000000000000000',
    'AI Mentor Performance Instructor',
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
    '12000000-0000-0000-0000-000000000001',
    '92000000-0000-0000-0000-000000000001',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'ai-mentor-performance',
    'AI Mentor Performance',
    'Isolated fixture for AI Mentor concurrency and token-usage evidence.',
    'Synthetic metadata used only by the isolated AI Mentor performance profile.',
    'INTERMEDIATE',
    'vi',
    0,
    'VND',
    60,
    'PUBLISHED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO course_sections (id, course_id, title, display_order)
VALUES (
    '22000000-0000-0000-0000-000000000001',
    '12000000-0000-0000-0000-000000000001',
    'Mentor performance section',
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
) VALUES (
    '32000000-0000-0000-0000-000000000001',
    '22000000-0000-0000-0000-000000000001',
    'Dependency inversion boundaries',
    'https://media.example.invalid/ai-mentor-performance/lesson.mp4',
    1800,
    FALSE,
    0
);

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
    WHERE slug = 'ai-mentor-performance'
      AND status = 'PUBLISHED'
      AND price = 0;

    SELECT count(*) INTO lesson_count
    FROM lessons
    WHERE id = '32000000-0000-0000-0000-000000000001'
      AND preview = FALSE;

    IF course_count <> 1 THEN
        RAISE EXCEPTION 'expected one published free mentor performance course, found %', course_count;
    END IF;
    IF lesson_count <> 1 THEN
        RAISE EXCEPTION 'expected one protected mentor performance lesson, found %', lesson_count;
    END IF;
END
$$;
