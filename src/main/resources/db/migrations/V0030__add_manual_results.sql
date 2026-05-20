ALTER TABLE compo ADD COLUMN manual_results boolean NOT NULL DEFAULT false;

CREATE TABLE manual_result (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    compo_id uuid NOT NULL REFERENCES compo(id) ON DELETE CASCADE,
    title text NOT NULL,
    author text NOT NULL,
    score_text text NOT NULL DEFAULT '',
    screen_comment text,
    position integer NOT NULL DEFAULT 0
);
