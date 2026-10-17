CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_organizations_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

INSERT INTO organizations (id, name, slug, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'DASIG Central Visayas',
    'dasig-central-visayas',
    'ACTIVE'
);

ALTER TABLE institutions
    ADD COLUMN organization_id UUID;

UPDATE institutions
SET organization_id = '00000000-0000-0000-0000-000000000001'
WHERE organization_id IS NULL;

ALTER TABLE institutions
    ALTER COLUMN organization_id SET DEFAULT '00000000-0000-0000-0000-000000000001',
    ALTER COLUMN organization_id SET NOT NULL,
    ADD CONSTRAINT fk_institutions_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id);

CREATE INDEX idx_institutions_organization_id
    ON institutions (organization_id);

CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    monthly_price_centavos BIGINT NOT NULL,
    institution_limit INTEGER NOT NULL,
    active_user_limit INTEGER NOT NULL,
    media_storage_bytes BIGINT NOT NULL,
    monthly_submission_limit INTEGER NOT NULL,
    monthly_ai_caption_limit INTEGER NOT NULL,
    monthly_ai_media_suggestion_limit INTEGER NOT NULL,
    per_user_daily_caption_limit INTEGER NOT NULL,
    analytics_retention_days INTEGER,
    export_level VARCHAR(30) NOT NULL,
    support_level VARCHAR(30) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_plans_code_version UNIQUE (code, version),
    CONSTRAINT chk_subscription_plans_version CHECK (version > 0),
    CONSTRAINT chk_subscription_plans_price CHECK (monthly_price_centavos >= 0),
    CONSTRAINT chk_subscription_plans_limits CHECK (
        institution_limit >= 0
        AND active_user_limit >= 0
        AND media_storage_bytes >= 0
        AND monthly_submission_limit >= 0
        AND monthly_ai_caption_limit >= 0
        AND monthly_ai_media_suggestion_limit >= 0
        AND per_user_daily_caption_limit >= 0
        AND (analytics_retention_days IS NULL OR analytics_retention_days >= 0)
    ),
    CONSTRAINT chk_subscription_plans_export_level CHECK (
        export_level IN ('NONE', 'CSV', 'CSV_AND_PDF')
    ),
    CONSTRAINT chk_subscription_plans_support_level CHECK (
        support_level IN ('BEST_EFFORT', 'STANDARD_EMAIL', 'PRIORITY_EMAIL')
    ),
    CONSTRAINT chk_subscription_plans_effective_range CHECK (
        effective_until IS NULL OR effective_until > effective_from
    )
);

INSERT INTO subscription_plans (
    id, code, name, version, monthly_price_centavos,
    institution_limit, active_user_limit, media_storage_bytes,
    monthly_submission_limit, monthly_ai_caption_limit,
    monthly_ai_media_suggestion_limit, per_user_daily_caption_limit,
    analytics_retention_days, export_level, support_level, effective_from
)
VALUES
    (
        '00000000-0000-0000-0000-000000000101',
        'FREE_PILOT', 'Free Pilot', 1, 0,
        3, 15, 10000000000,
        50, 100, 300, 5,
        30, 'NONE', 'BEST_EFFORT', NOW()
    ),
    (
        '00000000-0000-0000-0000-000000000102',
        'PLUS_NETWORK', 'Plus Network', 1, 749900,
        15, 75, 75000000000,
        500, 1500, 5000, 20,
        365, 'CSV', 'STANDARD_EMAIL', NOW()
    ),
    (
        '00000000-0000-0000-0000-000000000103',
        'PREMIUM_NETWORK', 'Premium Network', 1, 1099900,
        25, 150, 200000000000,
        2000, 5000, 20000, 50,
        NULL, 'CSV_AND_PDF', 'PRIORITY_EMAIL', NOW()
    );

CREATE INDEX idx_subscription_plans_active_price
    ON subscription_plans (active, monthly_price_centavos, version DESC);

CREATE TABLE organization_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL UNIQUE REFERENCES organizations(id),
    subscription_plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    plan_code_snapshot VARCHAR(50) NOT NULL,
    plan_version_snapshot INTEGER NOT NULL,
    entitlements_snapshot JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    grace_period_ends_at TIMESTAMPTZ,
    external_provider VARCHAR(50),
    external_customer_id VARCHAR(255),
    external_subscription_id VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_organization_subscriptions_status CHECK (
        status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'GRACE_PERIOD', 'RESTRICTED', 'CANCELLED')
    ),
    CONSTRAINT chk_organization_subscriptions_period CHECK (
        current_period_end > current_period_start
    )
);

INSERT INTO organization_subscriptions (
    id, organization_id, subscription_plan_id,
    plan_code_snapshot, plan_version_snapshot, entitlements_snapshot,
    status, current_period_start, current_period_end
)
VALUES (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000101',
    'FREE_PILOT',
    1,
    '{"institutionLimit":3,"activeUserLimit":15,"mediaStorageBytes":10000000000,"monthlySubmissionLimit":50,"monthlyAiCaptionLimit":100,"monthlyAiMediaSuggestionLimit":300,"perUserDailyCaptionLimit":5,"analyticsRetentionDays":30,"exportLevel":"NONE","supportLevel":"BEST_EFFORT"}'::jsonb,
    'TRIAL',
    NOW(),
    NOW() + INTERVAL '2 months'
);

ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY organizations_admin_access ON organizations
    FOR ALL
    USING (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    )
    WITH CHECK (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    );

CREATE POLICY subscription_plans_admin_access ON subscription_plans
    FOR ALL
    USING (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    )
    WITH CHECK (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    );

CREATE POLICY organization_subscriptions_admin_access ON organization_subscriptions
    FOR ALL
    USING (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    )
    WITH CHECK (
        COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin')
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE organizations FROM anon;
        REVOKE ALL ON TABLE subscription_plans FROM anon;
        REVOKE ALL ON TABLE organization_subscriptions FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE organizations FROM authenticated;
        REVOKE ALL ON TABLE subscription_plans FROM authenticated;
        REVOKE ALL ON TABLE organization_subscriptions FROM authenticated;
    END IF;
END $$;
