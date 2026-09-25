INSERT INTO roles (id, code, name) VALUES
 ('10000000-0000-0000-0000-000000000001', 'GUEST', 'Khách'),
 ('10000000-0000-0000-0000-000000000002', 'LECTURE', 'Giảng viên'),
 ('10000000-0000-0000-0000-000000000003', 'LEADER', 'Trưởng bộ môn');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.code = 'GUEST' AND p.code = 'course:read')
   OR (r.code IN ('LECTURE', 'LEADER') AND p.code IN ('course:read', 'course:write'));
