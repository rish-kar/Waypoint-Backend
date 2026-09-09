UPDATE plans
SET price_cents = 39900,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_MONTHLY';

UPDATE plans
SET price_cents = 349900,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';
