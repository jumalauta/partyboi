-- V0022 renamed the temporary "user_uuid" columns back to "user_id" on every table except
-- "password_reset", so inserting a reset code (which targets "user_id") has failed ever since.
ALTER TABLE "password_reset"
    RENAME COLUMN "user_uuid" TO "user_id";
