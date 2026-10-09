-- V5: notifications.actor_id (who triggered it - liker/commenter/sharer).
--
-- Nullable on purpose: pre-existing rows have no actor (the acting user is
-- indeterminable from stored data) and read back as "Someone". Actor user
-- deletion SET NULLs instead of deleting the notification. ddl-auto would
-- add the bare column; Flyway owns the FK + constraint explicitly.

ALTER TABLE IF EXISTS notifications
    ADD COLUMN IF NOT EXISTS actor_id BIGINT REFERENCES users (id) ON DELETE SET NULL;
