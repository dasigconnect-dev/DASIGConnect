# DASIGConnect Documentation

This directory contains the formal deliverables and retained visual reference material used by the project. Completed plans, superseded drafts, source-level requirement notes, and temporary generation artifacts remain recoverable from Git history instead of remaining in the demo branch.

## Canonical Documents

| Area | Canonical path | Purpose |
|---|---|---|
| Proposal | `pdf/DASIGConnect Project Proposal.pdf` | Approved project scope and objectives |
| MVP validation findings | `pdf/DASIGConnect_MVP Validation Findings & Refactoring Priorities.pdf` | Validation findings and refactoring priorities |
| Requirements inventory | `pdf/DASIGConnect_Requirements_Inventory.pdf` | Requirements inventory supporting traceability |
| SRS submission copy | `pdf/DASIGConnect_Software Requirements Specifications.pdf` | Formal SRS artifact |
| Software design | `pdf/DASIGConnect_Software Design Description.pdf` | Formal SDD artifact |
| Project management plan | `pdf/DASIGConnect_Software Project Management Plan.pdf` | Formal SPMP artifact |
| Software test document | `pdf/DASIGConnect_Software Test Document.pdf` | Formal test plan, cases, and reports |
| Traceability matrix | `pdf/DASIGConnect_Traceability Matrix.pdf` | Requirements traceability artifact |
| Local database setup | `pdf/local-db-setup.pdf` | Local PostgreSQL setup reference |
| Refactoring evidence | `word/DASIGConnect_Refactoring_Evidence.docx` | Required editable evidence for demo-readiness fixes, cleanup, and test coverage |
| Visual references | `img/` | Retained DASIG content screenshots |
| Deployment | `../DEPLOY.md` | Deployment and runtime guidance |
| Repository setup | `../README.md` | Architecture and local development instructions |

## Repository Rules

- Keep one clearly named canonical copy of each deliverable in version control.
- Store respondent-identifying spreadsheets, recordings, and private validation evidence outside the source repository.
- Do not commit `.env` files, credentials, tokens, generated previews, browser traces, or temporary conversion outputs.
- Use Git history and merged pull requests for completed implementation plans and historical task status.
- Add new documentation only when it is maintained, required for development, or part of an approved deliverable.

## Cleanup Manifest

Cleanup approved by the project member requesting demo preparation on 17 October 2026. All removed files remain recoverable from Git history.

| Removed path | Reason | Canonical replacement |
|---|---|---|
| `../HANDOFF.md` | May 2026 session handoff no longer represented current architecture or status | Git history and merged pull requests |
| `../TASKS.md` | May 2026 tracker contained stale completion states and gaps | Current issue tracker and merged pull requests |
| `md/Implementation-Plan.md` | Pre-refactor implementation plan completed and superseded | Current SRS, STD, and merged PRs |
| `md/Module_1_todos.md` | Historical module checklist completed/superseded | Current SRS use cases and tests |
| `md/Module_2_BE2_implementation_summary.md` | Historical implementation summary | Current SRS use cases and source code |
| `md/Module_2_notification_trigger_matrix.md` | Historical notification planning artifact | Current UC-3.3 specification and source code |
| `md/Backend_API_Security_Scan_Report.md` | Point-in-time audit superseded by implemented fixes and current tests | Security configuration, CI, and controller tests |
| `md/Frontend_Performance_Accessibility_Optimization_Plan.md` | Completed point-in-time optimization plan | Current frontend source, CI, and test suite |
| `md/caching-architecture-incremental-plan.md` | Completed multi-phase implementation log | Current TanStack Query/cache implementation |
| `md/SUBMISSION_REVIEW_SERVER_PAGINATION_DEVELOPMENT_PLAN.md` | Pagination implementation completed | Current submission/review APIs and UI |
| `md/ai-media-library-upgrade.md` | Early AI media design superseded by later implementation | Current SRS UC-2.1/UC-2.2 and source code |
| `md/AI_MEDIA_LIBRARY_PROCESSING_DECISION.md` | Historical architecture decision absorbed into implementation | Current media-processing services and migrations |
| `md/AI_MEDIA_SUGGESTION_STEP1_SAFE_DEVELOPMENT_PLAN.md` | Completed phased development plan | Current AI recommendation implementation and tests |
| `md/MEDIA_LIBRARY_VOYAGE_RETRIEVAL_IMPLEMENTATION_PLAN.md` | Completed retrieval implementation plan | Current Voyage/pgvector implementation and tests |
| `md/DEMO_READINESS_CODE_AND_DOCUMENT_CLEANUP_PLAN.md` | Phase plan completed and replaced by this maintained inventory | This document and PR history |
| `md/srs/SRS.md`, `md/srs/UC-*.md`, and the SRS alignment notes | Editable/source-level requirement copies removed from the demo branch | `pdf/DASIGConnect_Software Requirements Specifications.pdf` and Git history |
| `md/std.md` | Editable Software Test Document removed from the demo branch | `pdf/DASIGConnect_Software Test Document.pdf` and Git history |

## Local-Only Material

The retained formal deliverables, PDFs, and image files are part of the demo documentation set. Generated `output`, `outputs`, `tmp`, and `work` directories are ignored and are not part of the repository. Respondent-identifying data and non-deliverable academic working files should remain outside the source repository.
