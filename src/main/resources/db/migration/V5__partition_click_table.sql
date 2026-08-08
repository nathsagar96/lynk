-- Convert click table to native PostgreSQL partitioning by clicked_at (monthly)
-- This prevents unbounded table growth and enables efficient archival/retention

-- Step 1: Create new partitioned table
CREATE TABLE click_partitioned (
    id         BIGINT                   GENERATED ALWAYS AS IDENTITY,
    shortcode  VARCHAR(11)              NOT NULL,
    ip_address VARCHAR(20),
    user_agent TEXT,
    referer    TEXT,
    clicked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (clicked_at);

-- Step 2: Create indexes on the partitioned parent (propagates to all partitions)
CREATE INDEX idx_click_partitioned_shortcode ON click_partitioned (shortcode);
CREATE INDEX idx_click_partitioned_shortcode_clicked_at ON click_partitioned (shortcode, clicked_at);

-- Step 3: Create initial partitions (current month + 3 months ahead)
-- Current month (August 2026)
CREATE TABLE click_2026_08 PARTITION OF click_partitioned
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');

-- September 2026
CREATE TABLE click_2026_09 PARTITION OF click_partitioned
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');

-- October 2026
CREATE TABLE click_2026_10 PARTITION OF click_partitioned
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');

-- November 2026
CREATE TABLE click_2026_11 PARTITION OF click_partitioned
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');

-- Step 4: Create default partition for any data outside defined ranges
CREATE TABLE click_default PARTITION OF click_partitioned DEFAULT;

-- Step 5: Migrate existing data (use OVERRIDING SYSTEM VALUE for identity column)
INSERT INTO click_partitioned (id, shortcode, ip_address, user_agent, referer, clicked_at)
OVERRIDING SYSTEM VALUE
SELECT id, shortcode, ip_address, user_agent, referer, clicked_at
FROM click;

-- Step 6: Drop old table and rename new one
DROP TABLE click;
ALTER TABLE click_partitioned RENAME TO click;

-- Step 7: Rename indexes to match original naming convention
ALTER INDEX idx_click_partitioned_shortcode RENAME TO idx_click_shortcode;
ALTER INDEX idx_click_partitioned_shortcode_clicked_at RENAME TO idx_click_shortcode_clicked_at;
