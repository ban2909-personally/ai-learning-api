CREATE TABLE community_space_messages (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id UUID NOT NULL UNIQUE,
    space_id UUID NOT NULL REFERENCES community_spaces(id),
    author_id UUID NOT NULL REFERENCES users(id),
    client_id UUID NOT NULL,
    body VARCHAR(2000) NOT NULL CHECK (length(trim(body)) > 0),
    status VARCHAR(8) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REMOVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (space_id, author_id, client_id)
);
CREATE INDEX idx_space_messages_cursor ON community_space_messages(space_id, sequence);
