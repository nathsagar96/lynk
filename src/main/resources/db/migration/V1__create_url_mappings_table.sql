CREATE TABLE url_mappings (
    id           BIGSERIAL PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    alias        VARCHAR(32)   NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    expires_at   TIMESTAMPTZ,
    click_count  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_url_mappings_alias UNIQUE (alias)
);

CREATE INDEX idx_url_mappings_expires_at ON url_mappings (expires_at) WHERE expires_at IS NOT NULL;
