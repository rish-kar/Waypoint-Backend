ALTER TABLE users
    ADD COLUMN ai_trial_requests_used INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_ai_trial_requests_used_non_negative
        CHECK (ai_trial_requests_used >= 0);
