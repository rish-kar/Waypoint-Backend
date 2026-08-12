ALTER TABLE webhook_events
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 1;

ALTER TABLE webhook_events
    ADD COLUMN last_attempt_at TIMESTAMP NULL;

UPDATE webhook_events
SET last_attempt_at = received_at
WHERE last_attempt_at IS NULL;

CREATE INDEX idx_webhook_events_last_attempt_at
    ON webhook_events (last_attempt_at);
