-- UC-4.12 Phase 7A: bit-level file fixity and append-only integrity evidence.
-- SHA-256 proves that stored bytes are unchanged. It is intentionally separate from
-- perceptual_hash, which measures visual similarity for duplicate detection.

ALTER TABLE media_assets
    ADD COLUMN content_sha256 CHAR(64),
    ADD COLUMN checksum_generated_at TIMESTAMPTZ,
    ADD COLUMN integrity_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN integrity_checked_at TIMESTAMPTZ,
    ADD COLUMN integrity_failure_reason TEXT;

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_content_sha256
        CHECK (content_sha256 IS NULL OR content_sha256 ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_media_assets_integrity_status
        CHECK (integrity_status IN ('PENDING', 'VERIFIED', 'MISMATCH', 'MISSING', 'ERROR'));

CREATE INDEX ix_media_assets_integrity_status
    ON media_assets (institution_id, integrity_status)
    WHERE deleted_at IS NULL;

CREATE TABLE media_asset_integrity_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    expected_sha256 CHAR(64),
    observed_sha256 CHAR(64),
    status VARCHAR(20) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    failure_reason TEXT,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_integrity_check_expected_sha256
        CHECK (expected_sha256 IS NULL OR expected_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_integrity_check_observed_sha256
        CHECK (observed_sha256 IS NULL OR observed_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_integrity_check_status
        CHECK (status IN ('VERIFIED', 'MISMATCH', 'MISSING', 'ERROR')),
    CONSTRAINT chk_integrity_check_trigger
        CHECK (trigger_type IN ('INGEST', 'MANUAL', 'SCHEDULED', 'REPAIR_VERIFICATION'))
);

CREATE INDEX ix_integrity_checks_asset_checked
    ON media_asset_integrity_checks (asset_id, checked_at DESC);

CREATE INDEX ix_integrity_checks_institution_status
    ON media_asset_integrity_checks (institution_id, status, checked_at DESC);

ALTER TABLE media_asset_integrity_checks ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_asset_integrity_checks_tenant_isolation
    ON media_asset_integrity_checks
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );
