-- UC-4.8 extension: richer post-level insight signals for the Content Insights page.
-- Values are append-only snapshots. Watch-time values are stored in milliseconds because
-- Facebook post video metrics return millisecond-style counters.

ALTER TABLE facebook_post_metrics
    ADD COLUMN IF NOT EXISTS video_id TEXT,
    ADD COLUMN IF NOT EXISTS post_clicks INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS views INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unique_views INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fifteen_second_views INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sixty_second_views INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS watch_time_ms BIGINT,
    ADD COLUMN IF NOT EXISTS average_watch_time_ms BIGINT,
    ADD COLUMN IF NOT EXISTS reactions_by_type JSONB,
    ADD COLUMN IF NOT EXISTS raw_video_insights JSONB;

CREATE INDEX IF NOT EXISTS ix_fb_metrics_video_fetched
    ON facebook_post_metrics (video_id, fetched_at DESC)
    WHERE video_id IS NOT NULL;
