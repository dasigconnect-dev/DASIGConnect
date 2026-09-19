-- Moves the onboarding-guide "seen" tracking from per-browser localStorage to
-- a per-account column, so a guide dismissed once on an account never comes
-- back on that account regardless of browser, device, or a cleared/private
-- session — matching the "first login ever" intent, not "first time this
-- browser has local storage for it."
ALTER TABLE users ADD COLUMN tour_seen_screens jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE users ADD COLUMN tours_enabled boolean NOT NULL DEFAULT true;
