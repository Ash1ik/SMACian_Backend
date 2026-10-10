-- V8: Google sign-in (google_sub) + nullable date_of_birth.
--
-- google_sub is NULL until the first Google sign-in (unique when present;
-- Postgres treats NULLs as distinct, so no backfill conflict). date_of_birth
-- drops NOT NULL because ID tokens carry no DOB (profile update requires it
-- later). IF NOT EXISTS / guarded blocks keep this safe if ddl-auto ran first
-- (Flyway runs before Hibernate, so normally Flyway wins).

ALTER TABLE IF EXISTS users
    ADD COLUMN IF NOT EXISTS google_sub VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_users_google_sub') THEN
        ALTER TABLE users ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);
    END IF;
END
$$;

ALTER TABLE IF EXISTS users
    ALTER COLUMN date_of_birth DROP NOT NULL;
