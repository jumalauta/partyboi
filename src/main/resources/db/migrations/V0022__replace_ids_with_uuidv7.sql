-- Add UUID columns to tables

ALTER TABLE "appuser"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "compo"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "entry"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "error"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "event"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "file"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "message"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "screen"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "task"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE "trigger"
    ADD COLUMN "uuid" uuid NOT NULL UNIQUE DEFAULT uuidv7();

-- Regenerate UUIDs using timestamp when available

UPDATE "entry"
SET uuid = uuidv7(age(timestamp, now()));
UPDATE "error"
SET uuid = uuidv7(age(time, now()));
UPDATE "file"
SET uuid = uuidv7(age(uploaded_at, now()));
UPDATE "task"
SET uuid = uuidv7(age(created_at, now()));

-- Add new foreign key columns to "entry"

ALTER TABLE "entry"
    ADD COLUMN "compo_uuid" uuid,
    ADD COLUMN "user_uuid"  uuid,
    ADD FOREIGN KEY ("compo_uuid") REFERENCES "compo" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE,
    ADD FOREIGN KEY ("user_uuid") REFERENCES "appuser" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "entry"
SET compo_uuid = (SELECT compo.uuid
                  FROM compo
                  WHERE compo.id = entry.compo_id),
    user_uuid  = (SELECT appuser.uuid
                  FROM appuser
                  WHERE appuser.id = entry.user_id);

ALTER TABLE "entry"
    ALTER COLUMN "compo_uuid" SET NOT NULL,
    ALTER COLUMN "user_uuid" SET NOT NULL;

-- Add new foreign key columns to "file"

ALTER TABLE "file"
    ADD COLUMN "entry_uuid" uuid,
    ADD FOREIGN KEY ("entry_uuid") REFERENCES "entry" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "file"
SET entry_uuid = (SELECT entry.uuid
                  FROM entry
                  WHERE entry.id = file.entry_id);

ALTER TABLE "file"
    ALTER COLUMN "entry_uuid" SET NOT NULL;

-- Add new foreign key columns to "message"

ALTER TABLE "message"
    ADD COLUMN "user_uuid" uuid,
    ADD FOREIGN KEY ("user_uuid") REFERENCES "appuser" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "message"
SET user_uuid = (SELECT appuser.uuid
                 FROM appuser
                 WHERE appuser.id = message.user_id);

ALTER TABLE "message"
    ALTER COLUMN "user_uuid" SET NOT NULL;

-- Add new foreign key columns to "password_reset"

ALTER TABLE "password_reset"
    ADD COLUMN "user_uuid" uuid,
    ADD FOREIGN KEY ("user_uuid") REFERENCES "appuser" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "password_reset"
SET user_uuid = (SELECT appuser.uuid
                 FROM appuser
                 WHERE appuser.id = password_reset.user_id);

ALTER TABLE "password_reset"
    ALTER COLUMN "user_uuid" SET NOT NULL;

-- Add new foreign key columns to "vote"

ALTER TABLE "vote"
    ADD COLUMN "user_uuid"  uuid,
    ADD COLUMN "entry_uuid" uuid,
    ADD FOREIGN KEY ("user_uuid") REFERENCES "appuser" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE,
    ADD FOREIGN KEY ("entry_uuid") REFERENCES "entry" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "vote"
SET user_uuid  = (SELECT appuser.uuid
                  FROM appuser
                  WHERE appuser.id = vote.user_id),
    entry_uuid = (SELECT entry.uuid
                  FROM entry
                  WHERE entry.id = vote.entry_id);

ALTER TABLE "vote"
    ALTER COLUMN "user_uuid" SET NOT NULL,
    ALTER COLUMN "entry_uuid" SET NOT NULL;

-- Add new foreign key columns to "votekey"

ALTER TABLE "votekey"
    ADD COLUMN "user_uuid" uuid,
    ADD FOREIGN KEY ("user_uuid") REFERENCES "appuser" ("uuid") ON DELETE CASCADE ON UPDATE CASCADE;

UPDATE "votekey"
SET user_uuid = (SELECT appuser.uuid
                 FROM appuser
                 WHERE appuser.id = votekey.appuser_id);

-- Remove old foreign key columns

ALTER TABLE "vote"
    DROP COLUMN "user_id",
    DROP COLUMN "entry_id";

ALTER TABLE "votekey"
    DROP COLUMN "appuser_id";

ALTER TABLE "message"
    DROP COLUMN "user_id";

ALTER TABLE "password_reset"
    DROP COLUMN "user_id";

ALTER TABLE "entry"
    DROP COLUMN "user_id",
    DROP COLUMN "compo_id";

ALTER TABLE "file"
    DROP COLUMN "entry_id";


-- Remove old id columns

ALTER TABLE "appuser"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "compo"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "entry"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "error"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "event"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "file"
    DROP COLUMN "version",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "message"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "screen"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "task"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

ALTER TABLE "trigger"
    DROP COLUMN "id",
    ADD PRIMARY KEY ("uuid");

-- Rename all uuid columns to id

ALTER TABLE "appuser"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "compo"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "entry"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "entry"
    RENAME COLUMN "compo_uuid" TO "compo_id";
ALTER TABLE "entry"
    RENAME COLUMN "user_uuid" TO "user_id";
ALTER TABLE "error"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "event"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "file"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "file"
    RENAME COLUMN "entry_uuid" TO "entry_id";
ALTER TABLE "message"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "message"
    RENAME COLUMN "user_uuid" TO "user_id";
ALTER TABLE "screen"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "task"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "trigger"
    RENAME COLUMN "uuid" TO "id";
ALTER TABLE "vote"
    RENAME COLUMN "user_uuid" TO "user_id";
ALTER TABLE "vote"
    RENAME COLUMN "entry_uuid" TO "entry_id";
ALTER TABLE "votekey"
    RENAME COLUMN "user_uuid" TO "user_id";

-- Add remaining constraints

ALTER TABLE "vote"
    DROP CONSTRAINT IF EXISTS vote_pkey;

ALTER TABLE "vote"
    ADD CONSTRAINT vote_pkey PRIMARY KEY (user_id, entry_id);

-- Phew!