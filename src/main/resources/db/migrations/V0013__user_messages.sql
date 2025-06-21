CREATE TABLE message
(
    "id"      integer GENERATED ALWAYS AS IDENTITY,
    "user_id" integer NOT NULL,
    "type"    text    NOT NULL,
    "text"    text    NOT NULL,
    PRIMARY KEY ("id"),
    FOREIGN KEY ("user_id") REFERENCES "appuser" ("id")
);
