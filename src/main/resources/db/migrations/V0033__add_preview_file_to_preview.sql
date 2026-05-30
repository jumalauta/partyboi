ALTER TABLE preview ADD COLUMN preview_file_id uuid REFERENCES file (id);
