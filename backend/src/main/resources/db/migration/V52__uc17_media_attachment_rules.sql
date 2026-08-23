ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS requires_manual_publishing BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE submissions s
SET requires_manual_publishing = TRUE
WHERE EXISTS (
    SELECT 1
    FROM submission_media_assets sma
    JOIN media_assets ma ON ma.id = sma.media_asset_id
    WHERE sma.submission_id = s.id
      AND ma.file_type IN ('jpeg', 'png', 'webp', 'gif')
)
AND EXISTS (
    SELECT 1
    FROM submission_media_assets sma
    JOIN media_assets ma ON ma.id = sma.media_asset_id
    WHERE sma.submission_id = s.id
      AND ma.file_type IN ('mp4', 'mov', 'webm')
);
