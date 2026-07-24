ALTER TABLE batch_step_execution
    ADD COLUMN create_time TIMESTAMP;

UPDATE batch_step_execution
SET create_time = COALESCE(start_time, last_updated, CURRENT_TIMESTAMP)
WHERE create_time IS NULL;

ALTER TABLE batch_step_execution
    ALTER COLUMN create_time SET NOT NULL,
    ALTER COLUMN start_time DROP NOT NULL;
