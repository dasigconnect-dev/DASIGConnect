-- UC-2.1 Phase 1: file-system-style albums
--   * albums may nest inside other albums (parent_album_id, self-reference)
--   * album names are unique per parent (not per institution)
--   * every active media asset must belong to an album — album-less assets are
--     force-migrated into a per-institution "Unsorted" root album
--
-- Also repairs the V65 media_albums table, which never added the created_by
-- column the MediaAlbum entity maps. It is added nullable here; the service
-- layer populates it on new rows.

ALTER TABLE media_albums
    ADD COLUMN IF NOT EXISTS parent_album_id UUID REFERENCES media_albums(id);

-- created_by is inconsistent across environments: V65 never created it, but some
-- databases already have it as NOT NULL. Ensure it exists and is nullable so the
-- system-created "Unsorted" album below (no acting user) can be inserted. New rows
-- are still populated by the service layer.
ALTER TABLE media_albums
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);

ALTER TABLE media_albums
    ALTER COLUMN created_by DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_media_albums_parent
    ON media_albums (parent_album_id);

-- Name uniqueness moves from (institution, name) to (institution, parent, name).
-- COALESCE gives NULL parents a stable sentinel so root-level names stay unique.
DROP INDEX IF EXISTS uq_media_albums_institution_name;

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_albums_inst_parent_name
    ON media_albums (
        institution_id,
        COALESCE(parent_album_id, '00000000-0000-0000-0000-000000000000'::uuid),
        LOWER(name)
    );

-- Force every album-less active asset into a per-institution "Unsorted" root album.
DO $$
DECLARE
    inst RECORD;
    unsorted_id UUID;
BEGIN
    FOR inst IN
        SELECT DISTINCT institution_id
        FROM media_assets
        WHERE media_album_id IS NULL AND deleted_at IS NULL
    LOOP
        SELECT id INTO unsorted_id
        FROM media_albums
        WHERE institution_id = inst.institution_id
          AND parent_album_id IS NULL
          AND LOWER(name) = 'unsorted'
        LIMIT 1;

        IF unsorted_id IS NULL THEN
            unsorted_id := gen_random_uuid();
            INSERT INTO media_albums (id, institution_id, name, created_by, created_at, updated_at)
            VALUES (
                unsorted_id,
                inst.institution_id,
                'Unsorted',
                (SELECT id FROM users
                 WHERE institution_id = inst.institution_id
                 ORDER BY created_at
                 LIMIT 1),
                NOW(),
                NOW()
            );
        END IF;

        UPDATE media_assets
        SET media_album_id = unsorted_id
        WHERE institution_id = inst.institution_id
          AND media_album_id IS NULL
          AND deleted_at IS NULL;
    END LOOP;
END $$;
