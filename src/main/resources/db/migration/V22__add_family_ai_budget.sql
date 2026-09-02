CREATE TABLE family_ai_pool_usage (
    period_key VARCHAR(7) PRIMARY KEY,
    spent_microrupees BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE family_ai_user_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    period_key VARCHAR(7) NOT NULL,
    spent_microrupees BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_family_ai_user_period UNIQUE (user_id, period_key)
);

CREATE INDEX idx_family_ai_user_usage_period ON family_ai_user_usage (period_key);
