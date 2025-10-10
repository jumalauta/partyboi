ALTER TABLE "entry_file"
    DROP CONSTRAINT "entry_file_entry_id_fkey",
    ADD CONSTRAINT "entry_file_entry_id_fkey" FOREIGN KEY ("entry_id") REFERENCES "entry" ("id") ON DELETE CASCADE ON UPDATE CASCADE;
