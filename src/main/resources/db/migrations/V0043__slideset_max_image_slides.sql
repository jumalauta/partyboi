-- Max number of image slides shown per rotation pass of a slide set.
-- NULL = no limit (all image slides are shown on every pass).
ALTER TABLE slideset ADD COLUMN max_image_slides integer;
