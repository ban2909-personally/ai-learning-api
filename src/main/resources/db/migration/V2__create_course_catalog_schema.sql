CREATE TABLE categories (
    id UUID PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_categories_display_order CHECK (display_order >= 0)
);

CREATE TABLE courses (
    id UUID PRIMARY KEY,
    instructor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    slug VARCHAR(160) NOT NULL UNIQUE,
    title VARCHAR(180) NOT NULL,
    short_description VARCHAR(320) NOT NULL,
    description TEXT NOT NULL,
    level VARCHAR(30) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'vi',
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    thumbnail_url VARCHAR(500),
    estimated_duration_minutes INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_courses_level CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT chk_courses_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_courses_price CHECK (price >= 0),
    CONSTRAINT chk_courses_duration CHECK (estimated_duration_minutes >= 0),
    CONSTRAINT chk_courses_publish_time CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE INDEX idx_courses_public_catalog
    ON courses(status, published_at DESC, id)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_courses_category_public
    ON courses(category_id, status, published_at DESC);

CREATE INDEX idx_courses_instructor
    ON courses(instructor_id, status, updated_at DESC);

CREATE INDEX idx_courses_price
    ON courses(price)
    WHERE status = 'PUBLISHED';

INSERT INTO categories (id, slug, name, description, display_order) VALUES
    ('a99d920d-87ae-40ea-aed0-678885c26bfa', 'backend', 'Backend', 'Java, Spring Boot, API và kiến trúc hệ thống.', 10),
    ('3f70cb5c-cbff-46bc-af34-923368212e7f', 'frontend', 'Frontend', 'React, TypeScript và trải nghiệm web hiện đại.', 20),
    ('b1b05cd8-aea6-4b34-b015-a8cff77d18e2', 'data-ai', 'Data & AI', 'Dữ liệu, machine learning và ứng dụng AI.', 30),
    ('5961795e-0177-4296-baea-69f98611d765', 'devops', 'DevOps', 'Docker, CI/CD, cloud và vận hành hệ thống.', 40);
