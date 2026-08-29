DELETE FROM enrollments WHERE user_id = '8ec33d91-0cc4-445f-9266-5f44d7bca900';
DELETE FROM courses WHERE instructor_id = 'ac13c859-6dfd-48cd-934f-2c38ce26de68';
DELETE FROM user_roles WHERE user_id = '8ec33d91-0cc4-445f-9266-5f44d7bca900';
DELETE FROM users WHERE id IN (
    '8ec33d91-0cc4-445f-9266-5f44d7bca900',
    'ac13c859-6dfd-48cd-934f-2c38ce26de68'
);
