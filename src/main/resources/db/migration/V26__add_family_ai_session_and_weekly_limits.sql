ALTER TABLE special_premium_grants ADD COLUMN ai_period_request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE special_premium_grants ADD COLUMN ai_period_input_tokens BIGINT NOT NULL DEFAULT 0;

ALTER TABLE special_premium_grants ADD COLUMN ai_session_started_at TIMESTAMP;
ALTER TABLE special_premium_grants ADD COLUMN ai_session_spent_microrupees BIGINT NOT NULL DEFAULT 0;
ALTER TABLE special_premium_grants ADD COLUMN ai_session_request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE special_premium_grants ADD COLUMN ai_session_input_tokens BIGINT NOT NULL DEFAULT 0;

ALTER TABLE special_premium_grants ADD COLUMN ai_weekly_started_at TIMESTAMP;
ALTER TABLE special_premium_grants ADD COLUMN ai_weekly_spent_microrupees BIGINT NOT NULL DEFAULT 0;
ALTER TABLE special_premium_grants ADD COLUMN ai_weekly_request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE special_premium_grants ADD COLUMN ai_weekly_input_tokens BIGINT NOT NULL DEFAULT 0;
