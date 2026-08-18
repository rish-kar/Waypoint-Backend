ALTER TABLE billing_checkout_sessions
    ALTER COLUMN checkout_url DROP NOT NULL;

ALTER TABLE billing_checkout_sessions
    ADD COLUMN intent_id UUID;

ALTER TABLE billing_checkout_sessions
    ADD COLUMN provider_request_started_at TIMESTAMP;

CREATE UNIQUE INDEX idx_billing_checkout_sessions_intent_id
    ON billing_checkout_sessions (intent_id);
