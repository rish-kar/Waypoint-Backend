ALTER TABLE webhook_events ADD COLUMN processing_started_at TIMESTAMP;
UPDATE webhook_events
SET processing_started_at = COALESCE(processed_at, received_at)
WHERE processing_started_at IS NULL;
ALTER TABLE webhook_events ALTER COLUMN processing_started_at SET NOT NULL;
