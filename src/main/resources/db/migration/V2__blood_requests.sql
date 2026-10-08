-- V2: blood_requests + blood_request_images (new feature tables).
--
-- IF NOT EXISTS guards: Flyway runs BEFORE Hibernate, so on a fresh DB this
-- creates the tables; ddl-auto=update then finds them present. On databases
-- where update already created them, the guards make this a safe no-op.
-- (baseline-on-migrate=true absorbs pre-Flyway databases at V1.)

CREATE TABLE IF NOT EXISTS blood_requests (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blood_group      VARCHAR(3) NOT NULL,
    bags             INTEGER NOT NULL,
    urgency          VARCHAR(10) NOT NULL,
    hospital         VARCHAR(255) NOT NULL,
    location         VARCHAR(255) NOT NULL,
    needed_by        DATE NOT NULL,
    contact_number   VARCHAR(20) NOT NULL,
    note             VARCHAR(2000),
    status           VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_blood_requests_requester_id
    ON blood_requests (requester_id);
CREATE INDEX IF NOT EXISTS idx_blood_requests_group_status
    ON blood_requests (blood_group, status);
CREATE INDEX IF NOT EXISTS idx_blood_requests_status_created
    ON blood_requests (status, created_at);

CREATE TABLE IF NOT EXISTS blood_request_images (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id   BIGINT NOT NULL REFERENCES blood_requests (id) ON DELETE CASCADE,
    image_data   BYTEA NOT NULL,
    content_type VARCHAR(100),
    width        INTEGER,
    height       INTEGER,
    sort_order   INTEGER NOT NULL,
    created_at   TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_blood_request_images_request_id
    ON blood_request_images (request_id);
