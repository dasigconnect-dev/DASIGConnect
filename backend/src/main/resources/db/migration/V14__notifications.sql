CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(50) NOT NULL,
    message TEXT,
    deep_link TEXT,
    read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created_at
    ON notifications(recipient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_at
    ON notifications(recipient_id, read_at);

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY notifications_tenant_isolation ON notifications
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = notifications.recipient_id
              AND (
                  u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );
