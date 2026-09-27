ALTER TABLE users ADD COLUMN onboarding_completed BOOLEAN;

UPDATE users
SET onboarding_completed = TRUE;

ALTER TABLE users ALTER COLUMN onboarding_completed SET NOT NULL;
ALTER TABLE users ALTER COLUMN onboarding_completed SET DEFAULT FALSE;
