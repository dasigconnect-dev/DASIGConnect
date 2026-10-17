CREATE TABLE storage_quota_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    institution_id UUID NOT NULL,
    user_id UUID NOT NULL,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    reserved_bytes BIGINT NOT NULL CHECK (reserved_bytes > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'EXPIRING', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    media_asset_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ
);

CREATE INDEX idx_storage_quota_reservations_active
    ON storage_quota_reservations (organization_id, expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX idx_storage_quota_reservations_institution
    ON storage_quota_reservations (institution_id, created_at DESC);

ALTER TABLE storage_quota_reservations ENABLE ROW LEVEL SECURITY;

CREATE POLICY storage_quota_reservations_admin_read ON storage_quota_reservations
    FOR SELECT
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

CREATE POLICY storage_quota_reservations_system_write ON storage_quota_reservations
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') = '')
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') = '');

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE storage_quota_reservations FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE storage_quota_reservations FROM authenticated;
    END IF;
END $$;
