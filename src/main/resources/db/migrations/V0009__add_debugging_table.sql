CREATE TABLE error
(
    "id"      integer GENERATED ALWAYS AS IDENTITY,
    "key"     text      NOT NULL,
    "message" text      NOT NULL,
    "trace"   text,
    "context" jsonb,
    "time"    timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY ("id")
);
