CREATE TABLE learning_completion_facts (
    event_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    course_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_learning_completion_fact UNIQUE (user_id, enrollment_id, lesson_id),
    CONSTRAINT chk_learning_completion_projection_time CHECK (projected_at >= completed_at)
);

CREATE INDEX idx_learning_completion_user_course
    ON learning_completion_facts (user_id, course_id, completed_at DESC);

CREATE INDEX idx_learning_completion_user_history
    ON learning_completion_facts (user_id, completed_at DESC, event_id);
