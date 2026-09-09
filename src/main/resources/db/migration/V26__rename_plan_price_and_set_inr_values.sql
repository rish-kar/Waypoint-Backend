UPDATE plans
SET price_cents = 399,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_MONTHLY';

UPDATE plans
SET price_cents = 3500,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';

UPDATE plans
SET price_cents = 0,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code IN ('FREE', 'PREMIUM_SPECIAL', 'ADMIN');
