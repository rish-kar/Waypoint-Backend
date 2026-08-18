CREATE TABLE billing_checkout_sessions (
    user_id UUID PRIMARY KEY,
    plan VARCHAR(32) NOT NULL,
    checkout_url VARCHAR(2048) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_billing_checkout_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
