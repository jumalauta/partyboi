-- Collapse the per-date generated schedule slides into one dynamic "Schedule"
-- slide per slide set: keep the first row in run order, make it dateless, drop
-- the rest. The slide now decides at display time which party days to show.
WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY slideset_id ORDER BY run_order, id) AS rn
    FROM slide
    WHERE type = 'party.jml.partyboi.infoscreen.slides.ScheduleSlide'
)
DELETE FROM slide
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

UPDATE slide
SET content = '{}'::jsonb
WHERE type = 'party.jml.partyboi.infoscreen.slides.ScheduleSlide';
