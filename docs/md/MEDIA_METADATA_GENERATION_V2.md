# Media Library Metadata Generation V2

## Scope

This phase improves image-only metadata generation for Media Library uploads. It does not change AI Media Suggestion candidate retrieval, ranking, thresholds, diversity, or user interface behavior.

## Confirmed Existing Workflow

1. `MediaAssetService` registers the uploaded object after Cloudflare R2 storage succeeds.
2. `MediaProcessingQueueService` creates durable processing jobs.
3. `MediaProcessingWorker` first builds retrieval embeddings, then queues optional Gemini enrichment for images.
4. `AIClassificationService.enrichAsset` sends the stored image to `GeminiVisionClient`.
5. Gemini returns structured JSON. The backend persists metadata to `media_assets` and AI-generated tags to `asset_tags`.
6. A meaningful metadata change increments `semantic_revision` and queues `EMBED_SEMANTIC_ONLY`.
7. `MediaSemanticEmbeddingService` builds normalized text from persisted metadata and asks Voyage AI `voyage-4-lite` for a 1,024-dimensional document vector.
8. Revision-conditional repository updates discard stale embedding results when metadata changes while Voyage is running.

Gemini and Voyage failures remain retryable through the existing job queue. Optional Gemini enrichment does not mark an already searchable asset as failed.

## Confirmed Problems Before V2

- The Gemini contract mixed media format, content category, event interpretation, use cases, tags, and temporal guesses.
- Taxonomy values were prompt-controlled but not consistently normalized by one backend contract.
- OCR text existed, but title, language, density, and OCR confidence were not stored separately.
- Case-only duplicate values could survive in several arrays.
- Semantic embedding input repeated the same concept through description, category, tags, use cases, scenes, and other overlapping fields.
- Successful regeneration always advanced the semantic revision, even when normalized semantic metadata was unchanged.
- The telemetry database constraint allowed `CLAUDE_CLASSIFICATION` but not the active `GEMINI_CLASSIFICATION` stage.

## Canonical Contract

### Asset type

`photograph`, `graphic_design`, `screenshot`, `document`, `composite_image`, `unknown`

### Asset subtype

`group_photo`, `speaker_photo`, `workshop_activity_photo`, `event_photo`, `product_photo`, `food_photo`, `lab_activity_photo`, `portrait`, `landscape_photo`, `building_photo`, `artwork_photo`, `event_poster`, `informational_infographic`, `birthday_greeting`, `program_announcement`, `presentation_slide`, `online_meeting_screenshot`, `application_screenshot`, `certificate_or_document`, `photo_collage`, `other`, `unknown`

### Communication purpose

`event_documentation`, `event_promotion`, `program_promotion`, `recognition`, `public_information`, `meeting_documentation`, `educational_information`, `project_showcase`, `general_documentation`, `unknown`

### Other controlled values

- `people_presence`: `present`, `absent`, `uncertain`
- `text_density`: `none`, `low`, `medium`, `high`
- `metadata_schema_version`: `media-metadata-v2`
- `metadata_prompt_version`: `gemini-image-only-v2`

The existing `asset_type` column is retained as a legacy compatibility value. New broad format data is stored in `metadata_asset_type`; the backend derives the legacy value from the v2 subtype so existing consumers continue to work.

## Validation Rules

- Confidence values must be finite and between 0 and 1.
- Unsupported controlled values become `unknown` or `uncertain` instead of becoming new categories.
- Text is trimmed, whitespace-normalized, and length-bounded.
- Arrays are bounded and deduplicated case-insensitively.
- Topics, search terms, colors, and languages are normalized to lowercase.
- Missing OCR is represented by an empty array; `text_density=none` with no OCR forces OCR confidence to 0.
- Malformed JSON, invalid confidence values, and provider failures are rejected before existing metadata is mutated.
- Unknown model fields are not persisted.
- Unambiguous subtypes are reconciled with their broad type (for example, `event_poster` becomes `graphic_design` and `group_photo` becomes `photograph`).

## Voyage Semantic Input

The semantic document now uses a compact set of persisted fields:

- User title, filename, album, and manual tags.
- Caption and primary subject.
- Broad type, subtype, and communication purpose.
- Topics, observable activities, and visible objects.
- OCR text and detected title.
- Setting when informative.

AI tag aliases, repeated category labels, colors, speculative use cases, event hypotheses, and temporal guesses are excluded. The Voyage model, dimension, vector storage, and revision-safe upsert remain unchanged.

## Database Migration

`V132__structured_media_metadata_contract.sql` adds nullable structured metadata columns and check constraints. It does not delete or rewrite existing metadata or embeddings, and it performs no production backfill. It also permits the active `GEMINI_CLASSIFICATION` telemetry stage.

## Human-Reviewed Benchmark Procedure

The files in `docs/img` are Facebook screenshots, often containing browser or Facebook interface elements and sometimes multiple media items. They are reference sources, not clean original-upload inputs. A screenshot of a collage must not be labeled as if each original photograph was separately analyzed.

1. Obtain original uploaded images when possible. Otherwise crop one clearly bounded media asset from a screenshot and record that it is a crop.
2. Create a balanced set covering photographs, groups, speakers, workshops, posters, infographics, birthday greetings, announcements, slides, screenshots, documents, and collages.
3. Reserve at least 25% as a held-out set. Do not place held-out labels or examples in the prompt.
4. Have two reviewers independently label type, subtype, purpose, visible facts, useful topics, readable text, and unsupported assumptions.
5. Resolve reviewer disagreement before scoring.
6. Run Gemini once per benchmark image using the production model and prompt version. Store raw output separately from normalized output.
7. Score:
   - Exact accuracy for controlled classifications.
   - Schema-valid response rate.
   - Caption factuality and specificity using a documented 1-5 rubric.
   - Topic precision: useful supported topics divided by all returned topics.
   - OCR character or word error rate on legible text.
   - Unsupported-assumption rate.
   - Consistency across visually similar assets.
8. Compare prompt versions on the same held-out set. Do not claim improvement from unit tests alone.

No live Gemini benchmark was executed in this phase. The repository does not contain clean human-reviewed reference labels, and provider-backed evaluation would incur external API calls. The deterministic tests verify contract behavior, not real-world model accuracy.

Gemini remains the OCR mechanism for this phase. `ocr_confidence` is model-estimated rather than a provider-certified OCR score. A dedicated OCR dependency was not added because the available screenshots do not provide a clean labeled dataset showing that its deployment, latency, and maintenance cost would produce a meaningful improvement.

## Separate Next Phase

Recommendation relevance still requires its own audit and evaluation of retrieval candidates, query construction, thresholds, score combination, duplicate handling, and ranking. None of those behaviors are changed here.
