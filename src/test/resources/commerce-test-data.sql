INSERT INTO users (id, email, password_hash, display_name, status)
VALUES
    ('8ec33d91-0cc4-445f-9266-5f44d7bca900', 'learner.commerce@example.com', 'not-used-in-test', 'Học viên Commerce', 'ACTIVE'),
    ('d07ce7f9-e311-43a6-bc02-8c0d605ae955', 'instructor.commerce@example.com', 'not-used-in-test', 'Giảng viên Commerce', 'ACTIVE');

INSERT INTO courses (
    id, instructor_id, category_id, slug, title, short_description, description,
    level, language, price, currency, estimated_duration_minutes, status, published_at
) VALUES (
    '58d19684-f4dc-46a7-b716-8ba176e185f3',
    'd07ce7f9-e311-43a6-bc02-8c0d605ae955',
    'a99d920d-87ae-40ea-aed0-678885c26bfa',
    'clean-architecture',
    'Clean Architecture',
    'Thiết kế phần mềm dễ bảo trì.',
    'Thực hành kiến trúc module và ports and adapters.',
    'INTERMEDIATE',
    'vi',
    499000,
    'VND',
    600,
    'PUBLISHED',
    CURRENT_TIMESTAMP
);
