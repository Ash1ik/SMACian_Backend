-- V6: user_devices (FCM push tokens, one row per device).
--
-- IF NOT EXISTS guards keep this safe if ddl-auto already created the
-- table first (Flyway runs before Hibernate, so normally Flyway wins).

CREATE TABLE IF NOT EXISTS user_devices (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fcm_token  VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_devices_token UNIQUE (fcm_token)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id
    ON user_devices (user_id);
