-- UC-2.1: the protected default institution ("DASIG Central Visayas") is a shared
-- media space. Every institution can see its assets/tags; RLS must therefore let
-- any authenticated tenant read rows that belong to a protected institution.
-- (media_albums has no RLS, so album visibility is handled in the service layer.)

DROP POLICY IF EXISTS media_assets_tenant_isolation ON media_assets;
CREATE POLICY media_assets_tenant_isolation ON media_assets
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
        OR institution_id IN (SELECT id FROM institutions WHERE is_protected)
    );

DROP POLICY IF EXISTS asset_tags_institution_isolation ON asset_tags;
CREATE POLICY asset_tags_institution_isolation ON asset_tags
    USING (
        EXISTS (
            SELECT 1 FROM media_assets ma
            WHERE ma.id = asset_tags.media_asset_id
              AND (
                  ma.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
                  OR ma.institution_id IN (SELECT id FROM institutions WHERE is_protected)
              )
        )
    );
