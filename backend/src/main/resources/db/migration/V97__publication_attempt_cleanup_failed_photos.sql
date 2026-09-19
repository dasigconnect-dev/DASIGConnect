-- UC-3.2 A1: a partial photo-staging cleanup failure (some staged photos
-- deleted successfully, others didn't) was previously silent beyond a server
-- log line -- publication_attempts.photo_ids_staged records every staged
-- photo for the attempt, not specifically which ones cleanup failed to
-- delete, and neither was ever surfaced to an Administrator. This column
-- records just the failed-to-delete subset, so it can be shown in the
-- Review Queue's Failed tab for manual recovery.
ALTER TABLE publication_attempts
ADD COLUMN IF NOT EXISTS photo_ids_cleanup_failed TEXT;
