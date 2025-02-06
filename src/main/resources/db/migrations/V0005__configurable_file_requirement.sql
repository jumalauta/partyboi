CREATE TYPE optional_boolean AS ENUM ('true', 'false', 'optional');

ALTER TABLE "compo" ADD COLUMN "require_file" optional_boolean DEFAULT 'true';
