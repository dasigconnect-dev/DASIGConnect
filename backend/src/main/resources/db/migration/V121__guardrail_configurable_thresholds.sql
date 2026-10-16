ALTER TABLE page_settings ADD COLUMN IF NOT EXISTS conflict_buffer_minutes INT NOT NULL DEFAULT 30;
ALTER TABLE page_settings ADD COLUMN IF NOT EXISTS minimum_lead_time_hours INT NOT NULL DEFAULT 2;
ALTER TABLE page_settings ADD COLUMN IF NOT EXISTS maximum_lead_time_days INT NOT NULL DEFAULT 30;
