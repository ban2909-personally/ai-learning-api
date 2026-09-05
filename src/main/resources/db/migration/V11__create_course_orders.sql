CREATE TABLE course_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    course_id UUID NOT NULL,
    course_slug VARCHAR(160) NOT NULL,
    course_title VARCHAR(180) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    idempotency_key UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_course_orders_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_course_orders_positive_amount CHECK (amount > 0),
    CONSTRAINT ck_course_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_course_orders_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_course_orders_user_recent
    ON course_orders (user_id, created_at DESC, id DESC);
