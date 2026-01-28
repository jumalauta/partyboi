ALTER TABLE "compo"
    ADD COLUMN "scoring_method"      text,
    ADD COLUMN "empty_vote_handling" text,
    ADD COLUMN "min_points"          int,
    ADD COLUMN "max_points"          int;
