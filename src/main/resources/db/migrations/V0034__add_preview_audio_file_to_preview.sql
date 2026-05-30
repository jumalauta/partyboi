ALTER TABLE preview ADD COLUMN preview_audio_file_id uuid REFERENCES file (id);
