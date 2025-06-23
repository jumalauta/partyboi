CREATE TABLE "password_reset"
(
    "code"       text,
    "user_id"    int                      NOT NULL,
    "expires_at" timestamp with time zone NOT NULL DEFAULT now() + interval '30 min',
    PRIMARY KEY ("code"),
    FOREIGN KEY ("user_id") REFERENCES "appuser" ("id")
        ON DELETE CASCADE
        ON UPDATE CASCADE
);
