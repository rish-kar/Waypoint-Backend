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
    'ADMIN',
    'Admin',
    'NONE',
    0,
    'INR',
    TRUE,
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
