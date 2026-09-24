-- AI Feature Adoption gains the caption writing check (UC-2.4 A14, composer):
--
--  * proofread — 'checked' once per check, with the number of findings it
--                returned in suggested_value; 'applied' once per suggested fix
--                the user applied. Adoption = fixes applied / fixes suggested.
ALTER TABLE ai_interaction_log DROP CONSTRAINT IF EXISTS ai_interaction_log_interaction_type_check;
ALTER TABLE ai_interaction_log ADD CONSTRAINT ai_interaction_log_interaction_type_check
    CHECK (interaction_type IN (
        'caption_suggestion',
        'tag_classification',
        'media_recommendation',
        'album_match',
        'template_draft',
        'proofread'
    ));
