* **M1's Critical Dependency:** The most important prerequisite is M1's creation of the **Database Schema** (foundational tables: users, institutions, submissions, etc.). Other back-end developers (M2 and M4) need these tables defined before they can implement their respective repositories and services (UserRepository, InstitutionService, etc.).[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)  
  * **Action for M1:** M1 (Lerah) should prioritize creating and committing the initial table structure first. Once the schema is committed, M2 and M4 can start immediately.[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)  
* **Parallel Back-end Work (M1, M2, M4):**  
  * M1 can implement the JWTService, AuditLogService, and EmailService (core services) *after* M2 and M4 have started, as these services are cross-cutting and are only **called** by the other members, not modified by them.[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)  
  * M2 (Auth) and M4 (Institution/Scheduling) are working on isolated services and controllers, minimizing file conflicts.[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)  
* **Parallel Front-end Work (M3, M5):**  
  * The front-end members, M3 (Core Auth UI) and M5 (Submission UI), can start implementing their components and setting up the general UI structure (LoginPage, SubmissionFormPage) immediately.[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)  
  * They only need the API contracts defined by the back-end (M2 and M4) to begin integrating the final data fetching. Setting up the AuthContext (M3) can begin without M2 being finished, as the token management logic is largely self-contained.[1](https://docs.google.com/document/d/1bgc2sRNhwVR51qEV6VCl3B-Mm960l6gDUcVk-2BhbeQ/edit)

Module 1 is composed of:

* **UC-1.1:** User Provisioning, Onboarding, and Authentication  
* **UC-1.2:** Institution Onboarding and Workspace Provisioning  
* **UC-1.3:** Content Submission and Self-Service Scheduling

| Member Role | Component Focus | Specific Deliverables | Status |
| ----- | ----- | ----- | ----- |
| **M1 (Lerah)** | **Core Data & Infrastructure Setup** (Back-end) | \*   **Database Schema:** Create all foundational tables: users, institutions, invitation\_tokens, account\_lockouts, submissions, slot\_reservations, and RLS policies (UC-1.1, 1.2, 1.3).  \*   **Core Services:** Implement JWTService, AuditLogService, and EmailService (shared across all UCs). | Completed |
| **M2 (Chris)** | **UC-1.1 Back-end Services** | \*   Implement **Auth controllers:** AuthController, InvitationController, PasswordController.  \*   Implement persistence layers: UserRepository, InvitationTokenRepository, AccountLockoutService. | Completed |
| **M3 (Anton)** | **UC-1.1 Front-end & Core Auth** | \*   Implement **Auth Front-end:** LoginPage, LoginForm, InvitationAcceptPage, ForgotPasswordPage.  \*   Set up **Global State:** Implement AuthContext and ProtectedRoute to manage token state and enforce role-based access. | Completed |
| **M4 (Chim)** | **UC-1.2 & UC-1.3 Complex Back-end Logic** | \*   **UC-1.2:** Implement InstitutionController, InstitutionService, and WorkspaceProvisionerService (focus on RLS setup and provisioning logic).  \*   **UC-1.3 Scheduling:** Implement complex logic for GuardRailService and SlotReservationService. | Completed |
| **M5 (Jay)** | **UC-1.3 Content Submission UI** (Front-end) | \*   Implement the primary **SubmissionFormPage** UI container and associated components: MediaUploadZone, SlotPicker, GuardRailStatusPanel, and SubmissionQueue.  \*   Implement basic CRUD services for SubmissionController and SubmissionService (handling DRAFT state and integrating with M4's scheduling results). The frontend expects these endpoints: GET /api/v1/submissions GET /api/v1/submissions/{id} POST /api/v1/submissions/drafts PATCH /api/v1/submissions/{id} DELETE /api/v1/submissions/{id} POST /api/v1/submissions/{id}/submit POST /api/v1/submissions/{id}/media GET /api/v1/submissions/lookups POST /api/v1/guardrails/validate  | In Progress |

**✅ ALREADY DONE**

1. ✅ Contributor submission page converted & wired The submission page lives at /submissions/new and the dashboard "Submit Content" button routes there. UI is complete — backend endpoints still needed to save/load real data.

2. ✅ Invitation flow backend implemented Backend creates a pending user on invite, blocks re-inviting active users, uses correct React route /invite?token=…, and handles SMTP failures with pending\_email\_undelivered status instead of hiding the error.

3. ✅ Dashboard shows SMTP failure \+ fallback invite link When email delivery fails, the dashboard shows a visible warning and the raw invite link so an admin can share it manually without needing to resend.

4. ✅ Mail config moved to .env SMTP settings (MAIL\_HOST, MAIL\_USERNAME, APP\_FRONTEND\_BASE\_URL, etc.) are now read from environment variables instead of being hardcoded, making it safe to commit application.properties.

---

**🔧 STILL NEEDED — BACKEND**

5. ☐ Configure real SMTP credentials in backend/.env Add working SMTP values so email delivery works in production. Use Office 365 if your school allows SMTP AUTH, otherwise switch to a transactional provider like SendGrid, Mailgun, or Resend.

6. ☐ Create backend/.env.example A template file listing all required env vars with blank/placeholder values so teammates know what to set up locally without exposing real credentials. This gets committed to git; the real .env stays gitignored.

7. ☐ Build UserController \+ UserService \+ UserDto Needed to list, view, and manage users per institution. UserDto returns safe user info (no password hashes). UserService centralizes lifecycle logic: pending → active → inactive, and resend invite. UserController exposes the HTTP endpoints.

8. ☐ Add POST /api/v1/invitations/{id}/resend endpoint Allows admins or validators to resend an invitation when SMTP failed or the user lost the original email. Pairs with the dashboard's "delivery failed" warning — the UI needs somewhere to send the retry request.

9. ☐ Add GET /api/v1/users?institutionId=… endpoint The dashboard currently shows static counts for contributors and validators. This endpoint lets the frontend fetch real user lists filtered by institution so the dashboard reflects actual data.

10. ☐ Add GET /api/v1/me endpoint The frontend currently derives the logged-in user's name and institution from email or localStorage — which is fragile. A /me endpoint returns the actual user profile from the database so the frontend has reliable, up-to-date identity data.

---

**🖥️ STILL NEEDED — FRONTEND**

11. ☐ Wire submission page to backend endpoints (when ready) Once the backend submission endpoints are built, connect the frontend form at /submissions/new to actually save and load real submissions. UI is complete and waiting.

Strategy to Prevent Conflicts

1. **Separate Feature Branches:** All 5 members must work exclusively within dedicated feature branches (e.g., feature/M2-auth-backend, feature/M5-submission-ui) that are branched from the main branch. Merge to main only after passing code review and local testing.  
2. **Clear Ownership of Schema (M1):** M1 should define and commit the initial structure of all tables for Module 1 before other members implement their repositories. Changes to core schema after M1's initial commit must be coordinated with a migration script.  
3. **Service Isolation:** The assignments prioritize isolated services. For example, M2 works only on authentication, while M4 works only on institution and scheduling logic. They interact via services, minimizing controller/logic file conflicts.  
4. **Shared Service Usage:** Services like AuditLogService (M1) are cross-cutting. Other members should **only call** the interface methods defined by M1 and should not modify M1's service implementation files.  
5. **Front-end/Back-end Separation (M3 vs M2, M5 vs M4):** Assigning dedicated front-end and back-end tasks drastically reduces conflicts, as they are modifying different file directories (src/main/java vs. src/main/react).

The DASIGConnect project follows a standard three-tier client-server architecture. The project structure is organized primarily by technology stack and architectural tier, enforcing separation of concerns between the presentation (React), application (Spring Boot), and data (PostgreSQL) layers.

Here is the high-level and detailed project structure:1. High-Level Architecture (Three Tiers)

| Tier | Technology Stack | Core Responsibilities |
| ----- | ----- | ----- |
| **Presentation Tier (Front-end)** | React SPA, Tailwind CSS, Axios, EventSource (SSE) | Routing, Component rendering, Global state (AuthContext), API interaction, Real-time notifications. |
| **Application Tier (Back-end)** | Spring Boot, Spring Security, Spring Data JPA, jjwt | Business logic, State machine enforcement, Guard rail validation, External API orchestration, Tenant isolation via HandlerInterceptor. |
| **Data Tier (Database)** | PostgreSQL (Supabase), pgvector, RLS | Data persistence, Multi-tenant data isolation, Vector embeddings for AI search. |

### ***2\. Application Tier Structure (Spring Boot REST API)***

The Spring Boot back end is structured using standard conventions, organized by function (e.g., controllers, services, repositories) and logical domain (e.g., auth, submission, media, ai).

| Package | Key Components / Focus | Examples of Classes |
| ----- | ----- | ----- |
| **controller** | REST API endpoints, handling HTTP requests and DTO mapping. | AuthController, SubmissionController, MediaAssetController, CalendarController, ExceptionHandlingController. |
| **service** | Core business logic, transactional control, and orchestration. | SubmissionService, ValidationService, GuardRailService, SlotReservationService, NotificationService, PublishingSchedulerJob. |
| **repository** | Spring Data JPA interfaces for CRUD and custom JPQL/Native queries. | UserRepository, SubmissionRepository, MediaAssetRepository, NotificationRepository, OverrideRequestRepository. |
| **config** | Application configuration, security filter chain setup. | SecurityConfig (JWT filter chain, RBAC enforcement), TenantScopeInterceptor. |
| **external** | Clients for external service integrations. | FacebookPublisherService, ClaudeVisionClient, VoyageAIClient, EmailService (for JavaMailSender). |
| **model / entity** | JPA Entities representing database tables. | User, Submission, MediaAsset, Institution, ReviewLock. |
| **schedule** | Classes containing @Scheduled cron jobs. | StaleSubmissionDetectorJob, EmbeddingReconciliationJob, AbandonmentDetectorJob. |

### ***3\. Presentation Tier Structure (React SPA)***

The React front end is structured around distinct pages (routes) corresponding to user roles and core features, utilizing components and global state management.

| Grouping | Key Focus | Examples of Components / Files |
| ----- | ----- | ----- |
| **pages / routes** | Top-level containers defining the application's routes and workflow. | LoginPage, SubmissionFormPage, ValidationQueuePage, MediaLibraryPage, MasterCalendarPage, ResolutionCenterPage. |
| **components/ui** | Reusable, accessible, and theme-agnostic UI elements (using ShadCN/UI). | ShadCN/UI Dialog (Modals), Badge (Status Indicators), Tooltip. |
| **components/features** | Feature-specific, larger components. | MediaUploadZone, SlotPicker, GuardRailStatusPanel, MediaPreviewCarousel, CaptionSuggestionPanel. |
| **context / hooks** | Global state, authentication logic, and utility hooks. | AuthContext (stores JWT, role, institution\_id), useReducer, useAxiosInterceptor (for 401 handling). |
| **layout** | Global elements visible across the application. | GlobalHeader, SidebarNavigation, NotificationBell, ProtectedRoute. |
| **sse** | Infrastructure for handling real-time server events. | SSENotificationListener (opens persistent EventSource connection). |

### ***4\. Data Tier Structure (PostgreSQL)***

The database schema is comprehensive, ensuring multi-tenancy and supporting complex workflows like AI features and auditing.

| Feature | Tables / Components | Description |
| ----- | ----- | ----- |
| **Multi-Tenancy** | institutions table, RLS Policies | Data isolation enforced on all institution-scoped tables using Row-Level Security (RLS) policies. |
| **Core Workflow** | users, submissions, slot\_reservations | Records for accounts, content lifecycle, and scheduling conflict prevention. |
| **Media & AI** | media\_assets, asset\_tags, ai\_interaction\_log | Stores metadata, AI classification, Voyage AI embeddings (VECTOR(1024)), and tracks usage for KPIs. |
| **Audit & Log** | audit\_log, validation\_logs, publication\_attempts | Immutable records tracking all state-changing actions, validation decisions, and publishing retries. |

