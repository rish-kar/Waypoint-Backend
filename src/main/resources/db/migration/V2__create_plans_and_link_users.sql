CREATE TABLE plans (
    code VARCHAR(50) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    billing_interval VARCHAR(20) NOT NULL,
    price_cents INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    premium BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_plans_price_non_negative CHECK (price_cents >= 0)
);

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
) VALUES
    ('FREE', 'Free', 'NONE', 0, 'USD', FALSE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PREMIUM_MONTHLY', 'Premium Monthly', 'MONTHLY', 499, 'USD', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PREMIUM_ANNUAL', 'Premium Annual', 'ANNUAL', 3999, 'USD', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE users ADD COLUMN plan_code VARCHAR(50) DEFAULT 'FREE';
UPDATE users SET plan_code = 'FREE' WHERE plan_code IS NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_plan
    FOREIGN KEY (plan_code) REFERENCES plans(code);

CREATE INDEX idx_users_plan_code ON users (plan_code);
