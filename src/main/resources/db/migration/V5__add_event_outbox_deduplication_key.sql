ALTER TABLE event_outbox
    ADD COLUMN deduplication_key VARCHAR(255);

UPDATE event_outbox
SET deduplication_key = id::text;

ALTER TABLE event_outbox
    ALTER COLUMN deduplication_key SET NOT NULL;

ALTER TABLE event_outbox
    ADD CONSTRAINT uk_event_outbox_deduplication_key UNIQUE (deduplication_key);
