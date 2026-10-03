-- Initial schema for the lynk URL shortener.
--
-- Flyway is the single source of truth for DDL; Hibernate is configured with
-- ddl-auto=validate and will only verify that the entities match this schema.
--
-- Timestamps are timestamptz and map to java.time.Instant so that expiry
-- comparisons are zone-independent.

CREATE TABLE url_mapping
(
    id           BIGSERIAL PRIMARY KEY,
    original_url TEXT        NOT NULL,
    short_code   VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NULL,
    click_count  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_url_mapping_short_code UNIQUE (short_code)
);

-- The unique constraint already indexes short_code for redirect lookups.
-- expires_at is indexed separately to keep the nightly cleanup sweep from a sequential scan.
CREATE INDEX idx_url_mapping_expires_at ON url_mapping (expires_at);
