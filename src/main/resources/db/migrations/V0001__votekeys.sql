CREATE TABLE votekey (
    key text PRIMARY KEY,
    appuser_id integer REFERENCES appuser(id) ON DELETE CASCADE
);