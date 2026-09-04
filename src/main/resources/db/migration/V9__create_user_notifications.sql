CREATE TABLE user_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(500) NOT NULL,
    target_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ,
    CONSTRAINT chk_user_notifications_type CHECK (type IN ('LESSON_COMPLETED')),
    CONSTRAINT chk_user_notifications_title CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_user_notifications_body CHECK (length(trim(body)) > 0),
    CONSTRAINT chk_user_notifications_target CHECK (target_path LIKE '/%'),
    CONSTRAINT chk_user_notifications_read_time CHECK (read_at IS NULL OR read_at >= created_at)
);

CREATE INDEX idx_user_notifications_history
    ON user_notifications (user_id, created_at DESC, id DESC);

CREATE INDEX idx_user_notifications_unread
    ON user_notifications (user_id, created_at DESC, id DESC)
    WHERE read_at IS NULL;
