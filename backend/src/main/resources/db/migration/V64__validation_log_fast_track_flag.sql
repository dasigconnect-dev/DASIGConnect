-- UC-2.4 A6/Live Event Fast-Track: distinctly flag approvals that published
-- immediately (Fast-Track) in the immutable validation action log, mirroring
-- the existing is_self_review flag.
ALTER TABLE validation_logs
    ADD COLUMN is_fast_track BOOLEAN NOT NULL DEFAULT FALSE;
