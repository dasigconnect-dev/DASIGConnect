-- V130: AI caption suggestion telemetry for RP-03 (REQ-320, REQ-321).
-- Adds the one-click 1-5 quality rating and the caption drafting duration
-- (opening Post Details -> decision) to each caption decision logged in
-- ai_interaction_log. Background generations are logged there as
-- action_taken = 'auto_generated'; on-request ones stay 're_generate'.
ALTER TABLE ai_interaction_log
    ADD COLUMN IF NOT EXISTS quality_rating SMALLINT CHECK (quality_rating IS NULL OR (quality_rating BETWEEN 1 AND 5)),
    ADD COLUMN IF NOT EXISTS drafting_duration_ms BIGINT;
