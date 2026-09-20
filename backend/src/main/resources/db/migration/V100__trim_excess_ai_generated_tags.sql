-- AIClassificationService.persistSuggestedTags used to be purely additive:
-- a repeat classification run only skipped an EXACT label match, but Claude's
-- wording isn't deterministic across calls ("Outdoor Event" vs "outdoor
-- gathering"), so every re-run just kept adding ~15-30 more ai_generated rows
-- with no ceiling per asset. Fixed in code to replace instead of accumulate
-- (delete-then-insert), bounded to 30 per run. This is the one-time cleanup
-- for assets that already accumulated far beyond that before the fix — keeps
-- only the 30 most recently created ai_generated tags per asset. Manual tags
-- (source = 'manual') are untouched.
WITH ranked_ai_tags AS (
    SELECT id, ROW_NUMBER() OVER (
        PARTITION BY media_asset_id
        ORDER BY created_at DESC
    ) AS rn
    FROM asset_tags
    WHERE source = 'ai_generated'
)
DELETE FROM asset_tags
WHERE id IN (SELECT id FROM ranked_ai_tags WHERE rn > 30);
