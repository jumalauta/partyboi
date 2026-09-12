-- Give server-side sessions an owner and a lifetime.
--
-- Previously the session table held only (id, value): a stolen cookie was valid forever server-side
-- (the 7-day cookie max-age is only a browser hint), and there was no way to evict a user's sessions
-- on password change, admin demotion, or account deletion. Adding user_id lets those events target a
-- user's sessions, and expires_at lets read-time enforcement reject stale sessions.
--
-- Existing rows predate both columns (no owner, no expiry), so clear them once; this logs everyone
-- out, matching how earlier session-shape changes were handled (see V0012, V0020).
TRUNCATE TABLE session;

ALTER TABLE session
    ADD COLUMN user_id    uuid,
    ADD COLUMN expires_at timestamp with time zone;

-- Fast lookup when invalidating all of one user's sessions, and when purging expired rows.
CREATE INDEX session_user_id_idx ON session (user_id);
CREATE INDEX session_expires_at_idx ON session (expires_at);
