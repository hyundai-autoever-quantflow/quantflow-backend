ALTER TABLE users
    DROP CONSTRAINT users_email_key,
    DROP CONSTRAINT users_name_key,
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN social_provider TYPE VARCHAR(20),
    ALTER COLUMN social_provider SET NOT NULL,
    ALTER COLUMN provider_id SET NOT NULL,
    ADD CONSTRAINT uk_users_social_provider_id UNIQUE (social_provider, provider_id);
