-- Rename legacy institution status values to the new three-state model.
-- onboarding          → inactive  (newly created, no validator invited yet)
-- inactive_no_validator → inactive  (lost all validators, same state as newly created)
-- active              → active    (no change)

UPDATE institutions SET status = 'inactive' WHERE status = 'onboarding';
UPDATE institutions SET status = 'inactive' WHERE status = 'inactive_no_validator';
