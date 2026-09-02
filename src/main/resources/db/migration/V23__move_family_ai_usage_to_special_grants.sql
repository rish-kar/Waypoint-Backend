ALTER TABLE special_premium_grants ADD COLUMN ai_period_key VARCHAR(7);
ALTER TABLE special_premium_grants ADD COLUMN ai_spent_microrupees BIGINT NOT NULL DEFAULT 0;

DROP TABLE family_ai_user_usage;
DROP TABLE family_ai_pool_usage;
