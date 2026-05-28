-- AI interaction log (UC-2.4 KPI source, populated by UC-3.2 and UC-3.3)
CREATE TABLE ai_interaction_log (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id       UUID        NOT NULL REFERENCES submissions(id),
    institution_id      UUID        NOT NULL REFERENCES institutions(id),
    interaction_type    VARCHAR(30) NOT NULL
                            CHECK (interaction_type IN (
                                'caption_suggestion',
                                'tag_classification',
                                'media_recommendation'
                            )),
    action_taken        VARCHAR(30) NOT NULL,
    tone_selected       VARCHAR(30),          -- caption_suggestion only
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_interaction_institution ON ai_interaction_log(institution_id, created_at DESC);
CREATE INDEX idx_ai_interaction_type        ON ai_interaction_log(interaction_type, created_at DESC);
