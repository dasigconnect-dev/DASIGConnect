# ADR-0004: Folders (single-parent) and Albums (many-to-many) are distinct
- Status: Accepted
- Date: 2026-05-30
- Deciders: DASIGConnect team

## Context
UC-4.1 organizes the media library. The panel referenced "albums," and the team wants it to
feel like Google Drive but AI-advanced. Two membership models were on the table: one folder
per asset (single `folder_id`) vs. many folders per asset (junction table). We also had to
decide whether "folder" and "album" are the same thing.

Key facts that shaped the decision:
- **Google Drive deprecated multi-parent folders (2020)** for scalability/maintainability —
  a file now lives in exactly one folder. Multi-parent caused query, sync, and "which copy
  is real" confusion.
- **Google Photos albums are many-to-many** — a photo can be in several albums at once —
  because curated collections are a different job from filing.
- Multi-dimensional discovery in this system is already served by **tags + AI embeddings +
  semantic search**, not by folder membership.

## Decision
Model **two distinct concepts**:

- **Folder** — *where an asset lives.* Single-parent hierarchy via `media_folders` +
  `media_assets.folder_id` (`ON DELETE SET NULL`). An asset is in **one** folder.
- **Album** — *curated collection across folders.* Many-to-many via `media_albums` +
  `album_assets` junction (`ON DELETE CASCADE` on links). An asset can be in **many** albums
  without moving. `media_albums.source` ∈ {`manual`, `ai_suggested`} so Phase 2 AI
  auto-grouping writes into the same structure.

## Consequences
- **Maintainable + scalable:** single `folder_id` keeps the common path (filing, listing by
  folder) cheap and unambiguous — no junction orphan logic on the hot path.
- **Flexible where it matters:** albums give the multi-membership/curation power without
  polluting the folder model.
- **AI has a home:** auto-grouping inserts `media_albums (source='ai_suggested')` +
  `album_assets` rows — no schema change needed in Phase 2.
- **Digital-repository maturity:** the folder-vs-album distinction is exactly what separates
  a managed digital media library from a flat file store (ties to scope §10).
- Two structures to build (folder CRUD + album CRUD) instead of one — accepted; both are
  simple and serve genuinely different jobs.
- `suggested_album_id` from scope §5.1 is dropped in favor of the junction + `source` flag.
