-- Network-wide scheduling guard-rail on/off switch, surfaced in Page Settings.
-- Guard rails (±30-min spacing, ≤6 posts/day network-wide, ≥2h lead time, peak
-- hours) govern the one shared DASIG publishing calendar, so this is a single
-- global flag, not per-institution: it lives on the no-institution page_settings
-- row (institution_id IS NULL). Default true — rails on until an admin turns
-- them off.
ALTER TABLE page_settings
    ADD COLUMN IF NOT EXISTS guardrails_enforced BOOLEAN NOT NULL DEFAULT true;
