UPDATE file
SET storage_filename = 'entries/' || storage_filename
WHERE storage_filename NOT LIKE 'entries/%';
