-- Whether the entry was made remotely and whether it contains AI generated content.
ALTER TABLE entry ADD COLUMN remote boolean NOT NULL DEFAULT false;
ALTER TABLE entry ADD COLUMN ai_generated boolean NOT NULL DEFAULT false;
