ALTER TABLE "appuser"
    ADD COLUMN "verification_code" text,
    ADD COLUMN "email_verified"    boolean;
