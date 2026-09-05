DELETE FROM course_orders
WHERE user_id = '8ec33d91-0cc4-445f-9266-5f44d7bca900';

DELETE FROM courses
WHERE id = '58d19684-f4dc-46a7-b716-8ba176e185f3';

DELETE FROM users
WHERE id IN (
    '8ec33d91-0cc4-445f-9266-5f44d7bca900',
    'd07ce7f9-e311-43a6-bc02-8c0d605ae955'
);
