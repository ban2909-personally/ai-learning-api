INSERT INTO users (id, email, password_hash, display_name, status) VALUES
    ('df353774-10f6-4c7a-965b-8573113d37e8', 'owner.media@example.com', 'not-used', 'Giảng viên Media', 'ACTIVE'),
    ('27fdd7d8-3972-45b4-82cb-4056b59ec461', 'student.media@example.com', 'not-used', 'Học viên Media', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id) VALUES
    ('df353774-10f6-4c7a-965b-8573113d37e8', '254f63d9-eaac-4761-b16f-3caa7bd231d7'),
    ('27fdd7d8-3972-45b4-82cb-4056b59ec461', '38f571c6-5713-4d82-9855-8ebc91a16516');

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description, description,
    level, language, price, currency, estimated_duration_minutes, status, published_at
) VALUES (
    '0c7cccb2-44ac-4e91-852b-4589ad417a7d',
    'df353774-10f6-4c7a-965b-8573113d37e8',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'media-delivery-test',
    'Media delivery test',
    'Khóa học kiểm thử media.',
    'Nội dung kiểm thử media.',
    'BEGINNER', 'vi', 0, 'VND', 10, 'PUBLISHED', CURRENT_TIMESTAMP
);

INSERT INTO course_sections (id, course_id, title, display_order) VALUES (
    'a1159022-e168-45a4-922d-9e756117f223',
    '0c7cccb2-44ac-4e91-852b-4589ad417a7d',
    'Media section',
    0
);

INSERT INTO lessons (
    id, section_id, title, content_url, duration_seconds, preview, display_order,
    media_object_key, media_content_type, media_size_bytes, media_etag
) VALUES (
    '7c13978f-790b-4df4-9164-20c0af74c45b',
    'a1159022-e168-45a4-922d-9e756117f223',
    'Protected media',
    '/api/v1/media/courses/media-delivery-test/lessons/7c13978f-790b-4df4-9164-20c0af74c45b',
    10,
    FALSE,
    0,
    'courses/course/lessons/lesson/existing',
    'video/mp4',
    10,
    'existing-etag'
);

INSERT INTO enrollments (id, user_id, course_id, status, enrolled_at) VALUES (
    '286471d7-25ec-4721-ac5d-07a62908f5a4',
    '27fdd7d8-3972-45b4-82cb-4056b59ec461',
    '0c7cccb2-44ac-4e91-852b-4589ad417a7d',
    'ACTIVE',
    CURRENT_TIMESTAMP
);
