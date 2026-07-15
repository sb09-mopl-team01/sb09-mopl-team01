CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(249) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_event_outbox_retry_count CHECK (retry_count >= 0)
);

CREATE INDEX idx_event_outbox_claimable
    ON event_outbox (status, next_attempt_at, created_at, id);

CREATE INDEX idx_event_outbox_claimed_at
    ON event_outbox (status, claimed_at)
    WHERE status = 'CLAIMED';

CREATE INDEX idx_event_outbox_published_at
    ON event_outbox (published_at)
    WHERE status = 'PUBLISHED';
