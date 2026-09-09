ALTER TABLE users ADD COLUMN byok_provider VARCHAR(40);
ALTER TABLE users ADD COLUMN byok_api_key_ciphertext VARCHAR(4096);
ALTER TABLE users ADD COLUMN byok_model VARCHAR(200);

UPDATE users
SET byok_provider = 'openai',
    byok_api_key_ciphertext = openai_api_key_ciphertext,
    byok_model = openai_model
WHERE openai_api_key_ciphertext IS NOT NULL;
