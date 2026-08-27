-- Deleting a DRAFT submission failed with
-- ai_interaction_log_submission_id_fkey when the draft had any logged AI
-- interaction. The log is per-submission analytics, so it should follow the
-- submission on delete.

ALTER TABLE ai_interaction_log
    DROP CONSTRAINT IF EXISTS ai_interaction_log_submission_id_fkey;

ALTER TABLE ai_interaction_log
    ADD CONSTRAINT ai_interaction_log_submission_id_fkey
        FOREIGN KEY (submission_id) REFERENCES submissions(id) ON DELETE CASCADE;
