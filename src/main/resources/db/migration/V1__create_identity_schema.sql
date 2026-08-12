CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_sessions_user_active
    ON refresh_sessions(user_id, expires_at)
    WHERE revoked_at IS NULL;

INSERT INTO roles (id, code, name) VALUES
    ('38f571c6-5713-4d82-9855-8ebc91a16516', 'STUDENT', 'Học viên'),
    ('254f63d9-eaac-4761-b16f-3caa7bd231d7', 'INSTRUCTOR', 'Giảng viên'),
    ('0a90dd35-e7eb-45bd-86cd-8783ae6fe26d', 'ADMIN', 'Quản trị viên');

INSERT INTO permissions (id, code, description) VALUES
    ('7cb13bb1-0c7c-4a3a-95a7-3ccbb7871e3a', 'course:read', 'Xem khóa học'),
    ('4de2b0ec-b1cd-4292-bb88-fada7fc782ad', 'course:write', 'Quản lý khóa học'),
    ('3407002b-456b-48e8-82fc-26870c3d512a', 'admin:access', 'Truy cập chức năng quản trị');

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('38f571c6-5713-4d82-9855-8ebc91a16516', '7cb13bb1-0c7c-4a3a-95a7-3ccbb7871e3a'),
    ('254f63d9-eaac-4761-b16f-3caa7bd231d7', '7cb13bb1-0c7c-4a3a-95a7-3ccbb7871e3a'),
    ('254f63d9-eaac-4761-b16f-3caa7bd231d7', '4de2b0ec-b1cd-4292-bb88-fada7fc782ad'),
    ('0a90dd35-e7eb-45bd-86cd-8783ae6fe26d', '7cb13bb1-0c7c-4a3a-95a7-3ccbb7871e3a'),
    ('0a90dd35-e7eb-45bd-86cd-8783ae6fe26d', '4de2b0ec-b1cd-4292-bb88-fada7fc782ad'),
    ('0a90dd35-e7eb-45bd-86cd-8783ae6fe26d', '3407002b-456b-48e8-82fc-26870c3d512a');
