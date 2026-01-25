CREATE TABLE preview
(
    entry_id uuid REFERENCES entry (id) PRIMARY KEY,
    file_id  uuid NOT NULL REFERENCES file (id)
);