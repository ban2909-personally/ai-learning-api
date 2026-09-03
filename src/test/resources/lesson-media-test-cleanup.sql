DELETE FROM lesson_progress WHERE lesson_id = '7c13978f-790b-4df4-9164-20c0af74c45b';
DELETE FROM enrollments WHERE course_id = '0c7cccb2-44ac-4e91-852b-4589ad417a7d';
DELETE FROM lessons WHERE id = '7c13978f-790b-4df4-9164-20c0af74c45b';
DELETE FROM course_sections WHERE id = 'a1159022-e168-45a4-922d-9e756117f223';
DELETE FROM courses WHERE id = '0c7cccb2-44ac-4e91-852b-4589ad417a7d';
DELETE FROM user_roles WHERE user_id IN (
    'df353774-10f6-4c7a-965b-8573113d37e8',
    '27fdd7d8-3972-45b4-82cb-4056b59ec461'
);
DELETE FROM users WHERE id IN (
    'df353774-10f6-4c7a-965b-8573113d37e8',
    '27fdd7d8-3972-45b4-82cb-4056b59ec461'
);
