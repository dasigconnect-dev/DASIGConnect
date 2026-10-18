CREATE TABLE subscription_usage_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    institution_id UUID,
    user_id UUID,
    event_type VARCHAR(60) NOT NULL,
    quantity BIGINT NOT NULL,
    unit VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    source_type VARCHAR(60),
    source_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_usage_unit CHECK (unit IN ('COUNT', 'BYTES'))
);

CREATE INDEX idx_subscription_usage_org_type_occurred
    ON subscription_usage_events (organization_id, event_type, occurred_at DESC);

CREATE INDEX idx_subscription_usage_institution_occurred
    ON subscription_usage_events (institution_id, occurred_at DESC)
    WHERE institution_id IS NOT NULL;

CREATE INDEX idx_subscription_usage_source
    ON subscription_usage_events (source_type, source_id)
    WHERE source_id IS NOT NULL;

INSERT INTO subscription_usage_events (
    organization_id, event_type, quantity, unit, idempotency_key,
    source_type, occurred_at
)
SELECT organization.id,
       'INSTITUTION',
       COUNT(institution.id),
       'COUNT',
       'baseline:institutions:' || organization.id,
       'MIGRATION_BASELINE',
       NOW()
FROM organizations organization
LEFT JOIN institutions institution ON institution.organization_id = organization.id
GROUP BY organization.id;

INSERT INTO subscription_usage_events (
    organization_id, event_type, quantity, unit, idempotency_key,
    source_type, occurred_at
)
SELECT organization.id,
       'ACTIVE_USER_SEAT',
       COUNT(user_account.id),
       'COUNT',
       'baseline:active-users:' || organization.id,
       'MIGRATION_BASELINE',
       NOW()
FROM organizations organization
LEFT JOIN users user_account
    ON user_account.account_state = 'active'
   AND (
       user_account.institution_id IN (
           SELECT institution.id
           FROM institutions institution
           WHERE institution.organization_id = organization.id
       )
       OR (
           user_account.institution_id IS NULL
           AND organization.id = '00000000-0000-0000-0000-000000000001'
       )
   )
GROUP BY organization.id;

INSERT INTO subscription_usage_events (
    organization_id, event_type, quantity, unit, idempotency_key,
    source_type, occurred_at
)
SELECT organization.id,
       'MEDIA_STORAGE_BYTES',
       COALESCE(SUM(media.file_size_bytes), 0),
       'BYTES',
       'baseline:media-storage:' || organization.id,
       'MIGRATION_BASELINE',
       NOW()
FROM organizations organization
LEFT JOIN institutions institution ON institution.organization_id = organization.id
LEFT JOIN media_assets media
    ON media.institution_id = institution.id
   AND media.deleted_at IS NULL
   AND media.status <> 'DELETED'
GROUP BY organization.id;

CREATE OR REPLACE FUNCTION reject_subscription_usage_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'subscription_usage_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER subscription_usage_events_append_only
    BEFORE UPDATE OR DELETE ON subscription_usage_events
    FOR EACH ROW EXECUTE FUNCTION reject_subscription_usage_event_mutation();

ALTER TABLE subscription_usage_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY subscription_usage_events_admin_read ON subscription_usage_events
    FOR SELECT
    USING (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    );

CREATE POLICY subscription_usage_events_system_insert ON subscription_usage_events
    FOR INSERT
    WITH CHECK (true);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE subscription_usage_events FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE subscription_usage_events FROM authenticated;
    END IF;
END $$;
