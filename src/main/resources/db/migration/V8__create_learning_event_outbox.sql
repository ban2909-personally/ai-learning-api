CREATE TABLE learning_event_outbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    schema_version SMALLINT NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    locked_by VARCHAR(64),
    locked_until TIMESTAMPTZ,
    last_failure_code VARCHAR(64),
    CONSTRAINT uq_learning_event_outbox_aggregate_event UNIQUE (aggregate_id, event_type),
    CONSTRAINT chk_learning_event_outbox_schema_version CHECK (schema_version > 0),
    CONSTRAINT chk_learning_event_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_learning_event_outbox_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT chk_learning_event_outbox_payload_size CHECK (octet_length(payload::TEXT) <= 16384),
    CONSTRAINT chk_learning_event_outbox_lock_pair CHECK (
        (locked_by IS NULL AND locked_until IS NULL)
        OR (locked_by IS NOT NULL AND locked_until IS NOT NULL)
    )
);

CREATE INDEX idx_learning_event_outbox_pending
    ON learning_event_outbox (available_at, occurred_at, event_id)
    WHERE published_at IS NULL;

CREATE INDEX idx_learning_event_outbox_published
    ON learning_event_outbox (published_at)
    WHERE published_at IS NOT NULL;
