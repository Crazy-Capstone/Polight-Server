ALTER TABLE users DROP CONSTRAINT users_provider_check;

ALTER TABLE users ADD CONSTRAINT users_provider_check
    CHECK (provider IN ('KAKAO', 'APPLE'));

ALTER TABLE users ADD COLUMN apple_refresh_token_encrypted TEXT;
