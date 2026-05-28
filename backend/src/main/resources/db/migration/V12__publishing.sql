-- UC-3.1: Automated Publishing Pipeline
-- publication_attempts: immutable log of every Facebook publish attempt per submission
-- facebook_page_tokens: encrypted storage for the Facebook Page Access Token

CREATE TABLE publication_attempts (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id    UUID        NOT NULL REFERENCES submissions(id),
    attempt_number   INT         NOT NULL DEFAULT 1,
    attempted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    result           VARCHAR(10) NOT NULL,
    error_detail     TEXT,
    photo_ids_staged TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_pub_result CHECK (result IN ('success', 'failed'))
);

CREATE INDEX idx_pub_attempts_submission ON publication_attempts(submission_id, attempted_at DESC);

CREATE TABLE facebook_page_tokens (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id           VARCHAR(50) NOT NULL UNIQUE,
    encrypted_token   TEXT        NOT NULL,
    expires_at        TIMESTAMPTZ,
    is_active         BOOLEAN     NOT NULL DEFAULT true,
    last_validated_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
