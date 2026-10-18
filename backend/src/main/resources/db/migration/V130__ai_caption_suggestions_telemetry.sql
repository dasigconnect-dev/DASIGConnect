-- V123: AI Caption Suggestions & Telemetry for RP-03 (REQ-320, REQ-321)
ALTER TABLE ai_interaction_log
    ADD COLUMN IF NOT EXISTS quality_rating SMALLINT CHECK (quality_rating IS NULL OR (quality_rating BETWEEN 1 AND 5)),
    ADD COLUMN IF NOT EXISTS drafting_duration_ms BIGINT;

CREATE TABLE IF NOT EXISTS ai_caption_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    institution_id UUID REFERENCES institutions(id) ON DELETE SET NULL,
    tone VARCHAR(30) NOT NULL DEFAULT 'professional',
    caption TEXT,
    model VARCHAR(60) NOT NULL DEFAULT 'claude-vision',
    status VARCHAR(30) NOT NULL DEFAULT 'generated'
        CHECK (status IN ('generated', 'used', 'used_edited', 'dismissed', 'failed')),
    rating SMALLINT CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),
    drafting_duration_ms BIGINT,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ai_caption_suggestions_submission
    ON ai_caption_suggestions(submission_id, generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_caption_suggestions_status
    ON ai_caption_suggestions(status);
