\set ON_ERROR_STOP on

\if :{?media_etag}
\else
    \echo 'media_etag psql variable is required'
    \quit 3
\endif

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '94000000-0000-0000-0000-000000000001',
    'media-performance-instructor@example.invalid',
    '$2a$12$mediabandwidthhashisnotusedforlogin000000000000000000000',
    'Media Performance Instructor',
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
    '14000000-0000-0000-0000-000000000001',
    '94000000-0000-0000-0000-000000000001',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'media-bandwidth-performance',
    'Media Bandwidth Performance',
    'Isolated fixture for authenticated media range delivery.',
    'Synthetic bytes used only by the isolated media bandwidth drill.',
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
    '24000000-0000-0000-0000-000000000001',
    '14000000-0000-0000-0000-000000000001',
    'Media performance section',
    0
);

INSERT INTO lessons (
    id,
    section_id,
    title,
    content_url,
    duration_seconds,
    preview,
    display_order,
    media_object_key,
    media_content_type,
    media_size_bytes,
    media_etag
) VALUES (
    '34000000-0000-0000-0000-000000000001',
    '24000000-0000-0000-0000-000000000001',
    'Protected media range lesson',
    '/api/v1/media/courses/media-bandwidth-performance/lessons/34000000-0000-0000-0000-000000000001',
    1800,
    FALSE,
    0,
    'courses/14000000-0000-0000-0000-000000000001/lessons/34000000-0000-0000-0000-000000000001/media',
    'video/mp4',
    33554432,
    :'media_etag'
);

ANALYZE users;
ANALYZE courses;
ANALYZE course_sections;
ANALYZE lessons;

DO $$
DECLARE
    fixture_count bigint;
BEGIN
    SELECT count(*) INTO fixture_count
    FROM courses c
    JOIN course_sections s ON s.course_id = c.id
    JOIN lessons l ON l.section_id = s.id
    WHERE c.slug = 'media-bandwidth-performance'
      AND c.status = 'PUBLISHED'
      AND c.price = 0
      AND l.id = '34000000-0000-0000-0000-000000000001'
      AND l.media_object_key = 'courses/14000000-0000-0000-0000-000000000001/lessons/34000000-0000-0000-0000-000000000001/media'
      AND l.media_content_type = 'video/mp4'
      AND l.media_size_bytes = 33554432
      AND l.media_etag = :'media_etag';

    IF fixture_count <> 1 THEN
        RAISE EXCEPTION 'expected one complete media performance fixture, found %', fixture_count;
    END IF;
END
$$;
