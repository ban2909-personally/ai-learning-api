CREATE TABLE enrollments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_enrollments_user_course UNIQUE (user_id, course_id),
    CONSTRAINT chk_enrollments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_enrollments_completion CHECK (status <> 'COMPLETED' OR completed_at IS NOT NULL)
);

CREATE INDEX idx_enrollments_user_recent
    ON enrollments(user_id, enrolled_at DESC)
    WHERE status IN ('ACTIVE', 'COMPLETED');

CREATE INDEX idx_enrollments_course_status
    ON enrollments(course_id, status);
