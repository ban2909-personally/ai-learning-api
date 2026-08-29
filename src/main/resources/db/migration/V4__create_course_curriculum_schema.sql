CREATE TABLE course_sections (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT uq_course_sections_order UNIQUE (course_id, display_order),
    CONSTRAINT chk_course_sections_order CHECK (display_order >= 0)
);

CREATE TABLE lessons (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES course_sections(id) ON DELETE CASCADE,
    title VARCHAR(180) NOT NULL,
    content_url VARCHAR(1000) NOT NULL,
    duration_seconds INTEGER NOT NULL DEFAULT 0,
    preview BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL,
    CONSTRAINT uq_lessons_order UNIQUE (section_id, display_order),
    CONSTRAINT chk_lessons_duration CHECK (duration_seconds >= 0),
    CONSTRAINT chk_lessons_order CHECK (display_order >= 0)
);

CREATE INDEX idx_course_sections_course_order ON course_sections(course_id, display_order);
CREATE INDEX idx_lessons_section_order ON lessons(section_id, display_order);
