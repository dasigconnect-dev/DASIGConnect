-- Freezes the reviewable display fields of a submission at submit()/resubmit()
-- time, so the Review Queue can keep showing what was actually submitted for
-- review while it sits in needs_revision and the contributor edits/autosaves
-- (which mutate the live submission row directly) without those in-progress
-- edits leaking into the moderator's view before an actual resubmission.
ALTER TABLE submissions ADD COLUMN review_snapshot jsonb;
