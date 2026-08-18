CREATE TABLE revoked_jwt_tokens (
    token_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_revoked_jwt_tokens_expires_at
    ON revoked_jwt_tokens(expires_at);
