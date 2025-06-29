CREATE TABLE "task"
(
    "id"          integer GENERATED ALWAYS AS IDENTITY,
    "task"        jsonb                    NOT NULL,
    "created_at"  timestamp with time zone NOT NULL DEFAULT now(),
    "finished_at" timestamp with time zone,
    "state"       text                     NOT NULL,
    PRIMARY KEY ("id")
);
