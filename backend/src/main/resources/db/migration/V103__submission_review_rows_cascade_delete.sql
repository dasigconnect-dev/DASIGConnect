-- Deleting a REJECTED submission (now allowed alongside DRAFT) failed with
-- validation_logs_submission_id_fkey: every reviewed submission has at least
-- a lock_acquired + rejected row. A draft that was withdrawn after a reviewer
-- opened it hit the same error. review_locks and override_requests had the
-- same NO ACTION reference.
--
-- These rows are per-submission review state, so they follow the submission
-- on delete (same approach as V75 for ai_interaction_log). The permanent
-- record of each review decision — reviewer, remarks, rejection reason — is
-- kept separately in the append-only audit_log, which has no FK to
-- submissions and is untouched.
--
-- publication_attempts deliberately keeps NO ACTION: a submission that was
-- ever published must never be deletable, and that FK is the backstop.

ALTER TABLE validation_logs
    DROP CONSTRAINT IF EXISTS validation_logs_submission_id_fkey;
ALTER TABLE validation_logs
    ADD CONSTRAINT validation_logs_submission_id_fkey
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE;

ALTER TABLE review_locks
    DROP CONSTRAINT IF EXISTS review_locks_submission_id_fkey;
ALTER TABLE review_locks
    ADD CONSTRAINT review_locks_submission_id_fkey
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE;

ALTER TABLE override_requests
    DROP CONSTRAINT IF EXISTS override_requests_submission_id_fkey;
ALTER TABLE override_requests
    ADD CONSTRAINT override_requests_submission_id_fkey
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE;
