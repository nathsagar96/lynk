ALTER TABLE url_mappings ADD COLUMN owner_id VARCHAR(255);

CREATE INDEX idx_url_mappings_owner_id ON url_mappings (owner_id) WHERE owner_id IS NOT NULL;
