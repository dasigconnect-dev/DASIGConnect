CREATE TABLE IF NOT EXISTS email_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id),
    template_code VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_detail TEXT,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_email_delivery_status CHECK (status IN ('sent', 'failed', 'queued'))
);

CREATE INDEX IF NOT EXISTS idx_email_delivery_recipient_created_at
    ON email_delivery_log(recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_delivery_status
    ON email_delivery_log(status, created_at DESC);

ALTER TABLE email_delivery_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY email_delivery_log_tenant_isolation ON email_delivery_log
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = email_delivery_log.recipient_id
              AND (
                  u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );
