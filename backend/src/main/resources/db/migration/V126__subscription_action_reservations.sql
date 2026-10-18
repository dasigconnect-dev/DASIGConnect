CREATE TABLE subscription_action_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    institution_id UUID,
    user_id UUID,
    event_type VARCHAR(60) NOT NULL CHECK (
        event_type IN ('SUBMISSION', 'AI_CAPTION', 'AI_MEDIA_SUGGESTION')
    ),
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    source_type VARCHAR(60),
    source_id UUID,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('RESERVED', 'CONSUMED', 'RELEASED', 'EXPIRED')
    ),
    reserved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    released_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscription_action_reservations_active
    ON subscription_action_reservations (organization_id, event_type, reserved_at, expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX idx_subscription_action_reservations_user_daily
    ON subscription_action_reservations (user_id, event_type, reserved_at)
    WHERE status = 'RESERVED' AND user_id IS NOT NULL;

ALTER TABLE subscription_action_reservations ENABLE ROW LEVEL SECURITY;

CREATE POLICY subscription_action_reservations_admin_read ON subscription_action_reservations
    FOR SELECT
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

CREATE POLICY subscription_action_reservations_system_write ON subscription_action_reservations
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') = '')
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') = '');

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE subscription_action_reservations FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE subscription_action_reservations FROM authenticated;
    END IF;
END $$;
