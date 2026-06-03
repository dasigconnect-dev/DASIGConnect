-- UC-4.6 (Phase 3F): extend ai_interaction_log to record feedback on AI surfaces that are
-- NOT tied to a submission — notably media search (UC-4.5). Search feedback has a target asset
-- and a result rank but no submission, so submission_id must become nullable and new columns
-- capture the asset, rank, rating, and query context.

ALTER TABLE ai_interaction_log ALTER COLUMN submission_id DROP NOT NULL;

ALTER TABLE ai_interaction_log
    ADD COLUMN IF NOT EXISTS target_asset_id UUID REFERENCES media_assets(id) ON DELETE SET NULL;
ALTER TABLE ai_interaction_log ADD COLUMN IF NOT EXISTS result_rank INT;
ALTER TABLE ai_interaction_log ADD COLUMN IF NOT EXISTS rating SMALLINT;   -- +1 thumbs up, -1 thumbs down
ALTER TABLE ai_interaction_log ADD COLUMN IF NOT EXISTS query_text TEXT;   -- search query context (no submission)

-- Widen the interaction_type whitelist to include search (and future advisor) feedback.
ALTER TABLE ai_interaction_log DROP CONSTRAINT IF EXISTS ai_interaction_log_interaction_type_check;
ALTER TABLE ai_interaction_log ADD CONSTRAINT ai_interaction_log_interaction_type_check
    CHECK (interaction_type IN (
        'caption_suggestion',
        'tag_classification',
        'media_recommendation',
        'media_search',
        'advisor'
    ));

CREATE INDEX IF NOT EXISTS idx_ai_interaction_target_asset ON ai_interaction_log(target_asset_id);
