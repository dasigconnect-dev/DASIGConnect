-- AI Feature Adoption (Analytics) gains two tracked features:
--
--  * album_match    — Album Auto-Match in the composer. On each suggestion the
--                     latest proposal is recorded ('suggested', with what was
--                     proposed in suggested_value); on submit, one outcome row
--                     per submission ('kept' / 'changed').
--  * template_draft — Generate Template from Top Posts (UC-1.5 A10):
--                     'generated' per draft returned, 'saved' when one is saved.
--
-- Template drafts aren't tied to a submission, and Moderators/Admins have no
-- institution of their own, so both columns become nullable. Analytics scopes
-- a row by its submission's institution when it has one, else by this column.
ALTER TABLE ai_interaction_log ALTER COLUMN submission_id DROP NOT NULL;
ALTER TABLE ai_interaction_log ALTER COLUMN institution_id DROP NOT NULL;

ALTER TABLE ai_interaction_log ADD COLUMN IF NOT EXISTS suggested_value TEXT;

ALTER TABLE ai_interaction_log DROP CONSTRAINT IF EXISTS ai_interaction_log_interaction_type_check;
ALTER TABLE ai_interaction_log ADD CONSTRAINT ai_interaction_log_interaction_type_check
    CHECK (interaction_type IN (
        'caption_suggestion',
        'tag_classification',
        'media_recommendation',
        'album_match',
        'template_draft'
    ));

CREATE INDEX IF NOT EXISTS idx_ai_interaction_submission_type
    ON ai_interaction_log(submission_id, interaction_type, created_at DESC)
    WHERE submission_id IS NOT NULL;
