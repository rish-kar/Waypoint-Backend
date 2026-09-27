UPDATE plans
SET price_cents = 499,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_MONTHLY';

UPDATE plans
SET price_cents = 3999,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';

UPDATE plans
SET price_cents = 0,
    currency = 'USD',
    updated_at = CURRENT_TIMESTAMP
WHERE code IN ('FREE', 'PREMIUM_SPECIAL', 'ADMIN');
