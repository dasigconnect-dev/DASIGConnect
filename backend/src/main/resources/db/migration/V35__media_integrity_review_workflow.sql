-- UC-4.12 Phase 7B: operational review state for integrity failures.
-- Check history remains append-only in media_asset_integrity_checks.

ALTER TABLE media_assets
    ADD COLUMN integrity_review_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN integrity_review_acknowledged_at TIMESTAMPTZ,
    ADD COLUMN integrity_review_acknowledged_by UUID REFERENCES users(id);

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_integrity_review_status
        CHECK (integrity_review_status IN ('NONE', 'OPEN', 'ACKNOWLEDGED', 'RESOLVED'));

CREATE INDEX ix_media_assets_integrity_review
    ON media_assets (institution_id, integrity_review_status, integrity_checked_at)
    WHERE deleted_at IS NULL
      AND integrity_review_status IN ('OPEN', 'ACKNOWLEDGED');
