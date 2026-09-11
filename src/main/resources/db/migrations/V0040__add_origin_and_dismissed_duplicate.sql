-- Provenance for sync reconciliation: which instance a row was created on.
-- Nullable; rows from before this migration have no origin.
ALTER TABLE entry ADD COLUMN origin text;
ALTER TABLE appuser ADD COLUMN origin text;

-- Duplicate candidate pairs an admin has marked as "not a duplicate" so they
-- do not reappear after every sync. Pairs are stored with id_a < id_b.
-- Deliberately not a synced table: reconciliation happens on the instance
-- where the merge is performed.
CREATE TABLE dismissed_duplicate
(
    kind text NOT NULL,
    id_a uuid NOT NULL,
    id_b uuid NOT NULL,
    PRIMARY KEY (kind, id_a, id_b)
);
