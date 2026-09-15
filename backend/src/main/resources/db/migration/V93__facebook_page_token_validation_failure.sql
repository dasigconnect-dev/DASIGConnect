ALTER TABLE facebook_page_tokens
ADD COLUMN IF NOT EXISTS validation_failed_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS validation_failure_reason TEXT;
