# Release Notes

All notable changes to the DASIGConnect platform are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.1.0] - 2026-10-19

### Security
- **Subscription Entitlement Enforcement:** Implemented strict durable subscription entitlement boundaries and quota gating for organization plans (Free, Pro, Enterprise), preventing unauthorized resource usage, storage quota overflows, and unmetered AI operations.
- **PayMongo QRPh Webhook & Payment Verification:** Integrated secure signature validation and webhook integrity checks for automated PayMongo QRPh payment processing and subscription billing settlement.
- **Administrative Billing RBAC:** Enforced role-based access control protecting billing and subscription management endpoints (`/api/v1/billing/**`, `/api/v1/subscriptions/**`), ensuring strictly authorized organization administrators can modify subscription plans or access payment history.

### Fixed
- **Flyway Migration Sequencing & Collisions:** Resolved duplicate migration version conflicts across parallel feature streams (V127, V128, V129, V130) and repaired backend migration startup for media job types (V117).
- **Submission Gate & Staged Media Completeness:** Corrected submission completeness validation logic to verify staged media attachments prior to running binary gate checks.
- **AI Suggestion Panel Resiliency:** Fixed submission autosave URL synchronization, stabilized caption suggestion caching so suggestions generate once per session and persist, and unified the AI unavailable fallback banner across drafting views.
- **Mobile Viewport Stacking & Bottom Sheet Clipping:** Eliminated clipping on 360px viewport mobile screens by lifting modal and bottom-sheet stacking contexts, uncoupling fixed action trays from scrolling content.
- **Fast-Track Resolution Deep-Linking:** Restored resolution retry action links and direct navigation on mobile viewports for rejected or revision-requested submissions.
- **Frontend Compiler & React Lint Warnings:** Cleared unused variables (e.g. `hasEventContext`), unescaped HTML entities, and `useEffect` dependency warnings across all refactored screens.

### Changed
- **Submission Status Guidance & Badge Copy:** Redesigned contributor status guidance callouts with plain-language, action-oriented recommendations and concise badge copy to improve user autonomy and reduce review cycle confusion.
- **Mobile Layout & Bottom Action Bar (360px Viewport):** Overhauled contributor and reviewer mobile interfaces with symmetrical equal-column grid layouts, zero wasted white space, and icon-based collapsed header status chips.
- **System Health Dashboard Redesign:** Redesigned administrative System Health UI with responsive grid views, real-time AI quota tracking, and enhanced operational metrics visualization.

### Removed
- **Non-Production Development Artifacts:** Removed internal planning artifacts, markdown notes, agent configuration folders (`.agent/`, `.claude/`, `.gemini/`), developer documentation (`CLAUDE.md`, `DEPLOY.md`), and offline evaluation suites (`backend/evaluation/`) from the production release branch.
- **Superseded Code & Stale Mock Bindings:** Removed unused temporary mock bindings and resolved obsolete draft state transitions.

### Added
- **Subscription & Commercial Billing Engine (Phases 1–7a):**
  - Multi-tier subscription model foundation (Free, Pro, Enterprise) supporting per-organization tenant isolation.
  - Shadow and live usage metering for active seats, monthly post volume, and storage consumption.
  - Atomic storage reservation and quota management prior to media file uploads.
  - Durable subscription action limit policies and feature gating decorators.
  - Organization administrator billing settings interface (`BillingSettings.tsx`) with real-time tier comparisons and billing history.
  - Complete subscription lifecycle management engine (upgrade, downgrade, cancellation, and scheduled renewal).
  - Philippine-centric PayMongo QRPh payment gateway integration, dynamic QRPh checkout generation, payment link dispatch, and webhook event handling.
  - Public subscription plan comparison matrix with interactive tier selection and checkout modal.
- **Multimodal AI Vision & Caption Generation (RP-03):**
  - Integrated Google Gemini Vision client with a pluggable provider abstraction for automatic image captioning.
  - Media-first automated suggestion generation triggered directly upon file upload.
  - Interactive AI suggestion drawer with one-click insertion and draft refinement.
  - Integrated AI quota tracking and task telemetry logging (`TaskTelemetry`).
- **Contributor Independence & Bulk Onboarding (RP-01, RP-02, RP-05):**
  - Strict binary submission gate separating blocking validation requirements from optional post-quality tips (REQ-124, Case-30a).
  - First-time contributor onboarding guidance with persistent next-action guidance cards and task telemetry (REQ-125, REQ-126, Case-30b).
  - Bulk contributor invitation modal supporting batch CSV/text upload, real-time validation preview, duplicate detection, and batch audit logging (REQ-127, Case-30d).
- **Mobile Operability Certification (RP-04):**
  - Full mobile operability and responsive verification down to 360px viewport across drafting, submission gate, review queue, and resolution flows.
- **Automated CI Validation & E2E Test Suites:**
  - Added GitHub Actions continuous integration workflow for dev branch pull requests (`dev-branch-validation.yml`).
  - Implemented Playwright E2E test suites covering Case-30a (Binary Submission Gate), Case-30b (Contributor Independence), Case-30d (Bulk Invitations), and PayMongo QRPh billing checkout flows.
  - Expanded JUnit and MockMvc integration tests across subscription, billing, and task telemetry services.

---

## Pull Requests Included in this Release

| PR | Hash | Scope | Description | Author |
|---|---|---|---|---|
| **#439** | `4b745b5d` | `feat/system-health` | Track AI quotas and redesign UI for mobile responsiveness | Richemmae V. Bigno |
| **#438** | `8353bd64` | `ui/submission` | Redesign submission status guidance callout and concise badge copy | Richemmae V. Bigno |
| **#437** | `35cac46b` | `test/rp04` | RP-04 mobile operability tests and staged media completeness check | Richemmae V. Bigno |
| **#435** | `266d7234` | `feat/subscription` | Add subscription plan comparison and checkout launcher | Chris Daniel Palacio Cabataña |
| **#432** | `d46b69f9` | `fix/migration` | Repair backend migration startup and media job type enums | Chris Daniel Palacio Cabataña |
| **#431** | `b5baf1dd` | `ci` | Validate dev branch changes with automated CI workflow | Chris Daniel Palacio Cabataña |
| **#430** | `7c30946c` | `test/paymongo` | PayMongo QRPh payment links and billing validation hardening | Chris Daniel Palacio Cabataña |
| **#429** | `6e27ce20` | `fix/cleanup` | Clean compiler warnings, React hooks, and unescaped entities | Chris Daniel Palacio Cabataña |
| **#428** | `c3cb8b8e` | `fix/flyway` | Resolve duplicate Flyway V127 migration script collision | Chris Daniel Palacio Cabataña |
| **#427** | `d0df71f0` | `feat/subscription` | Phase 7A: Add PayMongo QRPh-assisted subscription billing | Chris Daniel Palacio Cabataña |
| **#425** | `f3270be0` | `test/std` | Synchronize Software Test Document (STD) and test suite adjustments | Richemmae V. Bigno |
| **#424** | `9b0a3844` | `feat/subscription` | Phase 6: Add subscription lifecycle operations (renew, cancel, upgrade) | Chris Daniel Palacio Cabataña |
| **#423** | `7c037a31` | `docs` | Establish MVP validation and refactoring documentation baseline | Richemmae V. Bigno |
| **#422** | `20587d4c` | `feat/subscription` | Phase 5: Add administrator billing settings UI and plan cards | Chris Daniel Palacio Cabataña |
| **#421** | `5bafd62f` | `refactor/rp05` | Bulk invitations with preview, batch audit, AI caption on upload, and mobile operability | Richemmae V. Bigno |
| **#420** | `fbe5d284` | `feat/subscription` | Phase 4: Enforce durable subscription entitlements and action limits | Chris Daniel Palacio Cabataña |
| **#419** | `1dce9e69` | `feat/subscription` | Phase 3: Add atomic storage quota reservations and upload limits | Chris Daniel Palacio Cabataña |
| **#418** | `3afb484a` | `feat/subscription` | Phase 2: Add subscription usage metering and shadow metrics | Chris Daniel Palacio Cabataña |
| **#415** | `6ec89704` | `refactor/rp02` | First-time independence next-action guidance, plain-language status copy, and task telemetry | Richemmae V. Bigno |
| **#414** | `cc0254a2` | `feat/subscription` | Phase 1: Subscription organization and multi-tier plan foundation | Chris Daniel Palacio Cabataña |
| **#413** | `4d328b92` | `refactor/rp01` | Separate binary submission gate from optional post-quality tips | Richemmae V. Bigno |
| **#411** | `d0e8a697` | `docs/skill` | Add production release manager skill and Keep a Changelog standards | Richemmae V. Bigno |

---

## [3.0.2] - 2026-10-17

### Security
- **CSV Formula Injection Mitigation (CWE-1236):** Neutralized potential command execution in `SystemHealthService` administrative CSV export by sanitizing cells beginning with formula trigger characters (`=`, `+`, `-`, `@`, `\t`, `\r`) with a leading apostrophe (`'`).
- **Admin Ownership Transfer Guard:** Hardened role check logic on administrative transfer to allow transfers between existing active administrators without triggering 409 conflict errors (INC-01).

### Fixed
- **Background Escalation Transaction Boundaries:** Extracted state transitions into `PublishingStateService` with Spring `@Transactional` proxy boundaries to eliminate risk of partial commits during batch execution (INC-02).
- **Frontend Hook Dependencies & Entity Escaping:** Resolved React `exhaustive-deps` warnings, unescaped HTML characters, and accessibility attribute mismatches across 20+ feature screens (`SubmissionScreen`, `FancyTextTool`, `CircularGallery`, `FolderFloat`, `PublishingTrendChart`, `ValidationQueueScreen`, `UserManagementScreen`).

### Changed
- **Session Re-Authentication Governance (By Design):** Formally aligned session warning behavior so that clicking "Stay Logged In" requires explicit password re-authentication via the session modal (`POST /api/v1/auth/login`) rather than silent token renewal, maintaining a strict security posture for sensitive DOST DASIG administrative actions (INC-03).
- **Evaluator Reference Documentation:** Updated root `README.md` to reference team code `2627-sem1-it411-38` and refreshed deployment links.

### Removed
- **Development Demo Bar:** Removed `DemoBar.tsx` and legacy demo mock output from production client bundle to ensure only production UI code is served.

### Added
- **Full Backend Controller Test Suite:** Implemented unit and MockMvc integration tests across 11 core controllers:
  - `AiTemplateControllerTest`
  - `EngagementRecommendationControllerTest`
  - `FacebookOAuthCallbackControllerTest`
  - `GuardRailControllerTest`
  - `HealthControllerTest`
  - `MediaProcessingAdminControllerTest`
  - `MessengerConnectionControllerTest`
  - `PageSettingsControllerTest`
  - `PostTemplateControllerTest`
  - `ProofreadControllerTest`
  - `WatermarkConfigurationControllerTest`
  - `SystemHealthServiceTest` (CSV sanitization and metric rollup verification)
- **Living Requirements & STD Sync:** Synchronized living SRS use cases (UC-1.1 through UC-3.6) and Software Test Document incident log with verified test runs.

---

## Pull Requests Included in this Release

| PR | Hash | Scope | Description | Author |
|---|---|---|---|---|
| **#401** | `5c4d8852` | `test` | Align admin transfer authorization coverage | Chris Daniel Cabataña |
| **#402** | `247522c6` | `refactor` | Resolve frontend lint violations and hook dependencies | Chris Daniel Cabataña |
| **#403** | `b4fea7e8` | `chore` | Strip stale `DemoBar.tsx` and dead demo output | Chris Daniel Cabataña |
| **#404** | `47c317f9` | `test` | Cover security-sensitive settings controllers | Chris Daniel Cabataña |
| **#405** | `9dfd0cb0` | `test` | Cover integration and recommendation controllers | Chris Daniel Cabataña |
| **#406** | `56a872c7` | `test` | Complete AI controller test coverage | Chris Daniel Cabataña |
| **#407** | `f7bbad84` | `docs` | Add MVP refactoring evidence documentation | Chris Daniel Cabataña |
| **#408** | `fc916d76` | `chore` | Consolidate documentation structure and retain deliverables | Chris Daniel Cabataña |
| **#409** | `31dab025` | `fix/security` | Neutralize CSV formula injection in SystemHealthService and update README | Richemmae V. Bigno |

---

## [3.0.1] - 2026-10-16
- Initial deployed MVP release for capstone validation and evaluator demonstration.
