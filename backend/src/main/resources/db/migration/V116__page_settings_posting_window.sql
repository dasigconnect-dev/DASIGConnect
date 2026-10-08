ALTER TABLE page_settings ADD COLUMN posting_window_start_hour INT NOT NULL DEFAULT 8;
ALTER TABLE page_settings ADD COLUMN posting_window_end_hour INT NOT NULL DEFAULT 20;