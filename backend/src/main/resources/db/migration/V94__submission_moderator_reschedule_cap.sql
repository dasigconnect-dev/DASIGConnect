ALTER TABLE submissions
ADD COLUMN IF NOT EXISTS original_scheduled_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS moderator_reschedule_count INTEGER NOT NULL DEFAULT 0;

-- Backfill: for any submission already scheduled before this column existed,
-- the best available anchor is its current scheduled_at -- not perfectly
-- accurate for one already moved by a Moderator pre-migration, but a
-- reasonable starting point rather than leaving it null (which would let a
-- Moderator move it further, still bounded, from whatever it lands on next).
UPDATE submissions
SET original_scheduled_at = scheduled_at
WHERE original_scheduled_at IS NULL
  AND scheduled_at IS NOT NULL;
