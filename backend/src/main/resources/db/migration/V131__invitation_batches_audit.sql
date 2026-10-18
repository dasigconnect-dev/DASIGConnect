CREATE TABLE IF NOT EXISTS invitation_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    row_count INT NOT NULL,
    sent_count INT NOT NULL,
    rejected_count INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE invitation_tokens
    ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES invitation_batches(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_invitation_batches_created_by_created_at
    ON invitation_batches(created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invitation_tokens_batch_id
    ON invitation_tokens(batch_id);

