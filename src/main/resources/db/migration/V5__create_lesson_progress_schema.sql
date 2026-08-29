CREATE TABLE lesson_progress (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    position_seconds INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_lesson_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT chk_lesson_progress_position CHECK (position_seconds >= 0)
);

CREATE INDEX idx_lesson_progress_enrollment_updated
    ON lesson_progress(enrollment_id, updated_at DESC);
