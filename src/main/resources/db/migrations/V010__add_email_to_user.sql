ALTER TABLE "appuser"
    ADD COLUMN "email" text,
    ADD UNIQUE ("email");

-- Shape of session object has changed -> log out all users
TRUNCATE TABLE session;