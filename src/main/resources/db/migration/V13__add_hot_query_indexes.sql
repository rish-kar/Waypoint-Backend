CREATE INDEX idx_subscriptions_user_updated_at
    ON subscriptions (user_id, updated_at DESC);

CREATE INDEX idx_subscriptions_user_status_renews_at
    ON subscriptions (user_id, status, renews_at);

CREATE INDEX idx_subscriptions_user_status_trial_ends_at
    ON subscriptions (user_id, status, trial_ends_at);

CREATE INDEX idx_subscriptions_user_status_ends_at
    ON subscriptions (user_id, status, ends_at);

CREATE INDEX idx_webhook_events_status_last_attempt_at
    ON webhook_events (processing_status, last_attempt_at);

CREATE INDEX idx_admin_audit_events_admin_created_at
    ON admin_audit_events (admin_id, created_at DESC);

CREATE INDEX idx_users_created_at
    ON users (created_at DESC);

CREATE INDEX idx_users_last_login_at
    ON users (last_login_at DESC);