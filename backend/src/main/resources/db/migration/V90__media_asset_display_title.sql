-- UC-2.2: lets an actor rename an asset's display title independently of its
-- original filename. Nullable and purely a display-layer overlay -- the
-- storage object key (media/<institution-code>/<asset-id>/<original-filename>)
-- and file_name column are never touched by a rename, same principle as
-- album rename never touching the storage tree (see CLAUDE.md).
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS display_title VARCHAR(255);
