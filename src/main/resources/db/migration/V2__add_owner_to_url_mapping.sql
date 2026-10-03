-- Per-link ownership.
--
-- The redirect route (GET /{shortCode}) is public and carries no user, so short codes stay globally
-- unique: ownership decides who may read a link's stats, not who may claim a code. A second user
-- asking for a taken alias still gets the 409 from uk_url_mapping_short_code.
--
-- Nullable on purpose. Rows registered before ownership existed have no subject to record, and a
-- migration must not destroy links to make a column non-null. They keep redirecting, but their stats
-- are unreadable: findByShortCodeAndOwnerSubject cannot match a NULL owner, so they answer the same
-- 404 as a code that never existed. That is the ceiling of this migration -- the way out is to
-- backfill or purge the leftovers and then set the column NOT NULL, at which point the service can
-- stop treating a missing subject as a client's problem.

ALTER TABLE url_mapping
    ADD COLUMN owner VARCHAR(255);

-- Stats reads are scoped by owner, and this is the index that lets one owner's links be selected
-- without scanning the whole table. It also serves the paged "my links" listing.
CREATE INDEX idx_url_mapping_owner ON url_mapping (owner);