CREATE TABLE subscription_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_subscription_id UUID NOT NULL REFERENCES organization_subscriptions(id),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    amount_centavos BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'PHP',
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    billing_period_start TIMESTAMPTZ NOT NULL,
    billing_period_end TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_invoice_amount CHECK (amount_centavos > 0),
    CONSTRAINT chk_subscription_invoice_currency CHECK (currency = 'PHP'),
    CONSTRAINT chk_subscription_invoice_status CHECK (
        status IN ('OPEN', 'PAID', 'VOID', 'PARTIALLY_REFUNDED', 'REFUNDED')
    ),
    CONSTRAINT chk_subscription_invoice_period CHECK (billing_period_end > billing_period_start)
);

CREATE INDEX idx_subscription_invoices_subscription_created
    ON subscription_invoices (organization_subscription_id, created_at DESC);
CREATE UNIQUE INDEX uq_subscription_invoices_one_open
    ON subscription_invoices (organization_subscription_id)
    WHERE status = 'OPEN';

CREATE TABLE subscription_payment_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES subscription_invoices(id),
    provider VARCHAR(30) NOT NULL,
    external_link_id VARCHAR(255) NOT NULL UNIQUE,
    provider_reference_number VARCHAR(255),
    checkout_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    live_mode BOOLEAN NOT NULL DEFAULT FALSE,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_payment_link_provider CHECK (provider = 'PAYMONGO'),
    CONSTRAINT chk_subscription_payment_link_status CHECK (status IN ('ACTIVE', 'PAID', 'ARCHIVED'))
);

CREATE INDEX idx_subscription_payment_links_invoice_created
    ON subscription_payment_links (invoice_id, created_at DESC);

CREATE TABLE subscription_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES subscription_invoices(id),
    payment_link_id UUID NOT NULL REFERENCES subscription_payment_links(id),
    provider VARCHAR(30) NOT NULL,
    external_payment_id VARCHAR(255) NOT NULL UNIQUE,
    amount_centavos BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_payment_provider CHECK (provider = 'PAYMONGO'),
    CONSTRAINT chk_subscription_payment_amount CHECK (amount_centavos > 0),
    CONSTRAINT chk_subscription_payment_currency CHECK (currency = 'PHP'),
    CONSTRAINT chk_subscription_payment_status CHECK (
        status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
    )
);

CREATE INDEX idx_subscription_payments_invoice_paid
    ON subscription_payments (invoice_id, paid_at DESC);

CREATE TABLE subscription_refunds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES subscription_payments(id),
    provider VARCHAR(30) NOT NULL,
    external_refund_id VARCHAR(255) NOT NULL UNIQUE,
    amount_centavos BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_subscription_refund_provider CHECK (provider = 'PAYMONGO'),
    CONSTRAINT chk_subscription_refund_amount CHECK (amount_centavos > 0),
    CONSTRAINT chk_subscription_refund_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_subscription_refunds_payment_created
    ON subscription_refunds (payment_id, created_at DESC);

CREATE TABLE paymongo_webhook_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_event_id VARCHAR(255) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    live_mode BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    payload_sha256 VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    error_detail VARCHAR(1000),
    CONSTRAINT chk_paymongo_webhook_receipt_status CHECK (
        status IN ('RECEIVED', 'PROCESSED', 'REJECTED', 'FAILED')
    )
);

CREATE INDEX idx_paymongo_webhook_receipts_received
    ON paymongo_webhook_receipts (received_at DESC);

ALTER TABLE subscription_invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_payment_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_refunds ENABLE ROW LEVEL SECURITY;
ALTER TABLE paymongo_webhook_receipts ENABLE ROW LEVEL SECURITY;

CREATE POLICY subscription_invoices_admin_access ON subscription_invoices
    FOR ALL USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));
CREATE POLICY subscription_payment_links_admin_access ON subscription_payment_links
    FOR ALL USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));
CREATE POLICY subscription_payments_admin_access ON subscription_payments
    FOR ALL USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));
CREATE POLICY subscription_refunds_admin_access ON subscription_refunds
    FOR ALL USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));
CREATE POLICY paymongo_webhook_receipts_system_access ON paymongo_webhook_receipts
    FOR ALL USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE subscription_invoices, subscription_payment_links,
            subscription_payments, subscription_refunds, paymongo_webhook_receipts FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE subscription_invoices, subscription_payment_links,
            subscription_payments, subscription_refunds, paymongo_webhook_receipts FROM authenticated;
    END IF;
END $$;
