# Architecture Decision Records (ADRs)

One short file per non-obvious decision. ADRs capture *why* a choice was made so the
team (and the panel) can see the reasoning — not just the result. They are append-only:
to reverse a decision, write a new ADR that supersedes the old one (don't edit history).

## Format (MADR-lite)

```
# ADR-NNNN: <short title>
- Status: Proposed | Accepted | Superseded by ADR-XXXX
- Date: YYYY-MM-DD
- Deciders: <names>

## Context
What problem/forces are at play? Constraints (HikariCP=5, free tier, Meta review...).

## Decision
What we chose, stated plainly.

## Consequences
Trade-offs accepted, what gets easier/harder, follow-ups.
```

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-phash-not-embeddings-for-duplicate-detection.md) | pHash, not embeddings, for duplicate detection | Accepted |
| [0002](0002-bounded-ingestion-queue.md) | Bounded ingestion queue over fire-and-forget @Async | Accepted |
| [0003](0003-separate-supabase-project-for-capstone2-dev.md) | Separate Supabase project for Capstone 2 development | Accepted |
| [0004](0004-folders-vs-albums-modeling.md) | Folders (single-parent) and Albums (many-to-many) are distinct | Accepted |
