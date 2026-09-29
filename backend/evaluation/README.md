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

Human reviewers assign every pooled candidate one relevance grade before
examining aggregate results:

| Grade | Meaning |
| ---: | --- |
| 0 | Not relevant |
| 1 | Somewhat relevant |
| 2 | Relevant |
| 3 | Highly relevant |

Use at least two reviewers where practical and resolve disagreements before the
final run. Include candidates pooled from every experiment plus any known
relevant library assets. This reduces the risk of favoring one strategy because
only its results were labeled.

`dataset.example.json` shows the shape only. It intentionally has one scenario
and will be rejected by the runner until it is replaced with at least 30 real,
human-labeled scenarios.

## Run locally

Use the local PostgreSQL database containing the original test media and current
Voyage embeddings. Set these environment variables from PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "media-ai-evaluation"
$env:MEDIA_AI_EVALUATION_DATASET = "C:\path\to\media-evaluation.json"
$env:MEDIA_AI_EVALUATION_OUTPUT = "C:\path\to\media-evaluation-report.json"
.\mvnw.cmd spring-boot:run
```

The profile writes one JSON report. It does not expose an HTTP evaluation
endpoint and does not write evaluation records to application tables. Stop the
process after the completion log appears.

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
