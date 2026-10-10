-- V9: users.role (USER/ADMIN for platform management).
--
-- IF NOT EXISTS guards keep this safe if ddl-auto already created the
-- column first (Flyway runs before Hibernate, so normally Flyway wins).

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS role VARCHAR(10) NOT NULL DEFAULT 'USER';
