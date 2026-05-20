ALTER TABLE institutions
    ADD COLUMN IF NOT EXISTS email_domain VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_institutions_email_domain
    ON institutions(email_domain);
