ALTER TABLE plans RENAME COLUMN price_cents TO price;

UPDATE plans
SET price = 399,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_MONTHLY';

UPDATE plans
SET price = 3500,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';

UPDATE plans
SET price = 0,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code IN ('FREE', 'PREMIUM_SPECIAL', 'ADMIN');
