-- Additive image-only metadata contract. Existing columns remain untouched so
-- current media search and recommendation consumers keep their legacy values.
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS metadata_asset_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS asset_subtype VARCHAR(50),
    ADD COLUMN IF NOT EXISTS communication_purpose VARCHAR(50),
    ADD COLUMN IF NOT EXISTS primary_subject VARCHAR(255),
    ADD COLUMN IF NOT EXISTS visual_setting VARCHAR(160),
    ADD COLUMN IF NOT EXISTS people_presence VARCHAR(20),
    ADD COLUMN IF NOT EXISTS semantic_topics TEXT[],
    ADD COLUMN IF NOT EXISTS detected_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS text_languages TEXT[],
    ADD COLUMN IF NOT EXISTS text_density VARCHAR(10),
    ADD COLUMN IF NOT EXISTS ocr_confidence NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS metadata_schema_version VARCHAR(30),
    ADD COLUMN IF NOT EXISTS metadata_prompt_version VARCHAR(50);

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_metadata_asset_type
        CHECK (metadata_asset_type IS NULL OR metadata_asset_type IN (
            'photograph', 'graphic_design', 'screenshot', 'document',
            'composite_image', 'unknown'
        )),
    ADD CONSTRAINT chk_media_asset_subtype
        CHECK (asset_subtype IS NULL OR asset_subtype IN (
            'group_photo', 'speaker_photo', 'workshop_activity_photo', 'event_photo',
            'product_photo', 'food_photo', 'lab_activity_photo', 'portrait',
            'landscape_photo', 'building_photo', 'artwork_photo', 'event_poster',
            'informational_infographic', 'birthday_greeting', 'program_announcement',
            'presentation_slide', 'online_meeting_screenshot', 'application_screenshot',
            'certificate_or_document', 'photo_collage', 'other', 'unknown'
        )),
    ADD CONSTRAINT chk_media_communication_purpose
        CHECK (communication_purpose IS NULL OR communication_purpose IN (
            'event_documentation', 'event_promotion', 'program_promotion', 'recognition',
            'public_information', 'meeting_documentation', 'educational_information',
            'project_showcase', 'general_documentation', 'unknown'
        )),
    ADD CONSTRAINT chk_media_people_presence
        CHECK (people_presence IS NULL OR people_presence IN ('present', 'absent', 'uncertain')),
    ADD CONSTRAINT chk_media_text_density
        CHECK (text_density IS NULL OR text_density IN ('none', 'low', 'medium', 'high')),
    ADD CONSTRAINT chk_media_ocr_confidence
        CHECK (ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1));

-- V110 predated the Gemini migration and only allowed the former Claude stage.
ALTER TABLE media_ai_processing_metrics
    DROP CONSTRAINT IF EXISTS chk_media_ai_metric_stage;

ALTER TABLE media_ai_processing_metrics
    ADD CONSTRAINT chk_media_ai_metric_stage CHECK (stage IN (
        'R2_UPLOAD', 'DB_REGISTRATION', 'QUEUE_DELAY',
        'VOYAGE_IMAGE_EMBEDDING', 'CLAUDE_CLASSIFICATION', 'GEMINI_CLASSIFICATION',
        'VOYAGE_SEMANTIC_EMBEDDING', 'READY_LATENCY',
        'AI_SUGGESTION_QUERY'
    ));
