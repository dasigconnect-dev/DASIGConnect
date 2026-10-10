ALTER TABLE page_settings
ADD COLUMN per_institution_active_quota INT NOT NULL DEFAULT 3,
ADD COLUMN daily_volume_cap INT NOT NULL DEFAULT 6;
