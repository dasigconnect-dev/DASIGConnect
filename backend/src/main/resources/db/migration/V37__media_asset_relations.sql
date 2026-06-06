-- UC-4.12 Phase 7E: original/version/derivative lineage between assets.
-- Every related asset keeps its own asset code, audit history, metadata, and fixity record;
-- a relation is a link, never an in-place overwrite. Originals stay immutable.

CREATE TABLE media_asset_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    parent_asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    child_asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    relation_type VARCHAR(20) NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_relation_type
        CHECK (relation_type IN ('new_version', 'derived_from', 'replacement_for', 'component_of')),
    CONSTRAINT chk_media_relation_no_self CHECK (parent_asset_id <> child_asset_id),
    CONSTRAINT uq_media_relation UNIQUE (parent_asset_id, child_asset_id, relation_type)
);

CREATE INDEX ix_media_relations_parent ON media_asset_relations (parent_asset_id);
CREATE INDEX ix_media_relations_child ON media_asset_relations (child_asset_id);
CREATE INDEX ix_media_relations_institution ON media_asset_relations (institution_id);

ALTER TABLE media_asset_relations ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_asset_relations_tenant_isolation
    ON media_asset_relations
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );
