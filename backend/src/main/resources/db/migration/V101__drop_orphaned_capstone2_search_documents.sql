-- The shared dev database still carries the full-text search-document objects
-- from the capstone2-advance-development branch's own V31 migration
-- (media_asset_search_documents + its refresh function and triggers). That
-- branch was never merged into dev, and nothing in dev's code reads
-- media_asset_search_documents. The refresh function also selects
-- media_assets.title, which only exists after capstone2's V30 and is not in
-- dev's schema (dev uses display_title, V90). So every insert or delete on
-- asset_tags fired the tag trigger and failed with "column ma.title does not
-- exist". SubmissionService.applySubmissionMediaTags hits this on every
-- submit, so submitting a post returned a 500.
--
-- The table is a denormalized cache rebuilt from media_assets/asset_tags, so
-- dropping it loses no source data. Everything uses IF EXISTS, so this is a
-- no-op on a database that never had these objects.
DROP TRIGGER IF EXISTS trg_media_asset_search_documents_tags ON asset_tags;
DROP TRIGGER IF EXISTS trg_media_asset_search_documents_asset ON media_assets;
DROP TRIGGER IF EXISTS trg_media_asset_search_documents_asset_delete ON media_assets;

DROP FUNCTION IF EXISTS media_asset_search_document_tag_trigger();
DROP FUNCTION IF EXISTS media_asset_search_document_asset_trigger();
DROP FUNCTION IF EXISTS refresh_media_asset_search_document(UUID);

DROP TABLE IF EXISTS media_asset_search_documents;
