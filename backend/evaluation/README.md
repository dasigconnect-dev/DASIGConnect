# Media Suggestion Evaluation

This directory documents the Phase 6, read-only evaluation runner. It compares
four retrieval strategies against human relevance labels without changing the
live API, database schema, feature flags, or frontend.

## Required dataset

Create at least 30 scenarios from original DASIG Media Library files. Do not use
Facebook screenshots as image-embedding inputs. Cover the planned topics:

- startup bootcamp;
- pitching competition;
- technology commercialization;
- speaker presentation;
- workshop participants;
- awarding;
- partnership meeting;
- registration poster;
- online consortium meeting;
- group event photo.

Each scenario references an existing draft submission. The selected asset IDs
must already be attached to that submission so the normal authorization and
candidate filters remain active.

The workflow has two explicit modes. `pool` executes all four strategies and
creates a strategy-blinded labeling template. `evaluate` accepts the completed
template and calculates the comparison metrics. Candidate IDs in the template
are deduplicated and UUID-sorted, so strategy names, scores, and ranking order
do not influence reviewers.

Pool generation stops without writing a labeling artifact if any strategy
returns an asset outside the submission's institution.

Human reviewers assign every pooled candidate one relevance grade before
examining aggregate results:

| Grade | Meaning |
| ---: | --- |
| 0 | Not relevant |
| 1 | Somewhat relevant |
| 2 | Relevant |
| 3 | Highly relevant |

Use at least two reviewers where practical and resolve disagreements before the
final run. Add `knownRelevantAssetIds` to a pool scenario when a relevant,
same-institution library asset may not be returned by any strategy. This reduces
the risk of favoring one strategy because only its results were labeled. The
runner verifies those additional assets are active and belong to the scenario's
institution.

`pool-dataset.example.json` and `dataset.example.json` show the two input shapes.
They intentionally contain one scenario and will be rejected until replaced
with at least 30 real scenarios.

## Step 1: Generate the blinded candidate pool

Use the local PostgreSQL database containing the original test media and current
Voyage embeddings. Set these environment variables from PowerShell:

Pool generation uses the normal retrieval path. Scenarios with text context may
call the configured Voyage query-embedding API; repeated identical text is
subject to the application's short-lived query cache. Run this only with an
approved evaluation dataset and provider budget.

```powershell
$env:SPRING_PROFILES_ACTIVE = "media-ai-evaluation"
$env:MEDIA_AI_EVALUATION_MODE = "pool"
$env:MEDIA_AI_EVALUATION_DATASET = "C:\path\to\media-candidate-pool.json"
$env:MEDIA_AI_EVALUATION_OUTPUT = "C:\path\to\media-labeling-template.json"
.\mvnw.cmd spring-boot:run
```

Replace every `null` value under `relevanceLabels` with a human grade from 0 to
3. Do not remove irrelevant candidates; grade them 0. Keep the generated IDs
and scenario context unchanged during review.

If a scenario produces no candidates, the pool run stops and identifies that
scenario. Add at least one known relevant asset from the same institution so
the no-result behavior can still be measured against a valid relevance set.

## Step 2: Calculate the evaluation report

```powershell
$env:SPRING_PROFILES_ACTIVE = "media-ai-evaluation"
$env:MEDIA_AI_EVALUATION_MODE = "evaluate"
$env:MEDIA_AI_EVALUATION_DATASET = "C:\path\to\completed-media-labels.json"
$env:MEDIA_AI_EVALUATION_OUTPUT = "C:\path\to\media-evaluation-report.json"
.\mvnw.cmd spring-boot:run
```

The profile writes local JSON artifacts only. It does not expose an HTTP
evaluation endpoint and does not write evaluation records to application
tables. Stop the process after the completion log appears.

## Reported measurements

For image-only, semantic-only, hybrid-v2, and reciprocal-rank-fusion rankings,
the report contains:

- top-result relevant/highly relevant rate;
- mean Precision@5;
- mean NDCG@10;
- no-result rate;
- cross-institution result count;
- visual, semantic, and total suggestion average and p95 latency.

The top-result target is at least 70%. The tenant-isolation target is zero
cross-institution results. The report does not claim upload-to-searchable
latency or suggestion acceptance rate; those are operational measures collected
by existing pipeline telemetry and user interaction logs.

Do not change production weights from this run unless the dataset, labels,
environment, and comparison results are retained for review.

## Data handling

- Keep access tokens, image bytes, captions containing personal data, and user
  emails out of the dataset and report.
- Scenario and media UUIDs are sufficient for reproducibility.
- Store completed datasets and reports in the approved validation evidence
  location if they should not be committed to the repository.
