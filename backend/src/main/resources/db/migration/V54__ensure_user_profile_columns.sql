-- Repair databases that were created before the UC-1.4 profile settings migration
-- landed or that skipped the later user-profile column additions.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS notify_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_email BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET notify_in_app = TRUE
WHERE notify_in_app IS NULL;

UPDATE users
SET notify_email = TRUE
WHERE notify_email IS NULL;

UPDATE users
SET session_version = 0
WHERE session_version IS NULL;
