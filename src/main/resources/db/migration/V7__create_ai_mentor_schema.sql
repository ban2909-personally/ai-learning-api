CREATE TABLE mentor_conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_mentor_conversations_user_lesson UNIQUE (user_id, lesson_id)
);

CREATE INDEX idx_mentor_conversations_user_updated
    ON mentor_conversations(user_id, updated_at DESC);

CREATE TABLE mentor_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES mentor_conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    provider_model VARCHAR(100),
    input_tokens INTEGER,
    output_tokens INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_mentor_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_mentor_messages_content CHECK (
        char_length(content) BETWEEN 1 AND 12000
    ),
    CONSTRAINT chk_mentor_messages_usage CHECK (
        (role = 'USER'
            AND provider_model IS NULL
            AND input_tokens IS NULL
            AND output_tokens IS NULL)
        OR
        (role = 'ASSISTANT'
            AND provider_model IS NOT NULL
            AND input_tokens >= 0
            AND output_tokens >= 0)
    )
);

CREATE INDEX idx_mentor_messages_conversation_history
    ON mentor_messages(conversation_id, created_at DESC, id DESC);
