ALTER TABLE users ADD COLUMN locale VARCHAR(35);

UPDATE plans
SET price_cents = 3499,
    currency = 'INR',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'PREMIUM_ANNUAL';
