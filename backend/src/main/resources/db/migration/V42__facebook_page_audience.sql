-- UC-4.8 Phase 5b: Facebook PAGE-level audience snapshots (followers + best-effort demographics).
-- Append-only time series fetched daily from the Graph API. This is page-global data for the
-- single DASIG Facebook Page (NOT institution-scoped), so it carries no tenant RLS policy; read
-- access is gated at the controller layer. Demographics columns are nullable because Meta has
-- deprecated most page_fans_* breakdown metrics — the sync degrades gracefully when they are
-- unavailable.

CREATE TABLE IF NOT EXISTS facebook_page_audience_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    followers_count INT,
    fan_count INT,
    age_gender JSONB,
    countries JSONB,
    cities JSONB,
    raw JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_fb_page_audience_page_fetched
    ON facebook_page_audience_snapshots (page_id, fetched_at DESC);
