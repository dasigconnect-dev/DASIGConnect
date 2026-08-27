-- Draft submission media staging.
--
-- Media uploaded while a submission is still a DRAFT is "staged": a real
-- media_assets row with NO institution and status = 'STAGED'. It is invisible to
-- the Media Repository and is only bound to an institution (status -> 'PROCESSING')
-- at Submit for Review. This lets an admin change a draft's "Posting As"
-- institution without losing uploaded files, and lets an abandoned draft be
-- hard-deleted without leaving orphaned library assets.

ALTER TABLE media_assets ALTER COLUMN institution_id DROP NOT NULL;

-- Only a staged asset may have a null institution. Every existing row already has
-- a non-null institution, so this is satisfied immediately (no backfill).
ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_staged_institution
    CHECK (institution_id IS NOT NULL OR status = 'STAGED');

-- Extend tenant isolation (V69 form) so an uploader can see their own staged rows.
DROP POLICY IF EXISTS media_assets_tenant_isolation ON media_assets;
CREATE POLICY media_assets_tenant_isolation ON media_assets
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
        OR institution_id IN (SELECT id FROM institutions WHERE is_protected)
        OR (
            media_assets.institution_id IS NULL
            AND media_assets.status = 'STAGED'
            AND media_assets.uploader_id
                = nullif(current_setting('app.current_user_id', true), '')::UUID
        )
    );

-- asset_tags policy unchanged: staged assets never get tag rows.
