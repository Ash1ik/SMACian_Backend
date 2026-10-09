-- V4: notifications table (in-app notification center).
--
-- Fan-out model: one row per recipient (see Notification.kt). IF NOT EXISTS
-- guards keep this safe if ddl-auto already created the table first
-- (Flyway runs before Hibernate, so normally Flyway wins).

CREATE TABLE IF NOT EXISTS notifications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          VARCHAR(500) NOT NULL,
    reference_id  BIGINT,
    is_read       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON notifications (recipient_id, created_at);
