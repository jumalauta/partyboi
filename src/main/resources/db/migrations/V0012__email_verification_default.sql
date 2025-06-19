ALTER TABLE appuser
    ALTER COLUMN "email_verified" SET DEFAULT 'false';

-- Shape of session object has changed -> log out all users
TRUNCATE TABLE session;