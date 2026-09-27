ALTER TABLE users
    ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN;

UPDATE users
SET onboarding_completed = TRUE
WHERE onboarding_completed IS NULL;

ALTER TABLE users
    ALTER COLUMN onboarding_completed SET DEFAULT FALSE;

ALTER TABLE users
    ALTER COLUMN onboarding_completed SET NOT NULL;
