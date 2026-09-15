\set ON_ERROR_STOP on

INSERT INTO users (id, email, password_hash, display_name, status)
VALUES (
    '90000000-0000-0000-0000-000000000001',
    'performance-instructor@example.invalid',
    '$2a$12$performancebaselinehashisnotusedforauthentication0000000',
    'Performance Baseline Instructor',
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
)
SELECT
    ('10000000-0000-0000-0000-' || lpad(series_number::text, 12, '0'))::uuid,
    '90000000-0000-0000-0000-000000000001'::uuid,
    CASE (series_number - 1) % 4
        WHEN 0 THEN 'a99d920d-87ae-40ea-aed0-678885c26bfa'::uuid
        WHEN 1 THEN '3f70cb5c-cbff-46bc-af34-923368212e7f'::uuid
        WHEN 2 THEN 'b1b05cd8-aea6-4b34-b015-a8cff77d18e2'::uuid
        ELSE '5961795e-0177-4296-baea-69f98611d765'::uuid
    END,
    'performance-course-' || lpad(series_number::text, 5, '0'),
    'Performance Course ' || lpad(series_number::text, 5, '0'),
    'Deterministic catalog performance fixture ' || series_number,
    'Synthetic content used only by the isolated catalog performance regression drill.',
    CASE (series_number - 1) % 3
        WHEN 0 THEN 'BEGINNER'
        WHEN 1 THEN 'INTERMEDIATE'
        ELSE 'ADVANCED'
    END,
    'vi',
    ((series_number - 1) % 20) * 100000,
    'VND',
    30 + ((series_number - 1) % 300),
    'PUBLISHED',
    TIMESTAMPTZ '2026-01-01 00:00:00+00' + series_number * INTERVAL '1 second'
FROM generate_series(1, 5000) AS generated(series_number);

ANALYZE users;
ANALYZE categories;
ANALYZE courses;

DO $$
DECLARE
    published_count bigint;
BEGIN
    SELECT count(*) INTO published_count
    FROM courses
    WHERE status = 'PUBLISHED';

    IF published_count <> 5000 THEN
        RAISE EXCEPTION 'expected 5000 published courses, found %', published_count;
    END IF;
END
$$;
