-- The preview.entry_id foreign key was created without an ON DELETE rule (V0029), so deleting an
-- entry that has a generated preview failed with a foreign-key violation. (The admin delete path
-- additionally used invalid "DELETE ... CASCADE" SQL, which never cascades.) Match the cascade rule
-- already used by entry_file (V0027) and vote so an entry and its preview are removed together.
ALTER TABLE "preview"
    DROP CONSTRAINT "preview_entry_id_fkey",
    ADD CONSTRAINT "preview_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "entry" ("id") ON DELETE CASCADE;
