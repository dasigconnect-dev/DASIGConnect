ALTER TABLE institutions
    ADD COLUMN email_domain VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX idx_institutions_email_domain ON institutions(email_domain);
