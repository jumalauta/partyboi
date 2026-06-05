-- Timing settings that feed the runtime estimate on the compo edit Entries tab.
-- changeover_sec: buffer added between each qualified entry when it runs.
-- default_slot_sec: slot length used for entries with no detectable media duration.
ALTER TABLE compo ADD COLUMN changeover_sec integer NOT NULL DEFAULT 20;
ALTER TABLE compo ADD COLUMN default_slot_sec integer NOT NULL DEFAULT 60;