ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS template_id VARCHAR(80);
