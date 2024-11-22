CREATE TABLE IF NOT EXISTS appuser (
    id SERIAL PRIMARY KEY,
    name text NOT NULL UNIQUE,
    password text NOT NULL,
    is_admin boolean DEFAULT false
);

CREATE TABLE IF NOT EXISTS session (
    id text NOT NULL,
    value text NOT NULL
);

CREATE TABLE IF NOT EXISTS compo (
    id SERIAL PRIMARY KEY,
    name text NOT NULL,
    rules text NOT NULL DEFAULT '',
    visible boolean NOT NULL DEFAULT true,
    allow_submit boolean NOT NULL DEFAULT true,
    allow_vote boolean NOT NULL DEFAULT false,
    public_results boolean NOT NULL DEFAULT false,
    formats text[]
);

CREATE TABLE IF NOT EXISTS entry (
    id SERIAL PRIMARY KEY,
    title text NOT NULL,
    author text NOT NULL,
    screen_comment text,
    org_comment text,
    compo_id integer REFERENCES compo(id),
    user_id integer REFERENCES appuser(id),
    qualified boolean NOT NULL DEFAULT true,
    run_order integer NOT NULL DEFAULT 0,
    timestamp timestamp with time zone DEFAULT now()
);

CREATE TABLE IF NOT EXISTS file (
    entry_id integer REFERENCES entry(id) ON DELETE CASCADE,
    version integer,
    orig_filename text NOT NULL,
    storage_filename text NOT NULL,
    type text NOT NULL,
    size numeric NOT NULL,
    uploaded_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT file_pkey PRIMARY KEY (entry_id, version)
);

CREATE TABLE IF NOT EXISTS vote (
    user_id integer REFERENCES appuser(id),
    entry_id integer REFERENCES entry(id),
    points integer NOT NULL,
    CONSTRAINT vote_pkey PRIMARY KEY (user_id, entry_id)
);

CREATE TABLE IF NOT EXISTS slideset (
    id text PRIMARY KEY,
    name text NOT NULL,
    icon text NOT NULL DEFAULT 'tv'::text
);

CREATE TABLE IF NOT EXISTS screen (
    id SERIAL PRIMARY KEY,
    slideset_id text NOT NULL REFERENCES slideset(id) ON DELETE CASCADE,
    type text NOT NULL,
    content jsonb NOT NULL,
    visible boolean NOT NULL DEFAULT true,
    run_order integer NOT NULL DEFAULT 0,
    show_on_info boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS property (
    key text PRIMARY KEY,
    value jsonb NOT NULL
);

CREATE TABLE IF NOT EXISTS event (
    id SERIAL PRIMARY KEY,
    name text NOT NULL,
    time timestamp with time zone NOT NULL,
    visible boolean NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS trigger (
    id SERIAL PRIMARY KEY,
    type text NOT NULL,
    action jsonb NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    signal text NOT NULL,
    description text NOT NULL,
    executed_at timestamp with time zone,
    error text
);
