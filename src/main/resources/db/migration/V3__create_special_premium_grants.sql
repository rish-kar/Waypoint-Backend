INSERT INTO plans (
    code,
    display_name,
    billing_interval,
    price_cents,
    currency,
    premium,
    active,
    created_at,
    updated_at
) VALUES (
    'PREMIUM_SPECIAL',
    'Premium Special',
    'NONE',
    0,
    'USD',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

CREATE TABLE special_premium_grants (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    active BOOLEAN NOT NULL,
    valid_until TIMESTAMP NULL,
    reason VARCHAR(255) NOT NULL,
    granted_by VARCHAR(100) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    revoked_by VARCHAR(100) NULL,
    revoked_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_special_premium_grants_active ON special_premium_grants (active);
CREATE INDEX idx_special_premium_grants_valid_until ON special_premium_grants (valid_until);
