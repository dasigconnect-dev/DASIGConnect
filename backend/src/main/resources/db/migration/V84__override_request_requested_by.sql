-- Override requests are now raised by a moderator while rescheduling (not by the
-- contributor). Track who raised it so the admin decision screen can show it.
ALTER TABLE override_requests
    ADD COLUMN IF NOT EXISTS requested_by_user_id UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_override_requests_requested_by
    ON override_requests(requested_by_user_id);
