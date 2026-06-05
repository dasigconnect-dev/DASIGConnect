-- UC-4.x consent / visibility gate (foundation). A government page holding photos of students
-- and minors needs a per-asset clearance flag. New uploads default to internal_only; EXISTING
-- assets are grandfathered to cleared_for_public so the live submission/publishing flow is not
-- frozen the moment this lands (the publish/submit hard gate is a separate, later slice).

ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS visibility TEXT;

-- Grandfather every existing asset as cleared (they were already usable before this gate).
UPDATE media_assets SET visibility = 'cleared_for_public' WHERE visibility IS NULL;

-- New rows default to internal_only; backfill is done, so we can tighten to NOT NULL.
ALTER TABLE media_assets ALTER COLUMN visibility SET DEFAULT 'internal_only';
ALTER TABLE media_assets ALTER COLUMN visibility SET NOT NULL;

ALTER TABLE media_assets ADD CONSTRAINT chk_media_assets_visibility
    CHECK (visibility IN ('internal_only', 'cleared_for_public'));
