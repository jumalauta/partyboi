CREATE TABLE "synclog"
(
    "id"          text,
    "description" text                     NOT NULL,
    "start_time"  timestamp with time zone NOT NULL,
    "end_time"    timestamp with time zone,
    "success"     boolean,
    "error"       text,
    PRIMARY KEY ("id")
);
