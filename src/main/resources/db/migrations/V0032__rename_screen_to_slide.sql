ALTER TABLE screen
    RENAME TO slide;

UPDATE slide
SET type = replace(type, 'party.jml.partyboi.screen.slides', 'party.jml.partyboi.infoscreen.slides');
