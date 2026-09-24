ALTER TABLE institutions
    ADD COLUMN IF NOT EXISTS ai_media_hybrid_ranking_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_media_assets_recommendation_candidates
    ON media_assets (institution_id, created_at DESC)
    WHERE deleted_at IS NULL
      AND status = 'READY'
      AND COALESCE(LOWER(temporal_classification), '') <> 'expired';

CREATE INDEX IF NOT EXISTS idx_media_processing_jobs_dead
    ON media_processing_jobs (updated_at DESC)
    WHERE status = 'DEAD';
