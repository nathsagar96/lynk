-- Add version column for optimistic locking on UrlEntity
ALTER TABLE url ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
