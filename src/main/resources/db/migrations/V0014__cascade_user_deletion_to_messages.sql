ALTER TABLE message
    DROP CONSTRAINT "message_user_id_fkey",
    ADD CONSTRAINT "message_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES appuser ("id")
        ON DELETE CASCADE
        ON UPDATE CASCADE;
