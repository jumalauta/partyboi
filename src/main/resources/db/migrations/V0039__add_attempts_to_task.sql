-- Track how many times a task has been claimed for execution so a task that was interrupted by a
-- worker crash/restart can be retried a bounded number of times instead of being discarded outright.
ALTER TABLE task ADD COLUMN attempts integer NOT NULL DEFAULT 0;
