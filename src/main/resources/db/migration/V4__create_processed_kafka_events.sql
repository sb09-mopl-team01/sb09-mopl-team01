CREATE TABLE processed_kafka_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_processed_kafka_events_event_key UNIQUE (event_key)
);
