CREATE TABLE "entry_file"
(
    "file_id"  uuid NOT NULL,
    "entry_id" uuid NOT NULL,
    PRIMARY KEY ("file_id"),
    FOREIGN KEY ("file_id") REFERENCES "file" ("id") ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY ("entry_id") REFERENCES "entry" ("id")
);

INSERT INTO entry_file
SELECT id AS file_id,
       entry_id
FROM file;

ALTER TABLE "file"
    DROP COLUMN "entry_id";
