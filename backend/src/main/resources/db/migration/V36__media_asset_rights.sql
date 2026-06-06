-- UC-4.12 Phase 7D: structured rights and consent record, one current row per asset.
-- The existing media_assets.visibility column stays the fast publishing gate; this record
-- explains WHO owns an asset, WHY it may be used, WHERE it may run, and WHEN permission ends.
-- A later enforcement step (this phase) requires a complete, non-expired record before a NEW
-- asset may transition to cleared_for_public; already-cleared assets are grandfathered.

CREATE TABLE media_asset_rights (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    asset_id UUID NOT NULL UNIQUE REFERENCES media_assets(id) ON DELETE CASCADE,
    rights_holder TEXT,
    rights_basis VARCHAR(20) NOT NULL DEFAULT 'unknown',
    license_uri TEXT,
    consent_reference TEXT,
    clearance_date DATE,
    expires_at DATE,
    permitted_channels TEXT[],
    restrictions TEXT,
    supporting_document_url TEXT,
    verified_by UUID REFERENCES users(id),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_asset_rights_basis
        CHECK (rights_basis IN ('owned', 'consent', 'licensed', 'public_domain', 'unknown'))
);

CREATE INDEX ix_media_asset_rights_institution ON media_asset_rights (institution_id);

-- Drives the Repository Health "expiring rights" view without scanning every row.
CREATE INDEX ix_media_asset_rights_expires
    ON media_asset_rights (institution_id, expires_at)
    WHERE expires_at IS NOT NULL;

ALTER TABLE media_asset_rights ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_asset_rights_tenant_isolation
    ON media_asset_rights
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );
