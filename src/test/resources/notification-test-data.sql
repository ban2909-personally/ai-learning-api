INSERT INTO user_notifications (
    id, user_id, type, title, body, target_path, created_at, read_at
) VALUES
    (
        '2ee6684d-ebda-4b8c-8fd3-cf6a8f0ff101',
        '8ec33d91-0cc4-445f-9266-5f44d7bca900',
        'LESSON_COMPLETED',
        'Thông báo mới nhất',
        'Bạn đã hoàn thành bài học mới nhất.',
        '/my-learning',
        '2026-09-04T08:00:00Z',
        NULL
    ),
    (
        '2ee6684d-ebda-4b8c-8fd3-cf6a8f0ff102',
        '8ec33d91-0cc4-445f-9266-5f44d7bca900',
        'LESSON_COMPLETED',
        'Thông báo cũ hơn',
        'Bạn đã hoàn thành bài học cũ hơn.',
        '/my-learning',
        '2026-09-04T07:00:00Z',
        NULL
    ),
    (
        '2ee6684d-ebda-4b8c-8fd3-cf6a8f0ff103',
        'bfbc9cf4-5b2c-4db0-8728-27c65a99bb13',
        'LESSON_COMPLETED',
        'Thông báo người khác',
        'Nội dung không được lộ.',
        '/my-learning',
        '2026-09-04T09:00:00Z',
        NULL
    );
