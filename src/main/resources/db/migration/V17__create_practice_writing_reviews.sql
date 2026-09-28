CREATE TABLE practice_writing_reviews (
    attempt_id UUID NOT NULL,
    question_id UUID NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES users(id),
    task_score SMALLINT NOT NULL CHECK (task_score BETWEEN 0 AND 5),
    coherence_score SMALLINT NOT NULL CHECK (coherence_score BETWEEN 0 AND 5),
    vocabulary_score SMALLINT NOT NULL CHECK (vocabulary_score BETWEEN 0 AND 5),
    grammar_score SMALLINT NOT NULL CHECK (grammar_score BETWEEN 0 AND 5),
    feedback VARCHAR(2000) NOT NULL CHECK (length(trim(feedback)) > 0),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attempt_id, question_id),
    FOREIGN KEY (attempt_id, question_id)
        REFERENCES practice_answers(attempt_id, question_id) ON DELETE CASCADE
);
CREATE INDEX idx_practice_writing_reviews_reviewer
    ON practice_writing_reviews(reviewer_id, reviewed_at DESC);
CREATE INDEX idx_practice_attempts_submitted
    ON practice_attempts(submitted_at, id) WHERE status = 'SUBMITTED';
