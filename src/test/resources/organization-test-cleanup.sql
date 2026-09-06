DELETE FROM organizations
WHERE created_by = 'aa57ecf4-bcb4-4ca4-91f9-a23c2f9aee11';

DELETE FROM users
WHERE id IN (
    'aa57ecf4-bcb4-4ca4-91f9-a23c2f9aee11',
    'ca6a0d72-eaec-4dd2-b375-7bbc75b09934'
);
