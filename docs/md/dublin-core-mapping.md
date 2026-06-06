# DASIGConnect — Dublin Core Mapping & Inventory Export (UC-4.12 Phase 7G)

The Media Library can export a **portable, standards-aligned inventory** so DASIG is never locked
into this application. The export is served by `GET /api/v1/media-repository/export?format=csv|json`
(validator = own institution; administrator = network or a chosen institution) and is recorded as
an audited `MEDIA_REPOSITORY_EXPORTED` event.

## Safety guarantees

- **No storage credentials, signed URLs, or private service URLs** appear in the export — the
  `media_assets.storage_url` column is deliberately omitted.
- **No cross-tenant rows** — the export is scoped to one institution (or, for administrators, the
  network) using the same tenant rules as the rest of the API.
- Read-only; no DB connection is held across any external call (there are none in this path).

## Dublin Core (DCMI) element mapping

DASIGConnect uses the 15 Dublin Core elements where they apply. Columns are prefixed `dc_`.

| Dublin Core element | Export field(s) | Source |
|---|---|---|
| `identifier` | `dc_identifier` | `media_assets.asset_code` (persistent ID) |
| `title` | `dc_title` | human-confirmed `media_assets.title` |
| `creator` | `dc_creator_uploader`, `dc_creator_rights_holder` | uploader email **and** rights holder, kept distinct (the uploader is not necessarily the rights holder) |
| `date` | `dc_date` | `media_assets.created_at` (ingest date) |
| `subject` | `dc_subject_tags`, `dc_subject_category` | manual `asset_tags` (human) + confirmed `ai_category` |
| `rights` | `dc_rights_basis`, `dc_rights_visibility`, `dc_rights_expires`, `dc_rights_state` | `media_asset_rights` (basis/expiry/derived state) + `media_assets.visibility` |
| `relation` | `dc_relation_parents`, `dc_relation_children` | `media_asset_relations` lineage (`<asset_code>:<relation_type>`) |
| `format` | `dc_format_type`, `dc_format_size_bytes` | `media_assets.file_type` and `file_size_bytes` |

Elements **not** populated (documented honestly): `description` (AI description is provenance, not a
curatorial abstract), `publisher`, `contributor`, `type`, `source`, `language`, `coverage`. These
can be added later if a curatorial need arises.

## Preservation fields (PREMIS-flavored, prefixed `pres_`)

We use **PREMIS concepts** for fixity and preservation events without claiming a PREMIS-complete
implementation (honest scope, per the Phase 7 boundaries).

| Field | Source | Meaning |
|---|---|---|
| `pres_fixity_sha256` | `media_assets.content_sha256` | bit-level fixity baseline (Phase 7A) |
| `pres_fixity_status` | `media_assets.integrity_status` | `PENDING`/`VERIFIED`/`MISMATCH`/`MISSING`/`ERROR` |
| `pres_curated_at` | `media_assets.curated_at` | human curation timestamp |
| `pres_classification_model` | `media_assets.ai_classification_model` | AI provenance |
| `pres_lifecycle_status` | `media_assets.status` | `PROCESSING`/`READY`/`FAILED` |

## Formats

- **CSV** (`text/csv`) — header row (`CSV_HEADERS`) + one row per active asset; RFC-4180 quoting.
- **JSON** (`application/json`) — an envelope `{ generatedAt, scope, institutionId, count,
  dublinCoreMapping, assets[] }`; `dublinCoreMapping` embeds the element→source map above so a
  downstream consumer can interpret the records without this document.

Both export **active** assets (`deleted_at IS NULL`). Soft-deleted assets pending purge are visible
in the Repository Health dashboard, not the portable inventory.
