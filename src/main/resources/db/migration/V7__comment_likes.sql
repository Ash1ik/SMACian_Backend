-- V7: comment_likes (likes on comments and replies).
--
-- IF NOT EXISTS guards keep this safe if ddl-auto already created the
-- table first (Flyway runs before Hibernate, so normally Flyway wins).

CREATE TABLE IF NOT EXISTS comment_likes (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_id  BIGINT NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT uk_comment_likes_comment_user UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_likes_comment_id
    ON comment_likes (comment_id);
