-- UC-3.5 Category C — Guard Rail Override Requests
-- Stores override requests submitted by Contributors in UC-1.3 and resolved by Administrator here.
CREATE TABLE IF NOT EXISTS override_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID NOT NULL REFERENCES submissions(id),
    contributor_id  UUID NOT NULL REFERENCES users(id),
    institution_id  UUID NOT NULL REFERENCES institutions(id),
    requested_slot  TIMESTAMPTZ NOT NULL,
    violated_rule   VARCHAR(20) NOT NULL,
    override_reason TEXT NOT NULL,
    decision        VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (decision IN ('pending', 'approved', 'denied', 'suggested', 'expired')),
    decision_reason TEXT,
    suggested_slot  TIMESTAMPTZ,
    decided_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_override_requests_institution
    ON override_requests(institution_id)
    WHERE decision = 'pending';

CREATE INDEX IF NOT EXISTS idx_override_requests_slot
    ON override_requests(requested_slot)
    WHERE decision = 'pending';
