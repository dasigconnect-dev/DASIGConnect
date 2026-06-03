-- Phase 3A (UC-4.5): denormalized media search documents for full-text search.
-- Tags live in asset_tags, so a separate refreshed document table is cleaner than a
-- generated tsvector column on media_assets.

CREATE TABLE media_asset_search_documents (
    asset_id       UUID        PRIMARY KEY REFERENCES media_assets(id) ON DELETE CASCADE,
    institution_id UUID        NOT NULL REFERENCES institutions(id),
    search_text    TEXT        NOT NULL,
    fts            TSVECTOR    NOT NULL,
    refreshed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_media_asset_search_documents_institution
    ON media_asset_search_documents(institution_id);

CREATE INDEX ix_media_asset_search_documents_fts
    ON media_asset_search_documents USING GIN (fts);

CREATE INDEX ix_media_asset_search_documents_text_trgm
    ON media_asset_search_documents USING GIN (search_text gin_trgm_ops);

CREATE OR REPLACE FUNCTION refresh_media_asset_search_document(p_asset_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_search_text TEXT;
    v_fts TSVECTOR;
    v_institution_id UUID;
BEGIN
    SELECT
        ma.institution_id,
        concat_ws(' ',
            ma.asset_code,
            ma.file_name,
            ma.title,
            ma.ai_category,
            ma.ai_description,
            ma.asset_type,
            array_to_string(ma.visible_objects, ' '),
            array_to_string(ma.specific_subjects, ' '),
            array_to_string(ma.visual_style, ' '),
            array_to_string(ma.dominant_colors, ' '),
            array_to_string(ma.possible_use_cases, ' '),
            array_to_string(ma.ai_tags, ' '),
            i.name,
            u.email,
            tags.labels
        ),
        setweight(to_tsvector('simple', coalesce(ma.title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(ma.asset_code, '') || ' ' || coalesce(ma.file_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(tags.labels, '') || ' ' || coalesce(array_to_string(ma.ai_tags, ' '), '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(ma.ai_category, '') || ' ' || coalesce(ma.asset_type, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(ma.ai_description, '') || ' ' ||
            coalesce(array_to_string(ma.specific_subjects, ' '), '') || ' ' ||
            coalesce(array_to_string(ma.visible_objects, ' '), '') || ' ' ||
            coalesce(array_to_string(ma.possible_use_cases, ' '), '')), 'C') ||
        setweight(to_tsvector('simple', coalesce(i.name, '') || ' ' || coalesce(u.email, '')), 'D')
    INTO v_institution_id, v_search_text, v_fts
    FROM media_assets ma
    JOIN institutions i ON i.id = ma.institution_id
    LEFT JOIN users u ON u.id = ma.uploader_id
    LEFT JOIN LATERAL (
        SELECT string_agg(t.label, ' ' ORDER BY t.source, t.created_at) AS labels
        FROM asset_tags t
        WHERE t.media_asset_id = ma.id
    ) tags ON TRUE
    WHERE ma.id = p_asset_id
      AND ma.deleted_at IS NULL;

    IF v_institution_id IS NULL THEN
        DELETE FROM media_asset_search_documents WHERE asset_id = p_asset_id;
        RETURN;
    END IF;

    INSERT INTO media_asset_search_documents (asset_id, institution_id, search_text, fts, refreshed_at)
    VALUES (p_asset_id, v_institution_id, coalesce(v_search_text, ''), coalesce(v_fts, ''::tsvector), now())
    ON CONFLICT (asset_id)
    DO UPDATE SET
        institution_id = EXCLUDED.institution_id,
        search_text = EXCLUDED.search_text,
        fts = EXCLUDED.fts,
        refreshed_at = EXCLUDED.refreshed_at;
END;
$$;

CREATE OR REPLACE FUNCTION media_asset_search_document_asset_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM media_asset_search_documents WHERE asset_id = OLD.id;
        RETURN OLD;
    END IF;

    PERFORM refresh_media_asset_search_document(NEW.id);
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION media_asset_search_document_tag_trigger()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM refresh_media_asset_search_document(OLD.media_asset_id);
        RETURN OLD;
    END IF;

    PERFORM refresh_media_asset_search_document(NEW.media_asset_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_media_asset_search_documents_asset
AFTER INSERT OR UPDATE OF
    institution_id,
    uploader_id,
    asset_code,
    file_name,
    title,
    ai_category,
    ai_description,
    asset_type,
    visible_objects,
    specific_subjects,
    visual_style,
    dominant_colors,
    possible_use_cases,
    ai_tags,
    deleted_at
ON media_assets
FOR EACH ROW
EXECUTE FUNCTION media_asset_search_document_asset_trigger();

CREATE TRIGGER trg_media_asset_search_documents_asset_delete
AFTER DELETE ON media_assets
FOR EACH ROW
EXECUTE FUNCTION media_asset_search_document_asset_trigger();

CREATE TRIGGER trg_media_asset_search_documents_tags
AFTER INSERT OR UPDATE OF label, source OR DELETE ON asset_tags
FOR EACH ROW
EXECUTE FUNCTION media_asset_search_document_tag_trigger();

INSERT INTO media_asset_search_documents (asset_id, institution_id, search_text, fts, refreshed_at)
SELECT ma.id, ma.institution_id, '', ''::tsvector, now()
FROM media_assets ma
WHERE ma.deleted_at IS NULL
ON CONFLICT DO NOTHING;

SELECT refresh_media_asset_search_document(id)
FROM media_assets
WHERE deleted_at IS NULL;

ALTER TABLE media_asset_search_documents ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_asset_search_documents_tenant_isolation ON media_asset_search_documents
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );
