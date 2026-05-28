-- Rename legacy institution status values to the new three-state model.
-- onboarding          → inactive  (newly created, no validator invited yet)
-- inactive_no_validator → inactive  (lost all validators, same state as newly created)
-- active              → active    (no change)
--
-- Must drop the V1 check constraint first, update rows, then re-add with
-- the new allowed values so the UPDATE does not violate the old constraint.

ALTER TABLE institutions DROP CONSTRAINT IF EXISTS chk_institutions_status;

UPDATE institutions SET status = 'inactive' WHERE status IN ('onboarding', 'inactive_no_validator');

ALTER TABLE institutions
    ADD CONSTRAINT chk_institutions_status
    CHECK (status IN ('inactive', 'pending', 'active'));
