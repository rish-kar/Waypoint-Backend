CREATE TABLE microsoft_oauth_transactions (
    id UUID PRIMARY KEY,
    state_hash VARCHAR(64) UNIQUE NOT NULL,
    code_verifier_ciphertext VARCHAR(1024) NOT NULL,
    extension_redirect_uri VARCHAR(2048) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_microsoft_oauth_transactions_expires_at ON microsoft_oauth_transactions (expires_at);

CREATE TABLE microsoft_provider_credentials (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_user_id VARCHAR(255) UNIQUE NOT NULL,
    refresh_token_ciphertext VARCHAR(4096) NOT NULL,
    scopes VARCHAR(1000),
    access_token_expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL
);

CREATE INDEX idx_microsoft_provider_credentials_user_id ON microsoft_provider_credentials (user_id);

CREATE TABLE microsoft_exchange_codes (
    id UUID PRIMARY KEY,
    code_hash VARCHAR(64) UNIQUE NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_microsoft_exchange_codes_expires_at ON microsoft_exchange_codes (expires_at);
CREATE INDEX idx_microsoft_exchange_codes_user_id ON microsoft_exchange_codes (user_id);
