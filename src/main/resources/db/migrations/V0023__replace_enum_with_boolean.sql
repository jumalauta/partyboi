-- SymmetricDS does not like this enum.

ALTER TABLE "compo"
    ALTER COLUMN "require_file" DROP DEFAULT;

ALTER TABLE "compo"
    ALTER COLUMN "require_file" TYPE boolean USING require_file::text::boolean;

ALTER TABLE "compo"
    ALTER COLUMN "require_file" SET DEFAULT TRUE;

DROP TYPE optional_boolean;