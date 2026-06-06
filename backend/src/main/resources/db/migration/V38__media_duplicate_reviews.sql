-- UC-4.12 Phase 7F: append-only record of human duplicate adjudications.
-- A decision NEVER deletes an asset — physical deletion stays in the retention workflow.
-- Reversal is a new row (latest decision per candidate wins); rows are never updated.

CREATE TABLE media_duplicate_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    candidate_asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    compared_asset_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    canonical_asset_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    decision VARCHAR(20) NOT NULL,
    merged_tags BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_dup_decision
        CHECK (decision IN ('duplicate', 'keep_both', 'not_duplicate'))
);

CREATE INDEX ix_media_dup_reviews_candidate
    ON media_duplicate_reviews (candidate_asset_id, reviewed_at DESC);
CREATE INDEX ix_media_dup_reviews_institution
    ON media_duplicate_reviews (institution_id, reviewed_at DESC);

ALTER TABLE media_duplicate_reviews ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_duplicate_reviews_tenant_isolation
    ON media_duplicate_reviews
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );
