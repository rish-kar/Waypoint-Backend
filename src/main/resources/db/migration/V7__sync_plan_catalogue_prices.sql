UPDATE plans
SET price_cents = 0,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code IN ('FREE', 'PREMIUM_SPECIAL');

UPDATE plans
SET price_cents = 39900,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_MONTHLY';

UPDATE plans
SET price_cents = 350000,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';
