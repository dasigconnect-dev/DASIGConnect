# 

## **CEBU INSTITUTE OF TECHNOLOGY**

**UNIVERSITY**

COLLEGE OF COMPUTER STUDIES

# 

# 

## 

## 

## 

# **Software Requirements Specifications**

## *for*

## DASIGConnect

# **Change History** {#change-history}

| Version | Date | Author(s) | Description of Changes |
| :---- | :---- | :---- | :---- |
| 1.0 | *05/02/2026* | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | Initial draft — scope, introduction, Module 1 use cases |
| 2.0 | 05/05/2026 | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | Added Module 2 use cases; expanded user characteristics |
| 3.0 | *5/09/2026* | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | Added Module 3 (AI features, Master Calendar, publishing); expanded NFRs |
| 4.0 | *5/12/2026* | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | Added tenant isolation section; revised guard rail taxonomy; expanded notification trigger matrix; incorporated adviser feedback |
| 5.0 | *5/16/2026* | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | resolved embedding pipeline specification; added automatic Step 2 publishing failure cleanup; added deployment platform (Vercel/Render); added Voyage AI as external dependency; added accessibility NFR; added data retention policy; added SUS acceptance threshold; clarified token key storage |
| 6.0 | *8/20/2026* | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno  | Updated requirements after implementation verification, including current role scopes, R2 media storage, publishing safeguards, analytics, approval workflow, and onboarding guides. |

# **Table of Contents** {#table-of-contents}

*[**Change History	2**](#change-history)*

[***Table of Contents	3***](#table-of-contents)

[*1\.  Introduction	7*](#heading=)

[*1.1.  Purpose	7*](#heading=)

[*1.2.  Scope	7*](#heading=)

[*1.3.  Definitions, Acronyms and Abbreviations	8*](#heading=)

[*1.4.  User Account State Machine	12*](#user-account-state-machine)

[*1.4.1.  State Transition Table	13*](#state-transition-table)

[*1.4.2.  Notes	14*](#notes)

[*1.5.  References	14*](#heading=)

[*2\.  Overall Description	16*](#heading=)

[*2.1.  Product perspective	16*](#heading=)

[*2.2.  User characteristics	16*](#heading=)

[*2.3.  Constraints	17*](#constraints)

[*2.4.  Assumptions and Dependencies	18*](#assumptions-and-dependencies)

[*2.5.  Design Philosophy and Operating Model	19*](#design-philosophy-and-operating-model)

[*3\.  Specific Requirements	20*](#heading=)

[*3.1.  External interface requirements	20*](#heading=)

[*3.1.1.  Hardware interfaces	20*](#heading=)

[*3.1.2.  Software interfaces	20*](#heading=)

[*3.1.3.  Communications interfaces	21*](#heading=)

[*3.2.  Functional requirements	22*](#heading=)

[*1\. Module 1: User Access & Content Creation	22*](#heading=)

[*1.1. Administrator Account Management	22*](#administrator-account-management)

[*1.1.1. Use Case Diagram	22*](#use-case-diagram)

[*1.1.2. Use Case Description	22*](#use-case-description)

[*1.1.3. Activity Diagram	25*](#activity-diagram)

[*1.1.4. Wireframe	25*](#wireframe)

[*1.2. Institution Management	26*](#institution-management)

[*1.2.1. Use Case Diagram	26*](#use-case-diagram-1)

[*1.2.2. Use Case Description	26*](#use-case-description-1)

[*1.2.3. Activity Diagram	27*](#activity-diagram-1)

[*1.2.4. Wireframe	27*](#wireframe-1)

[*1.3. Contributor Account Management	27*](#contributor-account-management)

[*1.3.1. Use Case Diagram	27*](#use-case-diagram-2)

[*1.3.2. Use Case Description	27*](#use-case-description-2)

[*1.3.3. Activity  Diagram	29*](#activity-diagram-2)

[*1.3.4. Wireframe	29*](#wireframe-2)

[1.4. Login & Session Management	29](#login-&-session-management)

[1.4.1. Use Case Diagram	29](#use-case-diagram-3)

[1.4.2. Use Case Description	29](#use-case-description-3)

[1.4.3. Activity  Diagram	30](#activity-diagram-3)

[1.4.4. Wireframe	30](#wireframe-3)

[1.5. Post Drafting & Templates	31](#post-drafting-&-templates)

[1.5.1. Use Case Diagram	31](#use-case-diagram-4)

[1.5.2. Use Case Description	31](#use-case-description-4)

[Post Template — Standard Baseline Set	33](#heading)

[1.5.3. Activity  Diagram	34](#activity-diagram-4)

[1.5.4. Wireframe	34](#wireframe-4)

[1.6. AI & Text Tools Integration	34](#ai-&-text-tools-integration)

[1.6.1. Use Case Diagram	34](#use-case-diagram-5)

[1.6.2. Use Case Description	34](#use-case-description-5)

[1.6.3. Activity  Diagram	36](#activity-diagram-5)

[1.6.4. Wireframe	36](#wireframe-5)

[1.7. Media Attachment Rules	36](#media-attachment-rules)

[1.7.1. Use Case Diagram	36](#use-case-diagram-6)

[1.7.2. Use Case Description	36](#use-case-description-6)

[1.7.3. Activity  Diagram	38](#activity-diagram-6)

[1.7.4. Wireframe	38](#wireframe-6)

[1.8. Engagement Helpers	38](#engagement-helpers)

[1.8.1. Use Case Diagram	38](#use-case-diagram-7)

[1.8.2. Use Case Description	38](#use-case-description-7)

[1.8.3. Activity  Diagram	39](#activity-diagram-7)

[1.8.4. Wireframe	39](#wireframe-7)

[1.9. Draft Submission	40](#draft-submission)

[1.9.1. Use Case Diagram	40](#use-case-diagram-8)

[1.9.2. Use Case Description	40](#use-case-description-8)

[1.9.3. Activity  Diagram	41](#activity-diagram-8)

[1.9.4. Wireframe	41](#wireframe-8)

[1.10. Moderator Account Management	41](#moderator-account-management)

[1.10.1. Use Case Diagram	41](#use-case-diagram-9)

[1.10.2. Use Case Description	41](#use-case-description-9)

[1.10.3. Activity  Diagram	43](#activity-diagram-9)

[1.10.4. Wireframe	43](#wireframe-9)

[*2\. Module 2: Media Library & Approval Pipeline	44*](#module-2:-media-library-&-approval-pipeline)

[*2.1. Library Uploads & Albums	44*](#library-uploads-&-albums)

[*2.1.1. Use Case Diagram	44*](#use-case-diagram-10)

[*2.1.2. Use Case Description	44*](#use-case-description-10)

[*2.1.3. Activity Diagram	46*](#activity-diagram-10)

[*2.1.4. Wireframe	46*](#wireframe-10)

[*2.2. Semantic Search & Filtering	46*](#semantic-search-&-filtering)

[*2.2.1. Use Case Diagram	46*](#use-case-diagram-11)

[*2.2.2. Use Case Description	46*](#use-case-description-11)

[*2.2.3. Activity Diagram	47*](#activity-diagram-11)

[*2.2.4. Wireframe	47*](#wireframe-11)

[*2.3. Media History Tracking	47*](#media-history-tracking)

[*2.3.1. Use Case Diagram	47*](#use-case-diagram-12)

[*2.3.2. Use Case Description	47*](#use-case-description-12)

[*2.3.3. Activity Diagram	48*](#activity-diagram-12)

[*2.3.4. Wireframe	48*](#wireframe-12)

[*2.4. Admin Approval Workflow	48*](#admin-approval-workflow)

[*2.4.1. Use Case Diagram	48*](#use-case-diagram-13)

[*2.4.2. Use Case Description	48*](#use-case-description-13)

[*2.4.3. Activity Diagram	50*](#activity-diagram-13)

[*2.4.4. Wireframe	50*](#wireframe-13)

[2.5. Automated Watermarking	51](#automated-watermarking)

[2.5.1. Use Case Diagram	51](#use-case-diagram-14)

[2.5.2. Use Case Description	51](#use-case-description-14)

[2.5.3. Activity Diagram	52](#activity-diagram-14)

[2.5.4. Wireframe	52](#wireframe-14)

[*3\. Module 3: Scheduling, Publishing and Analytics	53*](#module-3:-scheduling,-publishing-and-analytics)

[*3.1. Master Calendar Visibility	53*](#master-calendar-visibility)

[*3.1.1. Use Case Diagram	53*](#use-case-diagram-15)

[*3.1.2. Use Case Description	53*](#use-case-description-15)

[3.1.3. Activity Diagram	55](#activity-diagram-15)

[*3.1.4. Wireframe	55*](#wireframe-15)

[*3.2. Automated Facebook Post Publishing	55*](#automated-facebook-post-publishing)

[*3.2.1. Use Case Diagram	55*](#use-case-diagram-16)

[*3.2.2. Use Case Description	55*](#use-case-description-16)

[*3.2.3. Activity Diagram	57*](#activity-diagram-16)

[*3.2.4. Wireframe	57*](#wireframe-16)

[*3.3. Automated Reminders & Alerts	57*](#automated-reminders-&-alerts)

[*3.3.1. Use Case Diagram	57*](#use-case-diagram-17)

[*3.3.2. Use Case Description	57*](#use-case-description-17)

[*3.3.3. Activity Diagram	60*](#activity-diagram-17)

[*3.3.4. Wireframe	60*](#wireframe-17)

[*3.4. Social Engagement Analytics	60*](#social-engagement-analytics)

[*3.4.1. Use Case Diagram	60*](#use-case-diagram-18)

[*3.4.2. Use Case Description	60*](#use-case-description-18)

[*3.4.3. Activity Diagram	61*](#activity-diagram-18)

[*3.4.4. Wireframe	61*](#wireframe-18)

[*3.5. System Health Analytics	61*](#system-health-analytics)

[*3.5.1. Use Case Diagram	61*](#use-case-diagram-19)

[*3.5.2. Use Case Description	61*](#use-case-description-19)

[*3.5.3. Activity Diagram	63*](#activity-diagram-19)

[*3.5.4. Wireframe	63*](#wireframe-19)

[3.6. Audit Log Review	63](#audit-log-review)

[3.6.1. Use Case Diagram	63](#use-case-diagram-20)

[3.6.2. Use Case Description	63](#use-case-description-20)

[3.6.3. Activity Diagram	65](#activity-diagram-20)

[3.6.4. Wireframe	65](#wireframe-20)

[*3.3.  Business Rules and Guard Rails	66*](#heading=)

[*3.3.1.  Guard Rail Rules	66*](#guard-rail-rules)

[*3.3.1.1.  Hard Rules	66*](#hard-rules)

[*3.3.1.2.  Soft Rules	67*](#soft-rules)

[*3.3.1.3.  Trigger Rules	68*](#trigger-rules)

[*3.3.1.4.  Override & Administrative Action Audit Requirements	70*](#override-&-administrative-action-audit-requirements)

[*3.3.2.  Application Business Rules	70*](#application-business-rules)

[*3.3.2.1.  User Provisioning and Account Management	71*](#user-provisioning-and-account-management)

[*3.3.2.2.  Content Submission	71*](#content-submission)

[*3.3.2.3.  Media and Asset Management	72*](#media-and-asset-management)

[*3.3.2.4.  Content Approval	72*](#content-approval)

[*3.3.2.5.  Notification Behavior	73*](#notification-behavior)

[*3.3.2.6.  Analytics	73*](#analytics)

[*3.3.2.7.  Publishing	75*](#publishing)

[*3.3.2.8.  AI Features	76*](#ai-features)

[*3.4.  Non-functional requirements	78*](#heading=)

[*3.4.1.  Performance	78*](#heading=)

[*3.4.2.  Security	78*](#heading=)

[*3.4.3.  Reliability	79*](#heading=)

[*3.4.4.  Scalability	80*](#scalability)

[*3.4.5.  Maintainability and Auditability	80*](#maintainability-and-auditability)

[*3.4.6.  Data Retention	81*](#data-retention)

[*3.4.7.  Accessibility	81*](#accessibility)

[*3.4.8.  Usability	81*](#usability)

[*3.5.  Tenant Isolation (Multi-Institution Data Separation)	81*](#tenant-isolation-\(multi-institution-data-separation\))

[*3.5.1.  Core Principle	82*](#core-principle)

[*3.5.2.  Enforcement Mechanism (Defense in Depth)	82*](#enforcement-mechanism-\(defense-in-depth\))

[*3.5.3.  Visibility by Role and Feature	82*](#visibility-by-role-and-feature)

[*3.5.4.  Cross-Institution Access Attempts	83*](#cross-institution-access-attempts)

[*3.5.5.  Audit Logging of Administrator Cross-Institution Actions	83*](#audit-logging-of-administrator-cross-institution-actions)

[*3.5.6.  Tenant Isolation Testing	83*](#tenant-isolation-testing)

1. # **Introduction**

   1. ## ***Purpose***

The purpose of this document is to provide a complete and detailed specification of DASIGConnect — a centralized Social Media Content Workflow and Scheduling Management System designed for the DOST Acadême–Science and Innovation Group (DASIG) multi-institutional network. This Software Requirements Specification (SRS) defines all functional and non-functional requirements, user characteristics, system constraints, interface specifications, and external dependencies that govern the design, development, and testing of DASIGConnect.

This document also captures a critical design principle that distinguishes DASIGConnect from a workflow-digitization system: the system redistributes coordination work from the Administrator to a self-service model with software-enforced rules, directly addressing the administrative bandwidth bottleneck identified as the root cause of DASIG's current operational failures. This SRS serves as the authoritative reference for the development team, project advisers, and DASIG stakeholders throughout the project lifecycle.

2. ## ***Scope***

DASIGConnect is a web-based Social Media Content Workflow and Scheduling Management System scoped to support the social media content coordination operations of the DASIG network under DOST Region 7, specifically for its official Facebook page. The system replaces the current informal, fragmented coordination approach — which relies on manual communication between member institutions and a single overburdened Administrator — with a structured, role-based digital workflow in which Contributors at member institutions self-schedule their content within system-enforced rules, Moderators (network-wide, not institution-bound) triage and review the standard approval queue, and the Administrator retains final authority over exception handling, governance, and strategic configuration.

The system includes the following core functionalities:

* Multi-institution content submission with three-role access control (Contributor, Moderator, Administrator) — Moderator is a network-wide role with no institution binding, distinct from an earlier per-institution "Validator" draft  
* Self-service scheduling by Contributors with automated calendar queue balancing and conflict prevention, governed by a network-wide, admin-toggleable guard-rail ruleset (posting-hours window, daily cap, minimum spacing, minimum lead time)  
* A Fast-Track ("Live Event") submission path that bypasses scheduled-slot rules and publishes asynchronously immediately on approval, for time-sensitive coverage  
* Post templates (built-in and personal, save-as-template) mimicking Facebook's native composer UI for standardized content creation  
* On-demand AI-assisted caption generation from uploaded images using Anthropic Claude Vision, plus AI-assisted album/media matching (tag overlap \+ visual similarity via Voyage embeddings)  
* Fancy Unicode text styling tools for post captions and copy  
* Peak-hour posting recommendations based on historical Facebook engagement data  
* Mandatory media attachment enforcement, requiring Contributors to attach media from device upload or the media library before a draft can be submitted  
* Structured draft submission handoff from the Contributor to the network-wide Moderator/Administrator review queue  
* Centralized digital media repository with nested album organization, a shared network library, and per-institution isolation enforced via row-level security  
* Media history and asset lifecycle tracking, tiered deletion rules, a 30-day internal purge buffer, and exact-content-hash duplicate detection across the repository  
* AI-powered image classification and Voyage AI vector embeddings (voyage-4-lite, 1024-dimension) running as a background enrichment pipeline to power semantic media search and album matching  
* Moderator/Administrator approval workflow with revision remarks, edit-during-review diff tracking, and reviewer locking  
* Automated brand watermarking applied to approved post media at publish time, network-wide configurable  
* Master calendar with role-differentiated visibility and editing (read-only-by-policy caps for Moderators — capped reschedule count and window, hard guard-rail blocks require an Administrator; unrestricted drag-and-drop for the Administrator)  
* Automated Facebook post publishing via Graph API — image-only submissions use the verified two-step method (/{PAGE\_ID}/photos → /{PAGE\_ID}/feed), video-only submissions use the single-call method (/{PAGE\_ID}/videos), and publishing is claimed atomically before any API call to prevent double-publishing  
* System and submission lifecycle notification system covering 29 trigger events across state changes, approvals, failures, token health alerts, and empty-schedule warnings — delivered via in-app SSE, email, and (for a subset of events) Facebook Messenger  
* Social media and system analytics dashboard tracking posting metrics, page engagement, API token health, and cloud storage capacity, role-scoped with CSV export  
* An admin-only, immutable Audit Log — filterable, paginated, structured before/after diff view, CSV export, with a database-enforced append-only guarantee (no update/delete path exists anywhere in the system, not just absent from the UI)  
* Administrator exception-handling tools for failed publications, manual publishing fallback, emergency direct posting, and Facebook token management/re-authentication  
* An Administrator-only System Health dashboard: database/object-storage capacity, external API status, background job health, and operational metrics (publish success rate, Edit & Approve rate, missed-review rate, and more)
* Optional, contextual, view-only onboarding guides for the submission list, composer, review queue, review decisions, failed publishing recovery, and moderator editing workflows. Guide preferences and dismissed screens are stored per user account and can be disabled or reset.

The system does not include:

* Integration with social media platforms other than the DASIG official Facebook page  
* Generative AI image creation or automated content moderation  
* Autonomous publishing decisions — all Contributor submissions require Moderator/Administrator approval prior to publication. Administrator direct posts bypass the queue by design, require written justification, and are recorded in the audit log  
* Automated publishing for mixed-media submissions (images \+ video in a single post), which follow the Manual Publishing Fallback workflow (not limited to "the pilot period" — this is a permanent, structural Graph API constraint, not a temporary pilot limitation)  
* Contributor-facing AI classification suggestion popups — image tagging runs as a background process for media organization and search  
* Public post visibility via the Facebook Graph API during development, which remains subject to Meta Business Verification for Live mode

  3. ## ***Definitions, Acronyms and Abbreviations***

**ACTIVE (Account State)** — A user account state indicating that the user has completed the invitation token activation flow, set their permanent password, and confirmed their institution membership (for Contributors). An ACTIVE account may authenticate and access DASIGConnect according to its assigned role and tenant scope. ACTIVE is the normal operating state for all user accounts. Contrast with PENDING (token issued, activation not yet completed), EXPIRED (token expired before activation), INACTIVE (account deactivated by an Administrator), and PENDING\_EMAIL\_UNDELIVERED (invitation email not delivered).

**ACTIVE (Institution Status)** — An institution status indicating the workspace is fully operational: Contributors may be invited and may submit content, and automated publishing is enabled for the institution. Institutions are created directly in ACTIVE status by an Administrator. The institution may transition to INACTIVE if an Administrator deactivates it.

**Administrator** — A user role assigned to DASIG administrators operating under DOST Region 7\. Administrators have network-wide authority. Routine content review and approval is performed by Moderators and Administrators; Administrators also handle exception cases such as failed publishes, institution and account management, and governance. Any Administrator may create content in a Contributor capacity. One active Administrator may hold the Admin Owner designation, which is a governance flag rather than a separate role.

**AI** — Artificial Intelligence: The simulation of human intelligence in computer systems, used in DASIGConnect for caption generation, image classification, media recommendation, and album auto-matching.

**Album** — An event-based grouping of media assets within an institution's media library, assigned either automatically (Auto-Match, based on combined tag and visual similarity) or manually by the actor at the point of upload or during submission organization.

**API** — Application Programming Interface: A set of protocols that allows the system to communicate with external services, including the Facebook Graph API, the Anthropic Claude Vision API, and the Voyage AI API.

**CIT-U** — Cebu Institute of Technology – University: One of the primary DASIG member institutions.

**Contributor** — A user role assigned to faculty, student organization officers, and institutional communications officers at DASIG member institutions. Contributors create content, schedule it within system-enforced rules, and submit it for Administrator approval. Each Contributor operates within their institution's isolated workspace and can only view and manage their own submissions.

**Moderator** — A network-wide reviewer role with no institution binding. Moderators review submissions from all institutions and may perform permitted contributor-account actions, but do not have Administrator-only governance, system-health, audit-log, token-management, or unrestricted institution-management privileges.

**CRUD** — Create, Read, Update, Delete: The four basic operations of persistent data storage.

**DASIG** — DOST Acadême–Science and Innovation Group: The multi-institutional academic network under DOST Region 7 for which DASIGConnect is developed.

**DASIG Central Visayas** — A protected default institution, seeded during initial system deployment, used to attribute content published on the network's behalf rather than on behalf of a specific member institution. Cannot be deactivated through the standard institution management flow.

**DOST** — Department of Science and Technology: The Philippine government agency overseeing DASIG and its member institutions.

**DRAFT** — A submission state indicating a saved but unsubmitted post; visible only to the Contributor who created it (or the Administrator, if authored in a Contributor capacity). A submission enters DRAFT when the actor clicks Save as Draft. It transitions to PENDING\_APPROVAL upon successful submission, or remains in DRAFT indefinitely until submitted or discarded. If a DRAFT submission with a reserved slot is inactive for 7 days, the slot reservation is automatically released by GR-T2 while the DRAFT record is retained.

**Edit & Approve** — An Administrator action available during content review that combines a direct correction to any field of a submission with approval in the same action, as an alternative to sending content back to the Contributor via Request Revision.

**EXPIRED (Account State)** — A user account sub-state of PENDING, indicating that the Invitation Token issued during provisioning was not used within its 72-hour validity window. The account record is retained in the system; login is not possible. The account transitions back to PENDING when an Administrator reissues a new Invitation Token (the previous token is invalidated).

**Facebook Graph API** — Meta's developer API for programmatic interaction with Facebook Pages, used in DASIGConnect for automated post publishing via the verified two-step photo publishing method (image submissions) and the single-call video publishing method (video submissions).

**Guard Rail** — A system-enforced business rule that automatically prevents, warns about, or triggers autonomous action based on predefined criteria, replacing the need for human coordination. Guard rails are classified as Hard (system blocks the action), Soft (system warns; action proceeds), or Trigger (system acts autonomously). The full guard rail taxonomy is defined in Section 3.3.

**HEI** — Higher Education Institution: An academic institution such as CIT-U or Silliman University participating in the DASIG network.

**HTTPS** — Hypertext Transfer Protocol Secure: The encrypted communication protocol used for all client-server communication in DASIGConnect. Unencrypted HTTP connections are not supported.

**INACTIVE (Account State)** — A user account state indicating that an Administrator has deactivated the account via the account management panel. An INACTIVE account cannot authenticate, and all active JWT sessions are immediately revoked upon deactivation. The account's submission history, media assets, and audit records are retained. An INACTIVE account may be reactivated by an Administrator at any time.

**IN\_REVIEW** — A submission state indicating that an Administrator has clicked Review on the submission and is actively reviewing it. A review lock is acquired upon entry into this state, scoped to that Administrator for 15 minutes, preventing concurrent actioning by another Administrator. If the reviewing Administrator closes the Submission Detail panel without taking an action, the submission automatically reverts from IN\_REVIEW back to PENDING\_APPROVAL after the 15-minute lock timeout, releasing the lock. This timeout is distinct from the Approaching Publish Time Priority Flag (see GR-T1), which raises a visual priority indicator when a submission's scheduled publication time is within 30 minutes, regardless of submission state.

**JWT** — JSON Web Token: A compact, URL-safe token format used for secure user authentication and session management. JWTs in DASIGConnect carry a signed payload including the user's identity, role, institution\_id where applicable, and Admin Owner designation where applicable. The inactivity timeout is 8 hours, resetting on every authenticated API call. See UC-1.4 A7 for session expiry handling behavior.

**KPI** — Key Performance Indicator: A measurable metric used to evaluate system performance, including average posting delay, content completeness rate, and total posts published per institution. KPI targets and calculation formulas are defined in BR-ANA-01.

**Live Event Fast-Track** — A submission mode, selected during drafting, that bypasses standard scheduling and enters the Approval Queue with elevated priority; upon Administrator approval, the submission publishes immediately rather than at a scheduled time.

**Master Calendar** — A network-wide visual calendar showing all slot reservations and publication states across all DASIG member institutions, color-coded by institution. Visible in read-only mode to Contributors (network-wide slot timing, full detail limited to their own institution and DASIG Central Visayas); fully editable by the Administrator. The calendar renders submission cards for all scheduled states: PENDING\_APPROVAL (tentatively reserved), SCHEDULED, PUBLISHED, PUBLISHED\_MANUAL, and PUBLISH\_FAILED.

**MVP** — Minimum Viable Product: The initial functional version of DASIGConnect that includes all core features required for pilot deployment.

**NEEDS\_REVISION** — A submission state indicating an Administrator has requested changes from the Contributor via the Request Revision action. Revision remarks are mandatory (minimum 10 characters, maximum 1,000 characters). The slot reservation is released when the submission enters NEEDS\_REVISION. The Contributor must address the remarks, select a new time slot, and resubmit.

**PENDING\_APPROVAL** — A submission state indicating the Contributor has submitted the content and it is awaiting Administrator review. The time slot reservation is held while the submission is in this state (not applicable to Live Event Fast-Track submissions). A submission enters PENDING\_APPROVAL from DRAFT upon successful submission, and exits to IN\_REVIEW, NEEDS\_REVISION, REJECTED, or SCHEDULED. If the scheduled publication time falls within 30 minutes while the submission remains in PENDING\_APPROVAL or IN\_REVIEW state, the system raises a priority flag in the Approval Queue (GR-T1).

**PENDING — EMAIL UNDELIVERED (Account State)** — A user account sub-state indicating that the account was created and an Invitation Token was generated, but the automated invitation email failed to reach the recipient (bounced address, mail server rejection, or delivery timeout). The account cannot be activated until the invitation email is successfully delivered. The Administrator is alerted in the account management panel and may verify the email address and trigger a manual resend. If the email address is confirmed incorrect, the Administrator may update the address and reissue the Invitation Token, which invalidates the previous token and dispatches a new activation email to the corrected address.

**PUBLISH\_FAILED** — A submission state indicating that automated publishing via the Facebook Graph API failed after all retry attempts (3 retries with exponential backoff per GR-T5), or that the submission missed the cron scheduler's processing window (GR-T9). Triggers Administrator in-app, email, and (where configured) Messenger notification (T-06) and becomes available for manual recovery via the Approval Queue's Failed tab (UC-2.4 A8). The submission remains in PUBLISH\_FAILED until the Administrator retries with a new schedule or manually publishes (PUBLISHED\_MANUAL).

**PUBLISHED** — A terminal submission state indicating the content was successfully posted to the DASIG Facebook Page via automated publishing through the Facebook Graph API. The platform post ID is stored in the database upon transition to this state. The Master Calendar card changes to the published color and the Contributor receives an in-app notification (T-04). This state is distinct from PUBLISHED\_MANUAL (Administrator-executed manual publication).

**PUBLISHED\_MANUAL** — A terminal submission state indicating the Administrator manually published the content on the DASIG Facebook Page after automated publishing failed, via the Approval Queue's Failed tab (UC-2.4 A8). The manual publication timestamp and platform post URL are recorded by the Administrator. The Contributor receives an in-app notification (T-05).

**RBAC** — Role-Based Access Control: A security model in which system access is granted based on the user's assigned role (Contributor, Moderator, or Administrator). The Admin Owner is a governance designation held by exactly one active Administrator at a time, not a separate role. All API endpoints enforce RBAC server-side; requests that do not match the required role return a 403 Forbidden response.

**RDBMS** — Relational Database Management System: The database system used to store user accounts, submissions, media metadata, and scheduling records. DASIGConnect uses PostgreSQL hosted on Supabase.

**REST** — Representational State Transfer: An architectural style for the system's back-end API, using standard HTTP methods (GET, POST, PUT, PATCH, DELETE) for communication. API responses are formatted in JSON.

**RLS** — Row-Level Security: A PostgreSQL feature that enforces per-institution data isolation at the database level. RLS policies ensure that even if an application-layer query omits the tenant filter, the database itself returns zero rows for cross-institution data access. See the Tenant Isolation section for the full four-layer enforcement model.

**SCHEDULED** — A submission state indicating the post has been approved by an Administrator and assigned a permanent publication time on the Master Calendar; awaiting automated publishing by the cron scheduler. The slot reservation becomes permanent upon transition to SCHEDULED. A submission in SCHEDULED state cannot be actioned by Contributors; only the Administrator may reschedule it (UC-3.1 A2 or UC-2.4 A9, with guard rail re-validation and audit). The cron scheduler processes SCHEDULED submissions within a ±5 minute window of the scheduled time (UC-3.2).

**Self-Service Scheduling** — The design principle in which Contributors directly schedule their own posts within system-enforced rules (guard rails), rather than requesting the Administrator to schedule on their behalf. This principle is central to DASIGConnect's bandwidth-bottleneck reduction goal.

**Session Warning Banner** — A non-blocking, dismissible banner displayed at the top of the application interface when the user's JWT session is within 15 minutes of expiry due to inactivity. Clicking the banner triggers a silent token refresh that issues a new 8-hour JWT without requiring the user to re-enter credentials or leave the current page. If the user does not interact with the banner and the session expires, the system proceeds to the session-expired recovery behavior defined in UC-1.4 A7.

**Slot Reservation** — A tentative hold on a calendar time slot when a Contributor selects a publication time during content submission (not applicable to Live Event Fast-Track submissions). The reservation persists through DRAFT and `PENDING_APPROVAL` states, becomes permanent on Administrator approval (SCHEDULED state), and is released when: the Administrator requests revision (`NEEDS_REVISION`), the Administrator rejects the submission (`REJECTED`), the submission's scheduled time passes without review (`MISSED_REVIEW`, GR-T9), the Administrator reschedules the post (UC-3.1 A2), the Contributor's DRAFT is inactive for 7 days (GR-T2), or a slot conflict is detected at submit time (UC-1.9 A4). 

**SRS** — Software Requirements Specification: This document, defining all requirements for DASIGConnect.

**SSE** — Server-Sent Events: A technology used for delivering real-time in-app notifications from the server to the client without requiring a full WebSocket connection.

**Admin Owner** — A governance designation held by one active Administrator. The Admin Owner is required for selected actions targeting existing Administrator accounts, connecting a different Facebook Page, and initiating an Admin Owner transfer. Ownership can be transferred through a request-and-confirm flow to an active Administrator or Moderator; it does not create a separate account role.

**SUS** — System Usability Scale: A validated, technology-independent questionnaire for measuring the perceived usability of interactive systems (Brooke, 1996). DASIGConnect targets a minimum SUS score of ≥68 (the "above average" threshold per Bangor, Kortum, and Miller, 2009), as defined in the Usability section.

**Two-Step Publishing** — The verified Facebook Graph API publishing method used by DASIGConnect for image submissions: (1) POST to /{PAGE\_ID}/photos with published=false to stage the photo and obtain a photo\_id without immediately posting; (2) POST to /{PAGE\_ID}/feed with the approved caption and attached\_media referencing the photo\_id, producing a proper feed post. For submissions with multiple images, each image is staged in a separate Step 1 call before a single Step 2 feed post references all photo\_id values. This method applies to image-only submissions. Video submissions use a separate single-call method via POST /{PAGE\_ID}/videos.

**UAT** — User Acceptance Testing: A phase of testing in which designated DASIG users validate that the system meets their operational needs.

**UI** — User Interface: The visual interface through which users interact with DASIGConnect.

**Watermark** — A configurable brand overlay, composed of up to three canvas elements (image, text, shape), automatically applied to approved photo content at the point of publication, scaled proportionally to each photo's dimensions. Configurable at the network level or per institution, with a per-asset opt-out available during drafting. This table complements the submission state machine defined in the functional requirements (Section 3.2).

4. ## ***User Account State Machine*** {#user-account-state-machine}

The following table defines all valid states for a DASIGConnect user account, the transitions between them, the triggering event, and the responsible service. Any transition not listed below is invalid and must be rejected by the application layer with a 409 Conflict response for explicit state transition API calls.

This table complements the submission state machine defined in the functional requirements (Section 3.2).

1. ### ***State Transition Table*** {#state-transition-table}

| From State | To State | Triggering Event |
| :---- | :---- | ----- |
| *(none — account does not exist)* | PENDING | An Administrator creates an Administrator or Contributor account record via the account management panel. The system generates an Invitation Token and dispatches an activation email.  |
| PENDING | PENDING — EMAIL UNDELIVERED | Automated invitation email dispatch fails after all delivery retries — bounced address, mail server rejection, or delivery timeout. The token remains valid but unreachable.  |
| PENDING — EMAIL UNDELIVERED | PENDING | Administrator verifies the email address and triggers a manual resend from the account management panel. Delivery is confirmed by the email provider. A new token is generated; the previous token is invalidated.  |
| PENDING — EMAIL UNDELIVERED | EXPIRED | The Invitation Token expiry timestamp is crossed while the account is in PENDING — EMAIL UNDELIVERED state (i.e., the Administrator did not correct and resend the email within 72 hours of account creation). The account transitions to EXPIRED. Subsequent resolution requires the Administrator to correct the email address and reissue a new token, which follows the EXPIRED → PENDING path.  |
| PENDING | ACTIVE | User clicks the activation link in the invitation email, sets their permanent password meeting all complexity requirements, and account activation is confirmed (UC-1.1). Row-Level Security binding to the user's institution\_id is established.  |
| PENDING | EXPIRED | The Invitation Token expiry timestamp is crossed without the user completing activation. The account record is retained with EXPIRED status. Login is not possible. The Administrator may reissue a new token, which transitions the account back to PENDING.  |
| EXPIRED | PENDING | Administrator reissues a new Invitation Token from the account management panel (UC-1.1 A4). The previous token is explicitly invalidated before the new token is generated and dispatched.  |
| EXPIRED | INACTIVE | Administrator explicitly deactivates an EXPIRED account via the account management panel (e.g., to archive an account that will never be activated). The account is removed from the active provisioning queue.  |
| ACTIVE | INACTIVE | Administrator deactivates the account via the account management panel. InstitutionService orchestrates the following sequence: (1) AuthService revokes all active JWT sessions for the account immediately; (2) any pending or in-progress submissions owned by the account are handled per the submission state machine.  |
| INACTIVE | ACTIVE | Administrator clicks "Reactivate" on an existing INACTIVE account directly via the account management panel. The account is restored to ACTIVE state without requiring the user to go through the invitation token flow again. Existing credentials remain valid. The Administrator should confirm with the user that their credentials are still known.  |
| INACTIVE | PENDING | Administrator issues a new Invitation Token for an INACTIVE account (re-onboarding path) — used when the account owner has lost their credentials or needs to set a new password. A new token is generated and dispatched. The user must click the activation link and set a new password to complete activation (UC-1.1). The account transitions to ACTIVE only after the user completes the activation flow.  |

   2. ### ***Notes*** {#notes}

* Note 1 — State disambiguation (PENDING): The term PENDING refers to two distinct concepts in Note 1 — State disambiguation (PENDING): The term PENDING refers to two distinct concepts in DASIGConnect: (a) this account state, indicating a provisioned account awaiting invitation token activation; and (b) the submission state, indicating a submitted post awaiting Administrator review. Context determines which meaning applies. In this table, PENDING always refers to the account state.  
* Note 2 — EXPIRED as a PENDING sub-state: EXPIRED is a sub-state of PENDING introduced to distinguish between *"token active, awaiting use"* (PENDING) and *"token window has closed without activation"* (EXPIRED). Both states prevent login. The distinction matters for the account management panel display, for the reissuance flow, and for the PENDING — EMAIL UNDELIVERED → EXPIRED transition, which requires the Administrator to issue a new token rather than simply resending the existing one.  
* Note 3 — Invitation token expiry cleanup job: The PENDING → EXPIRED and PENDING — EMAIL UNDELIVERED → EXPIRED transitions are triggered by a scheduled cleanup job within `InvitationService`. This job runs daily and queries for account records where `invitation_token_expires_at < NOW()` and `state IN ('PENDING', 'PENDING_EMAIL_UNDELIVERED')`. A guard rail trigger governing this behavior (analogous to GR-T2, GR-T7) should be added to Section 3.3 as GR-T8 — Invitation Token Expiry Detection: *"Trigger condition: account state IN (PENDING, PENDING — EMAIL UNDELIVERED) AND invitation\_token\_expires\_at \< NOW(). System action: transition account to EXPIRED; retain record; surface in Administrator account management panel."*  
* Note 4 — Password reset does not alter account state: Password reset (UC-1.1 A8a — unauthenticated reset, and A8b — authenticated change) does not trigger any account state transition. The account remains in ACTIVE state throughout both reset flows. A8a invalidates all active JWT sessions upon password change (forcing re-login on all devices). A8b does not invalidate other sessions. Neither flow is represented in this state machine.  
* Note 5 — Administrator account provisioning: Administrator accounts are provisioned directly by the development team during the pilot period and are not subject to the invitation token flow. Administrator accounts have no entry in this state machine. Administrator account management is a post-pilot operational concern.  
* Note 6 — No automated account deletion: No automated account deletion occurs during the pilot period. All states — including EXPIRED and INACTIVE — retain the account record, submission history, media asset references, and audit entries. Permanent deletion is a post-pilot governance decision requiring explicit organizational authorization.

  5. ## ***References***

* Anthropic. (2024). Claude API Documentation. Retrieved from [https://docs.anthropic.com](https://docs.anthropic.com)  
* Aral, S., Dellarocas, C., & Godes, D. (2013). Introduction to the special issue—Social media and business transformation. Information Systems Research, 24(1), 3–13.  
* Baca, M. (Ed.). (2016). Introduction to metadata (3rd ed.). Getty Publications.  
* Brooke, J. (1996). SUS: A "quick and dirty" usability scale. In P. W. Jordan et al. (Eds.), Usability evaluation in industry (pp. 189–194). Taylor & Francis.  
* Brown, T. B., et al. (2020). Language models are few-shot learners. Advances in Neural Information Processing Systems, 33, 1877–1901.  
* Kaplan, A. M., & Haenlein, M. (2010). Users of the world, unite\! Business Horizons, 53(1), 59–68.  
* Li, J., Li, D., Xiong, C., & Hoi, S. (2022). BLIP: Bootstrapping language-image pre-training. ICML 2022\.  
* Meta Platforms. (2025). Graph API Reference v25.0. Retrieved from [https://developers.facebook.com/docs/graph-api](https://developers.facebook.com/docs/graph-api)  
* O'Reilly, T. (2007). What is Web 2.0. Communications & Strategies, 65(1), 17–37.  
* Radford, A., et al. (2021). Learning transferable visual models from natural language supervision. ICML 2021\.  
* Schwaber, K., & Sutherland, J. (2020). The Scrum Guide. Scrum.org.  
* Turban, E., Whiteside, J., King, D., & Outland, J. (2018). Introduction to information systems (6th ed.). Wiley.  
* Van der Aalst, W. M. P. (2016). Process mining: Data science in action (2nd ed.). Springer.  
* Bangor, A., Kortum, P., & Miller, J. (2009). Determining what individual SUS scores mean: Adding an adjective rating scale. *Journal of Usability Studies, 4*(3), 114–123.  
* Voyage AI. (2025). Voyage AI Embedding API Documentation. Retrieved from [https://docs.voyageai.com](https://docs.voyageai.com)

2. # **Overall Description**

   1. ## ***Product perspective***

DASIGConnect is a new, purpose-built, standalone web-based system that operates within the DASIG network's digital infrastructure. It is designed to replace the current informal, ad hoc coordination approach — which relies on individual messaging, email chains, and a single overburdened Administrator manually managing the DASIG Facebook page — with a structured, traceable, role-based digital workflow that distributes routine work to where bandwidth exists (member institutions) and reserves the Administrator for exception handling.

The system does not extend or replace any existing institutional software; it introduces a dedicated coordination layer between content contributors across DASIG member institutions and the official DASIG Facebook Page.

The system operates on a three-tier client-server architecture:

* **Presentation Tier** — A React single-page application served via Vercel's global CDN. Users access the system through a standard browser interface without requiring additional installations. The frontend communicates with the back end exclusively through RESTful API calls over HTTPS and a persistent connection for real-time notifications.  
* **Application Tier** — A Spring Boot REST API server handles all business logic, state machine enforcement, guard rail validation, external API orchestration, tenant isolation, and background job scheduling. The API is stateless, with session state carried via token-based authentication.  
* **Data Tier** — A PostgreSQL database manages all persistent data, including user accounts, institution workspaces, content submissions, media metadata, scheduling records, and analytics data. Row-Level Security policies enforce per-institution data isolation. Supabase provides PostgreSQL and pgvector. Media files and generated derivatives are stored in Cloudflare R2 through an S3-compatible backend integration; the browser uploads through backend-issued presigned URLs and reads media through the backend proxy.

The system integrates with the following external services:

* **Facebook Graph API** — used for automated publishing of Contributor-submitted content to the DASIG Facebook Page, supporting both image and video post formats, with re-authentication handling for expired access tokens (UC-3.5 A5).  
* **Anthropic Claude Vision API** — used for on-demand AI caption generation (UC-1.6) and background image classification to support media organization and search (UC-2.2).  
* **Voyage AI** — used to generate vector embeddings from image descriptions, powering semantic similarity search in the media recommendation feature (UC-2.2).  
* **Email Service Provider** — used for dispatching submission-status notifications, invitation tokens, password reset links, and system alerts.

Scheduled publishing, notification delivery, and system maintenance (such as embedding retries and token expiry checks) are handled through automated background processing, detailed further in the System Design section.

2. ## ***User characteristics***

DASIGConnect serves three role categories, reflecting the federated structure of the DASIG network, where each member institution retains editorial control over its own content while DASIG centrally coordinates publication only when human judgment is required.

**Contributor**

Faculty members, student organization officers, and institutional communications officers from DASIG member institutions. Each Contributor operates within their institution's isolated workspace and can only view and manage their own submissions. Contributors have basic digital literacy and require no technical expertise. They have read-only visibility into the Master Calendar to see network-wide slot availability when scheduling their own posts — they can see institution badges and submission time slots for posts from all institutions, but cannot view the content (caption, media, description) of posts from other institutions. Their submissions and institution-scoped media remain restricted to their institution.

**Moderator**

Moderators are network-wide reviewers and are not bound to a single institution. They review submissions from all institutions, may invite Contributors where permitted, and use the network-wide Approval Queue. They do not have Administrator-only governance, system-health, token-management, or unrestricted institution-management privileges.

**Administrator**

DASIG administrators operating under DOST Region 7\. Administrators have network-wide authority and are not bound to any single institution. Administrators are expected to have a working understanding of the content approval process and Facebook publishing operations, but require no specialized technical background beyond standard computer literacy. Every Administrator has full access to both the Administrator toolset and the Contributor toolset — meaning an Administrator can, in addition to their oversight duties, create and submit their own content the same way a Contributor would. The Admin Owner is a governance flag on one Administrator account, not a separate role, and is required for selected existing-Administrator actions and Admin Owner transfer.

3. ## ***Constraints*** {#constraints}

* user endpoints. Users in areas with poor network infrastructure may experience degraded performance or inability to upload media assets.  
* **Facebook API Development Mode** — During the development and evaluation phases, the DASIGConnect application operates under Facebook Development mode, restricting public post visibility to users who hold assigned roles on the registered Meta Developer Application. Transition to Live mode and full public post visibility requires Meta Business Verification by the DASIG organization, which is outside the scope of this capstone project. The codebase requires no changes to support this transition.  
* **Facebook Graph API Standard Access** — The system's publishing functionality requires the `pages_show_list`, `pages_read_engagement`, and `pages_manage_posts` permissions. Any changes to Meta's API policies, rate limits, or access requirements may affect system functionality.  
* **Vision-Capable Language Model API** — AI caption generation, image classification, and recommendation features depend on the availability and response quality of the integrated Anthropic Claude Vision API. Caption accuracy may vary based on image quality and content clarity. All AI features degrade gracefully if the API is unavailable, allowing the core workflow to remain functional.  
* **Voyage AI API** — Media recommendation quality depends on the availability of the Voyage AI `voyage-4-lite` embedding service. If embedding generation fails at upload time, the EmbeddingReconciliationJob (GR-T7) provides a fallback retry path. Assets that fail after 10 reconciliation attempts are flagged as `EMBEDDING_FAILED` and excluded from future recommendation cycles.  
* **Single Facebook Page & Platform Scope** — The DASIG Facebook Page is a single shared resource across all member institutions; coordinating publication timing on this shared resource — addressed through software-enforced guard rails rather than manual Administrator coordination — is the system's primary technical challenge. The system is scoped exclusively to this page: integration with other social media platforms (Instagram, X/Twitter, LinkedIn) is outside the scope of this version.  
* **Video Publishing Limitation** — Mixed-media submissions (containing both images and video) are not supported by the automated publishing pipeline and require recovery via the Approval Queue's Failed tab (UC-2.4 A8). Video-only and image-only submissions are supported by the automated pipeline.  
* **Media Content Types** — The system supports image (JPEG, PNG, WebP, GIF) and video (MP4, MOV, WebM) file uploads only. Other file formats (documents, audio) are not supported in this version. Maximum file size: 50 MB per file (UC-1.7).  
* **Deployment Platform and Background Processing** — The React frontend is deployed through Vercel and the Spring Boot backend is deployed through the selected production hosting environment. Because scheduled jobs run inside the backend process, the deployment must provide sufficient availability for minute-level publishing and five-minute monitoring jobs. If the hosting platform suspends the backend, scheduled publishing may be delayed and must be surfaced through the existing failed-publication and missed-review recovery paths.
* **Database and Object-Storage Limits** — Supabase is used for PostgreSQL and pgvector. Media files and generated derivatives are stored in Cloudflare R2 through the backend's S3-compatible storage integration. The browser uploads media using backend-issued presigned URLs and reads media through the backend proxy. HikariCP maximum pool size is constrained to 5 connections for the Supabase Session Pooler. Actual database, R2, bandwidth, and hosting limits depend on the selected service plans and must be configured for the pilot scale.

  4. ## ***Assumptions and Dependencies*** {#assumptions-and-dependencies}

The following assumptions and dependencies govern the operation, deployment, and evaluation of DASIGConnect:

* DASIG will designate one active Administrator as the Admin Owner and will identify the member institutions and authorized users who will use the system. Each institution is responsible for maintaining accurate Contributor information and assigning authorized personnel to its DASIGConnect activities.

* The Facebook Graph API remains available and continues to support Page publishing, Page engagement retrieval, and Page-token validation through the permissions and API version configured for the deployment. Changes to Meta's API policies, permissions, rate limits, or availability may affect publishing and engagement features.

* The connected Facebook Page has a valid, authorized Page Access Token. Token expiration, revocation, or permission changes suspend automated publishing and trigger the system's token-health notifications and reauthorization workflow. An Administrator with Admin Owner authority is responsible for connecting a different Facebook Page.

* Meta Business Verification and the transition from Facebook Development Mode to Live Mode are organizational prerequisites for public visibility of published posts. These activities are controlled by DASIG and Meta and are outside the application's implementation scope.

* The Anthropic Claude Vision API remains available and within the configured service limits for caption generation and image classification. If the service is unavailable, the system preserves the core drafting and approval workflow and exposes the applicable unavailable or retry state.

* The Voyage AI embedding API (`voyage-4-lite`) remains available and within its configured service limits. If an image embedding fails, the embedding reconciliation process provides a retry path; assets that remain unsuccessful are excluded from embedding-dependent recommendations until processing succeeds.

* Supabase remains available for PostgreSQL and pgvector, and Cloudflare R2 remains available for media objects and generated derivatives. The deployment must provide the database, object-storage, bandwidth, and connection-pool capacity required by the expected user and media volume. HikariCP is configured with a maximum pool size of five for the Supabase Session Pooler.

* DASIG member institutions and users have stable internet access sufficient for browser-based authentication, media upload through presigned URLs, media preview, and real-time notification delivery. Poor connectivity may delay uploads or prevent access without indicating a defect in the workflow rules.

* The email service provider (Resend) remains operational. Email is used for invitations, password resets, account activation, and configured submission or system notifications. Email delivery is independent of in-app notifications: failures are recorded in the email delivery log and do not block submission, approval, or other core workflow transitions. Invitation delivery failures leave the account in `PENDING_EMAIL_UNDELIVERED` until an Administrator verifies the address and resends the invitation (UC-1.1 A1); in-app notifications remain available (UC-3.3 A1).

* The system's background jobs run within the deployed Spring Boot application. The hosting environment must keep the backend sufficiently available for minute-level publishing, five-minute monitoring and stale-submission detection, notification processing, token checks, embedding reconciliation, and retention jobs. If the backend is unavailable, scheduled processing may be delayed and the affected submission is handled by the existing stale-submission, missed-review, or failed-publication recovery paths.

* The guard-rail thresholds defined in Section 3.3 — a 30-minute network-wide conflict buffer, two-hour minimum lead time, 8:00 AM–8:00 PM posting window, and six-post daily cap — represent the initial operating policy for the shared Facebook Page. An Administrator may enable or disable enforcement through the runtime setting. When enforcement is disabled, Standard posts still require a future scheduled time and slot reservation, while Fast-Track posts continue to bypass scheduled-slot rules.

* Usability evaluation will include representative users of the implemented roles and will follow the SUS procedure and minimum score defined in Section 3.4.8. A score below 68 triggers a usability review and targeted revision cycle.

* The system is developed and maintained using the team's selected development process. Changes in stakeholder availability, external-service availability, or deployment configuration may affect delivery and operational timelines without changing the functional requirements defined in this SRS.

* The Admin Owner remains reachable for governance decisions, Page connection changes, and ownership transfers. A planned ownership change is completed through the Admin Owner request-and-confirm flow to an active Administrator or Moderator; the Admin Owner designation is not a separate user role.

  5. ## ***Design Philosophy and Operating Model*** {#design-philosophy-and-operating-model}

DASIGConnect is designed around a single guiding principle that emerged from root-cause analysis of DASIG's current operational failures:

*The Administrator's involvement is inversely proportional to the volume of work. Routine, high-volume work is handled by Contributors within system-enforced rules and centralized Administrator review. The Administrator's exception-handling role is reserved for situations where human judgment provides genuine value beyond routine review. The system itself enforces coordination — humans do not coordinate.*

This principle directly addresses the bandwidth bottleneck identified in the project proposal: a single overburdened Administrator at DASIG cannot manually coordinate content from 7–12 institutions producing 50+ posts per month while also performing other DOST responsibilities. Any system that funnels routine scheduling decisions through ad hoc manual coordination would reproduce the original bottleneck on a different interface.

DASIGConnect implements this philosophy through a three-layer operational model:

**Layer 1 — Self-Service (Contributors).** Institution-side. High volume. Routine work. Contributors create content and schedule it directly within system-enforced rules, without requiring institutional-level review before submission. Goal: frictionless content production within fair rules.

**Layer 2 — Automation (System Rules and Cron Jobs).** Software-enforced. No human in the loop for normal cases. The system automatically enforces the network-wide conflict buffer, posting-hours window, minimum lead time, and daily volume cap when the guard-rail switch is enabled. Administrators may explicitly override a hard scheduling block, with the override recorded. Cron jobs handle scheduled publishing (every minute), stale-submission detection (every 5 minutes), retry logic, notification dispatch, token health checks, embedding reconciliation, and invitation token expiry detection. Goal: coordinate without coordination meetings. Replace bureaucratic approval steps with deterministic rules.

**Layer 3 — Exception Handling (Administrator).** Human judgment, reserved for genuine exceptions: failed publishes, institution onboarding, strategic overrides, and governance. The Administrator is not in the routine content-review path in the sense of being a scheduling bottleneck — content approval is centralized but system-supported, not manually coordinated. Goal: high-value human judgment applied only where it cannot be automated.

3. # **Specific Requirements**

   1. ## ***External interface requirements***

      1. ### ***Hardware interfaces***

DASIGConnect is a web-based system and does not require dedicated or specialized hardware interfaces. End users access the system through standard computing devices — desktops, laptops, or tablets — equipped with a modern web browser and a stable internet connection. No proprietary hardware, peripherals, or drivers are required. The system is hosted on a standard web server (cloud-based or institutionally provided) with sufficient CPU, RAM, and storage to serve concurrent users and store uploaded media files. For media uploads, standard input devices (keyboard, mouse, touchpad) and file storage (local drive, camera-connected storage) are sufficient. 

2. ### ***Software interfaces***

* **Facebook Graph API (v25.0)** — Used for automated publishing of approved and scheduled content to the official DASIG Facebook Page. The system authenticates via OAuth 2.0 using a long-lived Facebook Page Access Token. The token is encrypted at rest in the database using AES-256-GCM symmetric encryption. The encryption key (`FB_TOKEN_ENCRYPTION_KEY`) is stored as an environment variable in Render's environment variable dashboard and is never committed to version control, never included in the application repository, and never exposed in any API response or log output. Each encrypted value stores the IV and authentication tag alongside the ciphertext to support authenticated decryption. The API uses the verified two-step publishing method for image submissions: (1) `POST /{PAGE_ID}/photos` with `published=false` to obtain a photo ID; (2) `POST /{PAGE_ID}/feed` with `attached_media` to create a proper feed post. For video submissions, the system uses a single-call publishing method: `POST /{PAGE_ID}/videos` with the video file URL, caption text, and `published=true`. Mixed-media submissions (images \+ video in the same submission) are not supported by automated publishing during the pilot period and require manual recovery via the Approval Queue's Failed tab (UC-2.4 A8). The system requires only the `pages_show_list`, `pages_read_engagement`, and `pages_manage_posts` permissions.

* **Anthropic Claude Vision API** — Used for two AI-assisted features. (1) **Caption generation (UC-1.6):** the system submits base64-encoded image data with a structured prompt to the Claude vision endpoint and receives 1–3 tone-labeled caption variants. Images exceeding 5 MB are resized in-memory (scaling 70% → 50% → 35% → 25% → 15% until the JPEG falls below 5 MB) before submission to comply with API payload limits. The system sets `java.awt.headless=true` for Render server compatibility. API calls are triggered on demand when the Contributor explicitly clicks "Suggest Caption." The request timeout is 30 seconds. (2) **Image classification and description generation (UC-2.2):** the system classifies uploaded images into one of eight predefined content categories and generates a detailed factual image description and 8–15 controlled tags. API calls are triggered automatically and asynchronously upon each image upload to the media repository. Classification runs as a background pipeline; the Contributor is not blocked by it. The eight predefined categories are: Research, Awards and Recognition, Events, Community Outreach, Partnerships, Youth Programs, Media Coverage, Administrative.

* **Voyage AI API** (`voyage-4-lite` model, `output_dimension=1024`) — Embedding API used to generate 1024-dimensional vector representations of AI-generated image descriptions for semantic similarity search. Called asynchronously upon image upload after Claude Vision returns the image description and classification (UC-2.2). The explicit `output_dimension=1024` parameter is required for consistent vector dimensionality compatibility with the pgvector `VECTOR(1024)` column. Vectors are stored as `vector(1024)` columns in the PostgreSQL database via the pgvector extension. If embedding generation fails at upload time, the EmbeddingReconciliationJob retries hourly (GR-T7). The system validates returned vector length before storage.

* **Vercel —** Static hosting and global CDN for the React single-page application frontend. The frontend build artifact is deployed to Vercel on each release.

* **Render —** Cloud hosting for the Spring Boot application server. Environment variables (including `JWT_SECRET`, `FB_TOKEN_ENCRYPTION_KEY`, and all API keys) are configured in Render's environment variable dashboard and are never committed to version control.

* **Email Service Provider** (Resend or compatible SMTP) — Used for dispatching submission-status notification emails to recipients within 5 minutes of state change in ≥95% of cases. Compatible with standard SMTP servers or transactional email services.

* **Relational Database Management System (PostgreSQL via Supabase)** — The back-end application interfaces with PostgreSQL for persistent storage of all system data including user accounts, institution workspaces, content submissions, media metadata, scheduling records, slot reservations, notification logs, audit logs, and analytics aggregates. Row-Level Security policies enforce per-institution data isolation. The pgvector extension adds a `VECTOR(1024)` column to `media_assets` for cosine similarity nearest-neighbor search.

* **Web Browser** — The DASIGConnect front end is accessible via modern web browsers including Google Chrome (v100+), Mozilla Firefox (v100+), Microsoft Edge (v100+), and Apple Safari (v15+). No browser plugins or extensions are required.

* **Operating System** —The system is OS-agnostic on the client side, accessible from any operating system that supports a compatible web browser. The server-side application is designed for deployment on Linux-based server environments.

  3. ### ***Communications interfaces***

* **HTTPS / TLS** — All communication between the client browser and the application server is encrypted via HTTPS using TLS 1.2 or higher. Unencrypted HTTP connections are not supported.

* **RESTful API** — The front-end application communicates with the back-end server via a RESTful API using standard HTTP methods (GET, POST, PUT, PATCH, DELETE). API responses are formatted in JSON.

* **Server-Sent Events (SSE)** — Real-time in-app notifications are delivered using Server-Sent Events to ensure that notification badges update within 30 seconds of a submission state change without requiring page refresh.

* **OAuth 2.0** — The system uses OAuth 2.0 for authentication with the Facebook Graph API. Page Access Tokens are stored encrypted at rest in the database and are never exposed in client-side code, API responses, or version control.

* **SMTP / Email Protocol** — Submission-status notification emails are transmitted via SMTP or a compatible transactional email API, targeting delivery within 5 minutes of state change in ≥95% of cases.

  2. ## ***Functional requirements***

1. ### ***Module 1: User Access & Content Creation*** 

   This module covers the entry points into DASIGConnect and the tools Contributors use to build a post before it enters the approval pipeline. It spans account authentication and role-based access setup, the post composer and template system, AI-assisted content tools, mandatory media attachment rules, scheduling assistance, and the final handoff of a draft into the Administrator's review queue. 

   1. #### ***Administrator Account Management***   {#administrator-account-management}

      1. ##### ***Use Case Diagram*** {#use-case-diagram}

      2. ##### ***Use Case Description*** {#use-case-description}

      

| Use Case ID | UC-1.1 |
| :---- | :---- |
| **Use Case Name** | Administrator Account Management  |
| **Actor(s)** | Administrator (Admin Owner required for all actions targeting existing Admin account)  |
| **Precondition(s)** | The actor holds a valid, authenticated ACTIVE session with Administrator privileges. Admin Owner status is required for every action targeting an existing Admin account (A3–A6, the demotion path within A7, and A8): deactivation, reactivation, deletion, erasure, demotion, and ownership transfer. Proposing a promotion (A7) is open to any active Administrator — the target still has to confirm before anything changes, so it carries the same standard as inviting a new Admin. A peer (non-owner) Administrator may invite new Admins (subject to the cap, A2) and manage Moderator/Contributor accounts, including proposing an Admin promotion for one of them (A7). Self-targeting is blocked across every role-change and account-management action, Owner-gated or not. The initial Admin account is provisioned directly by the development team prior to pilot deployment and is outside the scope of this use case.  |
| **Main Flow** | The Admin navigates to Admin Management and selects Invite Admin. The system validates the request as a network-scoped Admin invitation with no institution assignment, and validates that active Admins plus outstanding Admin invitations number fewer than 3 (A2). The system generates a unique, single-use, time-sensitive invitation token bound to the invitee's email address, valid for 72 hours, and stores only the token hash. The system creates or updates the invitee's account record in `PENDING` state, reusing an existing `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `CANCELLED`, or `EXPIRED` record for that email if one exists (an existing `ACTIVE` account, or a re-invitation targeting an `INACTIVE` account, returns a conflict error — the latter must go through Reactivation, A4), and marks any older unused invitation tokens for the same email as used. The system dispatches an activation email containing the activation link with the raw token. The invitee completes activation by setting their password. The system re-validates the Admin cap at this step (A2) so a stale pending invitation cannot exceed the limit. The account transitions to `ACTIVE`, receives the Admin role, remains institutionless, and gains network-wide Admin privileges. |
| **Alternative Flow(s)** | **A1 — Invitation Email Undelivered:** If dispatch fails after retries, the account remains `PENDING_EMAIL_UNDELIVERED` until an Admin verifies the address and triggers a resend (A9). **A2 — Admin Limit Reached:** The system enforces a single combined count — active Admins plus outstanding Admin invitations plus pending promotions — against a maximum of 3, uniformly across every gate that could grant Admin access: invitation (Main Flow step 2), invitation acceptance (step 6), direct promotion (A7), and reactivation (A4). Each gate excludes only its own in-flight item from the count where relevant. Reactivation is the strictest case, with no exclusions — a deactivated 4th Admin cannot be reactivated into a network already holding 3\. When any gate would exceed the cap, the system rejects the action and directs the Admin to remove or transfer an existing Admin first. **A3 — Deactivate Admin Account:** *(Admin Owner only.)* Deactivates another `ACTIVE` Admin account (a non-active target returns a validation error). Revokes all active sessions, blocks login, retains historical submissions and audit data, and records the action in the audit log. **A4 — Reactivate Admin Account:** *(Admin Owner only.)* Reactivates a previously deactivated (`INACTIVE`) Admin account, blocked at the Admin cap (A2). The account's existing password remains valid and no new session token is issued. A non-inactive target returns a validation error. Re-inviting a deactivated Admin is explicitly rejected; reactivation is the only path back to `ACTIVE`. **A5 — Delete Admin Account:** *(Admin Owner only.)* Permanently removes an Admin account only when it is `INACTIVE`, `CANCELLED`, or `EXPIRED` (an `ACTIVE` target is rejected, requiring deactivation first). If the account has any historical footprint, it is not hard-deleted — it persists as an anonymized-at-rest inactive row with a `USER_REMOVED` audit entry. Only a completely footprint-free account is hard-deleted. **A6 — Erase Admin Account (Right to Be Forgotten):** *(Admin Owner only.)* Target must be `INACTIVE` or `CANCELLED`, must not be the Owner's own account, and must not already be erased. Anonymizes the record in place and writes a `USER_ANONYMIZED` audit entry. **A7 — Promote Existing User to Admin:** *(Propose/confirm flow. Proposing is open to any active Admin; only demoting an existing Admin is Owner-only.)* **Propose:** Any active Administrator (Owner or peer) selects an existing `ACTIVE` Moderator or Contributor to promote. The target cannot already be Admin (no-op) or the proposing Admin's own account, and the action is blocked at the Admin cap (A2). The system reserves a slot and creates a pending promotion; the target's role and access do not change yet. Writes `ADMIN_PROMOTION_REQUESTED` and notifies the target. A target cannot have two pending promotions at once. **Confirm:** The target confirms the promotion on their own account. The system re-checks the Admin cap before applying the change: institution assignment is cleared, the account gains network-wide Admin privileges (Admin Owner flag remains false), and active sessions are revoked. Writes `ADMIN_PROMOTION_CONFIRMED`. Unconfirmed after 72 hours, the request expires (`410 Gone`) and the slot frees itself. **Decline:** The target declines; the system clears the request, immediately frees the slot, and notifies the proposing Admin. Writes `ADMIN_PROMOTION_DECLINED`. **Cancel:** The proposing Admin rescinds a pending promotion before the target responds, with the same effect as a decline. Writes `ADMIN_PROMOTION_CANCELLED`. **Demotion** (Admin → Moderator or Contributor, **Admin Owner only**) remains a separate, immediate action with no confirmation step, recorded as `USER_ROLE_CHANGED`; demotion to Contributor requires assigning an active institution. **A8 — Admin Owner Transfer:** The current Admin Owner initiates a transfer request to a target account, which must be either an existing `ACTIVE` Administrator or an `ACTIVE` Moderator. The target has 24 hours to confirm before the request expires and is cleared. The system writes an `ADMIN_TRANSFER_REQUESTED` audit entry at initiation. **If the target is an existing Administrator:** upon confirmation, the outgoing account's Admin Owner flag is revoked and reassigned to the incoming account; both remain Administrators. *(This path currently cannot be confirmed through the API due to a role-gating inconsistency on the confirmation endpoint — flagged for engineering resolution, still open as of this revision.)* **If the target is a Moderator:** upon confirmation, the target is promoted to Administrator and becomes the new Admin Owner; the outgoing Owner is demoted to Moderator, keeping the total Admin headcount constant. In both cases, the outgoing account's active sessions are invalidated upon confirmation, and the system writes an `ADMIN_TRANSFER_CONFIRMED` audit entry. **A9 — Cancel Pending Admin Invitation:** An Admin cancels a pending Admin invitation while the target account is `PENDING`, `PENDING_EMAIL_UNDELIVERED`, or `EXPIRED`. The system deletes all outstanding tokens for that email and sets the account to `CANCELLED`. **A10 — Resend Pending Admin Invitation:** An Admin resends an invitation for an account in `PENDING_EMAIL_UNDELIVERED`, `EXPIRED`, or `CANCELLED` state. The system generates a fresh 72-hour token, invalidates all prior open tokens, and resets the account to `PENDING`. **A11 — Admin Owner Unreachable:** If the current Admin Owner cannot be reached to initiate a transfer, resolution is an out-of-scope, post-pilot governance concern requiring direct intervention by the development team or DOST Region 7 oversight. |
| **Postcondition(s)** | A new Admin account exists in `PENDING` or `ACTIVE` state; an existing Admin account has been deactivated, reactivated, deleted (if footprint-free), anonymized, had a promotion proposed, confirmed, declined, or cancelled, or been demoted; its pending invitation has been cancelled or resent; or Admin Owner status has been transferred. Every state-changing action revokes the affected account's active sessions where applicable, and is reflected in the audit log under one of: `INVITATION_ACCEPTED`, `USER_STATUS_UPDATED`, `USER_ROLE_CHANGED`, `USER_REMOVED`, `USER_ANONYMIZED`, `ADMIN_TRANSFER_REQUESTED`, `ADMIN_TRANSFER_CONFIRMED`, `ADMIN_PROMOTION_REQUESTED`, `ADMIN_PROMOTION_CONFIRMED`, `ADMIN_PROMOTION_DECLINED`, or `ADMIN_PROMOTION_CANCELLED`.  |

##### 

      3. ##### ***Activity Diagram*** {#activity-diagram}

      4. ##### ***Wireframe*** {#wireframe}

2. #### ***Institution Management***  {#institution-management}

   1. ##### ***Use Case Diagram*** {#use-case-diagram-1}

      2. ##### ***Use Case Description*** {#use-case-description-1}

      

| Use Case ID | UC-1.2 |
| :---- | :---- |
| **Use Case Name** | Institution Management  |
| **Actor(s)** | Administrator (full access), Moderator (read-only)  |
| **Precondition(s)** | The actor holds a valid, authenticated ACTIVE session with Administrator or Moderator privileges. At least one Administrator account exists. A protected default institution, "DASIG Central Visayas," is seeded during initial system deployment for network-wide Administrator post attribution and cannot be deactivated (A6) or permanently deleted (A8) through this use case.  |
| **Main Flow** | The actor navigates to Institution Management from the admin console. The system displays the institution list with each institution's name, status, and Contributor count. Moderators see this list in read-only form; action controls are visible only to Administrators. *(Administrator only)* The Admin selects Add Institution and enters the institution's required identifying details. The system validates the input, creates the institution workspace record, and establishes Row-Level Security scoping for that institution. The new institution appears in the institution list and becomes available for Contributor assignment. |
| **Alternative Flow(s)** | **A1 — Edit Institution Details:** *(Administrator only.)* **A2 — Deactivate Institution:** *(Administrator only.)* Blocked if any Contributors remain assigned (must reassign first, A4); otherwise deactivates, preventing new Contributor invitations and retaining historical data. **A3 — Reactivate Institution:** *(Administrator only.)* **A4 — Reassign Institution User:** *(Administrator only.)* Target institution must be `ACTIVE`; historical submissions remain attributed to the original institution. **A5 — Duplicate Institution Name:** Rejected, along with duplicate institution code or email domain. **A6 — Attempted Deactivation of Protected Institution:** Blocks deactivation of DASIG Central Visayas. **A7 — Moderator Attempts a Restricted Action:** Rejected with an authorization error; controls aren't exposed in the Moderator's view. **A8 — Permanent Institution Deletion:** *(Administrator only.)* Requires typing the institution's code to confirm. Blocked if: the target is DASIG Central Visayas; any Contributor exists in `PENDING`, `PENDING_EMAIL_UNDELIVERED`, or `ACTIVE` state; any submission of any state has ever existed; or any non-deleted media asset exists. If clear, deletes institution-scoped tokens/reservations/settings/watermark configs, deletes soft-deleted media and albums, removes the institution record, best-effort purges storage post-commit, and writes an `INSTITUTION_DELETED` audit entry. Irreversible, distinct from Deactivate. |
| **Postcondition(s)** | An institution record has been created, edited, deactivated, reactivated, or permanently deleted (if eligible); or a Contributor has been reassigned, with per-institution data isolation enforced accordingly.  |

      3. ##### ***Activity Diagram*** {#activity-diagram-1}

      4. ##### ***Wireframe*** {#wireframe-1}

   3. #### ***Contributor Account Management***  {#contributor-account-management}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-2}

      2. ##### ***Use Case Description*** {#use-case-description-2}

| Use Case ID | UC-1.3 |
| :---- | :---- |
| **Use Case Name** | Contributor Account Management  |
| **Actor(s)** | Administrator (any active admin), Moderator (invitation actions only, scoped to invitations they personally sent for resend/cancel/delete)  |
| **Precondition(s)** | The actor holds a valid, authenticated ACTIVE session with Administrator privileges (any active admin), or Moderator privileges for invitation actions specifically.   |
| **Main Flow** | The actor navigates to the institution's Contributor management panel and selects "Invite Contributor." The target institution must not be `INACTIVE`; a `PENDING` institution is permitted. If the actor is a Moderator, the system permits inviting to any institution (Moderators are network-wide) but restricts the assigned role to Contributor only. The system generates a unique, time-sensitive Invitation Token, valid for 72 hours, and creates the account record in `PENDING` state. If a `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `CANCELLED`, or `EXPIRED` account row already exists for that email, it is reused rather than recreated; an existing `ACTIVE` account for that email returns a conflict error, and an existing `INACTIVE` account for that email now also returns a conflict error (must go through Reactivation, A4) — consistent with how Admin and Moderator accounts are already treated. The system dispatches an activation email containing a single-use activation link scoped to that institution. The invitee completes activation (password setup) and the account transitions to `ACTIVE`, with Row-Level Security binding established to the assigned institution. |
| **Alternative Flow(s)** | **A1 — Invitation Email Undelivered:** Account remains `PENDING_EMAIL_UNDELIVERED` until resend is triggered. **A2 — Token Used on Wrong Institution:** Generic invalid-token error, no institution information disclosed. **A3 — Deactivate Contributor:** *(Administrator only.)* Deactivates an `ACTIVE` Contributor account; a non-active target returns a validation error. Revokes all active sessions and retains submission history unaffected. **A4 — Reactivate Contributor:** *(Administrator only.)* Reactivates an `INACTIVE` Contributor account; a non-inactive target returns a validation error. **A5 — Reissue Invitation:** For a Contributor account in `EXPIRED`, `CANCELLED`, `PENDING`, or `PENDING_EMAIL_UNDELIVERED` state, the actor issues a new Invitation Token, resetting it to `PENDING`. The previous token is invalidated. *(Actor: Administrator, or a Moderator reissuing an invitation they personally sent.)* **A6 — Delete Contributor Account:** Eligible target states are `INACTIVE`, `CANCELLED`, or `EXPIRED`. If the account has any footprint (a submission, a media upload, a validation/review log, an album it created, or any audit log entry), it is not hard-deleted; it persists as an anonymized-at-rest inactive row with a `USER_REMOVED` audit entry. Only a completely footprint-free account is hard-deleted. *(Actor: Administrator (any account), or a Moderator (only a Contributor they personally invited, whose invitation is `CANCELLED` or `EXPIRED`).)* **A7 — Erase Contributor Account (Right to Be Forgotten):** *(Admin Owner only.)* Target must be `INACTIVE` or `CANCELLED`. Anonymizes the record in place and writes a `USER_ANONYMIZED` audit entry. **A8 — Contributor as Role-Change Target:** A Contributor may be promoted directly to Moderator (immediate, any active Admin, no confirmation, sessions invalidated, `USER_ROLE_CHANGED`) or proposed for Admin (any active Admin may propose; the Contributor must confirm — UC-1.1 A7). Full behavior specified in UC-1.1/UC-1.10; not duplicated here. |
| **Postcondition(s)** | A Contributor account exists in `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `ACTIVE`, `CANCELLED`, or `EXPIRED` state, bound to a specific institution, or an existing account has been deactivated, reactivated, hard-deleted (if footprint-free), anonymized (A7), or moved out of the Contributor role entirely via A8. A "deleted" account with any historical footprint persists as an anonymized inactive row rather than disappearing.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-2}

      4. ##### ***Wireframe*** {#wireframe-2}

   4. #### ***Login & Session Management***   {#login-&-session-management}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-3}

      2. ##### ***Use Case Description*** {#use-case-description-3}

| Use Case ID | UC-1.4 |
| :---- | :---- |
| **Use Case Name** | Login & Session Management  |
| **Actor(s)** | Contributor, Administrator, Moderator |
| **Precondition(s)** | The actor holds an account record in the appropriate state (`ACTIVE` for login; `PENDING` for first-time activation, handled under UC-1.1/1.3).  |
| **Main Flow** | The actor logs in with registered credentials. The system validates the account state and issues a session token (JWT), valid for 8 hours, containing the actor's role, `session_version`, and tenant scope. Contributors receive institution-scoped sessions (the JWT carries `institution_id`). Moderators and Admins receive network-scoped sessions — neither carries an `institution_id` claim. The system routes the actor to the dashboard appropriate for their role. Admin Management, User Management, System Health, Audit Log, Page Settings, and Watermark Configuration are shown only to Admin users and hidden from Moderators and Contributors. Institution Management and the Review Queue are shown to Moderators and Admins. Analytics, Calendar, Media Repository, Notifications, Submissions, and Settings are shown to all three roles — Analytics is role-*scoped*, not Admin-exclusive. |
| **Alternative Flow(s)** | **A1 — Invalid Credentials:** Generic authentication error shown; no session issued. 5 failed attempts within the window lock the account for 15 minutes. **A2 — Account Not Yet Activated (`PENDING`/`PENDING_EMAIL_UNDELIVERED`):** Login rejected; actor directed to check email. **A3 — Account `EXPIRED`:** Login rejected; the actor is instructed to contact whoever manages their account (an Administrator, or the Moderator who sent the original invitation, if applicable) for reissue. **A4 — Account `INACTIVE`:** Login rejected with a deactivation notice, directing the actor to contact whoever manages their account for reactivation. **A5 — Password Reset (Unauthenticated):** The actor requests a reset link from the login page. Upon use, `session_version` is incremented, invalidating every previously issued token — forcing re-login on all devices. The new password must satisfy the policy: at least 12 characters, upper- and lower-case, a digit, a special character, no whitespace, and not built from a common fragment (`password`, `qwerty`, `admin`, `dasig`, `welcome`, `letmein`, `123`, `abc`) or the account's own email/name. **A6 — Password Change (Authenticated):** A logged-in actor changes their password from Settings, after providing their current password. `session_version` is not touched, so other active sessions are not invalidated. Same password policy as A5. **A7 — Session Expiry:** The frontend decodes the JWT's `exp` claim and shows a countdown banner in the final minutes before expiry, with **Stay Logged In** / **Dismiss** actions. Clicking Stay Logged In (or letting the countdown reach zero regardless of the banner) opens a Session Expired modal requiring the actor to re-enter their password — a full re-login (new JWT, dashboards/caches reset), not a silent token refresh. A `POST /auth/refresh` endpoint exists in the backend but is not currently called by the frontend. **A8 — Link Facebook Messenger for Notifications:** From Personal Settings, a Moderator or Administrator (not Contributors) may link their personal Facebook account. Not an OAuth consent flow — the system generates a short, random, 10-minute link code; the actor sends that code as a message to the DASIG Facebook Page in Messenger; a webhook validates it against the stored code hash, completing the link. Eligible for Messenger delivery on applicable triggers (UC-3.3 A4). Unlinking at any time reverts delivery to silent skip (UC-3.3 A5). **A9 — Unauthorized Admin Surface Access Attempt:** If a Moderator or Contributor attempts to access Admin Management, User Management, System Health, Audit Log, Page Settings, Watermark Configuration, or Facebook Page integration management by direct URL or API call, the system rejects the request with an authorization error and does not render the restricted screen in navigation. Analytics is not in this list. **A10 — Logout:** The actor logs out from a single device/session. The system blacklists only that one token — narrower than A5's account-wide revocation. Sessions open on other devices are unaffected. |
| **Postcondition(s)** | The actor holds a valid authenticated session scoped to their role and institution (Contributors) or network-wide (Moderators, Admins), or the requested settings/session action has completed. Admin-only surfaces remain unavailable to Moderators and Contributors; Messenger linking remains unavailable to Contributors.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-3}

      4. ##### ***Wireframe*** {#wireframe-3}

   5. #### ***Post Drafting & Templates***   {#post-drafting-&-templates}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-4}

      2. ##### ***Use Case Description*** {#use-case-description-4}

| Use Case ID | UC-1.5 |
| :---- | :---- |
| **Use Case Name** | Post Drafting & Templates  |
| **Actor(s)** | Contributor, Administrator, Moderator. Any of the three uses the same composer. Administrators and Moderators additionally get a Posting As institution selector; Contributors' posts are always attributed to their own institution.  |
| **Precondition(s)** | The actor holds a valid, authenticated ACTIVE session (UC-1.4). A Contributor's session is institution-scoped; an Administrator/Moderator session is network-wide and must pick an institution to post as (defaults to the protected network institution, "DASIG Central Visayas").  |
| **Main Flow** | The actor opens the composer — either New Post, or an existing item from "My Submissions" to resume editing (A1) or view status. Filters: drafts · action-needed · submitted · published · failed · all. The composer is a three-step wizard: Media → Post Details → Organize & Schedule. Forward progress requires the current step's Required readiness items; backward navigation is always allowed (A8). Data persists across steps within the session. *Step 1 — Media* The actor attaches media (device upload, library pick, AI-suggested media, per-asset captions, per-asset watermark opt-out) per UC-1.7. Advancing to Details when Details is already complete and no draft exists yet silently creates the draft. *Step 2 — Post Details* *(Administrator/Moderator only)* A Posting As dropdown, defaulting to DASIG Central Visayas. Changing it on a saved draft clears the preferred schedule, keeps selected media, prompts confirmation if a schedule was set, and requires a re-save to move the draft.  The actor enters event title and event date.  The actor writes the caption directly or via Suggest Caption (UC-1.6), with an optional tone/instruction prompt, returning one caption in the selected tone. Hard character cap of 3000 characters (code points); pasting past it trims with a warning. Caption length within a comfortable range is also a non-blocking Recommended check. The actor highlights caption text, clicks the Fancy text button in the caption action row, and selects a Unicode style from the displayed Fancy Text panel; the selected text is replaced with the chosen styled equivalent. The actor may click Check writing in the caption action row to request an on-demand AI writing check; suggestions are advisory only and may be applied or dismissed without blocking editing or submission. The actor adds hashtags — suggested pills or free-typed — inserted into the caption. The actor may pick a template from the Templates button and panel beside the caption form; applying it requires at least one attached media file, then replaces the caption with the template caption and clears the current tags. *Step 3 — Organize & Schedule* The actor organizes media into an album and adds media tags (UC-1.7). On submit, the backend reconciles the album — freshly uploaded assets file into the album resolved from the post's album name, auto-creating one from the event title if none is set; library picks keep their own album; all attached assets receive the post's media tags.  The actor chooses Set a Schedule (default, peak-hour suggestions per UC-1.8) or Live Event Fast-Track (hides scheduling fields). A Standard post always needs a future scheduled time; the guard-rail switch controls only the rules applied to that time. The actor may open a Facebook-style preview — a persistent center-panel toggle on any step.  The actor clicks Submit (UC-1.9). Disabled while any blocking item is unmet; if only Recommended items remain, allowed after a non-blocking confirmation showing the readiness score (A7). **Readiness Checklist (all steps)** Live-updating right-panel checklist producing a readiness score out of 100 (Required 75%, Recommended 25%), a grade, a description, and a dial. Each row is clickable, jumping to the relevant step/field. *Required (blocking):* event title present; event date present; caption non-empty; ≥1 media attachment; file requirements (size/format); album assigned; schedule (a future slot always required for Standard; guard rails ON additionally enforces the 8 AM–8 PM window and conflict rules — ±30 min spacing, ≤6 posts/day, ≥2h lead time; guard rails OFF skips those, but a future date is still required). *Recommended (non-blocking):* caption length in a comfortable range; ≥1 hashtag; ≥1 media tag; ≥1 per-media caption; a template used (or intentionally skipped — always passes for Fast-Track). **Scheduling Guard Rails (network-wide switch)** Governs spacing (±30 min), daily cap (≤6 posts/day network-wide), lead time (≥2h), and publish window (8 AM–8 PM) on the one shared DASIG calendar. Toggled by an Administrator in Settings → Page → Scheduling Guard Rails. The switch governs the rules on a scheduled time, not whether one is picked — a Standard post always requires a future slot and always gets a slot reservation; Fast-Track never does. **Templates** Built-in templates are hard-coded frontend constants, not admin-managed. Custom templates are personal (owner-scoped, not shared across an institution) — created via Save as Template, name-deduped per owner, deletable by their owner. The composer shows both lists together; applying either requires ≥1 media file attached and sets caption \+ tags. **Autosave & Explicit Save** Save as Draft is available on every step, independent of Next/Submit gating. Once a draft has an ID, edits autosave silently after a short delay; the first save is always explicit — a draft is never auto-created except when advancing from Media to Details with Details already complete. |
| **Alternative Flow(s)** | **A1 — Edit Existing Draft:** Loads a `draft` at Step 1 with all reached steps accessible via backward navigation. **A2 — Discard Draft:** Confirmation prompt, then deletion — allowed only in `draft` status; purges orphaned draft uploads and the slot reservation. **A3 — Template Not Selected:** Flow proceeds normally; "Template used" stays a ⚠ Recommended item. **A4 — Draft Returned for Revision:** A `needs_revision` draft reopens with reviewer remarks parsed into per-field comments; affected fields pulse and can be marked "addressed." Resubmission accepted from `draft` or `needs_revision`. A `rejected` submission shows the rejection reason and is terminal. Reviewer remarks exist only for revision requests (`validatorRemarks`) or rejection (`rejectionReason`); approval sets neither. **A5 — Dirty-State Navigation Warning:** Closing or navigating away with unsaved changes prompts confirmation. **A6 — Incomplete Readiness at Save:** Save as Draft is allowed at any step with unmet Required or Recommended items; the checklist persists as a reminder. **A7 — Submit Despite Recommended Warnings:** With Required items met but Recommended unmet, Submit proceeds after a non-blocking confirmation showing the readiness score. **A8 — Navigate Backward Between Steps:** Always allowed; forward requires the current step's Required items. Data persists within the session. **A9 — Withdraw a Submitted Post:** A still-`pending` submission may return to `draft` (refused once `in_review`); writes a `SUBMISSION_WITHDRAWN` audit entry, then re-enters this flow. |
| **Postcondition(s)** | A draft post record exists in `draft` state with event title/date, caption, hashtags, attached media (with per-asset captions and watermark opt-outs), resolved album \+ media tags, template metadata (if used), and schedule type (Standard or Fast-Track) — ready for further editing or submission (UC-1.9). A withdrawn or revision-returned submission re-enters this flow in `draft`/`needs_revision`.  |

##### 

##### ***Post Template — Standard Baseline Set*** {#heading}

| Template Name | Target Use Case | Default Caption Structure / Placeholders | Default Pre-Appended Tags |
| ----- | ----- | ----- | ----- |
| Event Announcement | Upcoming seminars, workshops, summits | \[EVENT TITLE\] / 📅 Date: / 📍 Venue or Platform: / 🔗 Registration Link: / \[Brief description / Call to action\] | \#DASIGCentralVisayas \#DOST7 \#InnovationEvent |
| Event Recap / Milestone | Post-activity highlights, achievements | HISTORY HAS BEEN MADE / EVENT RECAP / \[Summary of accomplishments / key takeaways\] / \[Acknowledged partners and attendees\] | \#DASIGCentralVisayas \#HistoryMadeHere \#DOST7 |
| Competition / Pitching Call | Hackathons, reverse pitching challenges | CALL FOR INNOVATORS / PARTICIPANTS / \[Challenge Theme / Problem Statement\] / 🏆 Prizes or Opportunities: / 📅 Deadline for Submission: / 🔗 Apply here: | \#FlipTheScript \#ReversePitching \#DASIG |
| Partner Feature / Spotlight | Member university/HEI spotlights | INSTITUTIONAL SPOTLIGHT: \[University Name\] / \[Feature on student research, lab innovation, or award\] | \#ConnectedInnovation \#CentralVisayas \#\[UniversityTag |

#####  {#heading}

3. ##### ***Activity  Diagram*** {#activity-diagram-4}

   4. ##### ***Wireframe*** {#wireframe-4}

      

   6. #### ***AI & Text Tools Integration***   {#ai-&-text-tools-integration}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-5}

      2. ##### ***Use Case Description*** {#use-case-description-5}

| Use Case ID | UC-1.6 |
| :---- | :---- |
| **Use Case Name** | AI & Text Tools Integration  |
| **Actor(s)** | Contributor, Administrator, Moderator — all three use the same composer. A Moderator without an institution scope may generate captions for any institution's submission.  |
| **Precondition(s)** | The actor is in an active composer session (UC-1.5) on a saved draft — "Suggest Caption" is not rendered until the draft has an id. Fancy Text works on any editable draft. Both tools are available in Live Event Fast-Track mode. Neither is available on a read-only submission. |
| **Main Flow** | **AI Caption Generation** The actor clicks Suggest Caption in the caption field's action row. A prompt dialog opens. The actor may optionally type instructions and pick one of three tones — professional (default), community, energetic. The dialog proceeds as long as there's an attached image, an existing caption, or a typed prompt. The actor confirms. The prompt is limited to 280 characters; Generate is disabled past the limit (also enforced server-side). The system sends up to 4 submission images, base64-encoded and downscaled in memory when necessary, together with media metadata, event title/date/institution, existing caption, prompt, and selected tone to Claude, with a 30-second timeout. The system returns exactly one caption for the selected tone. The actor inserts it, dismisses it, or clicks Regenerate (optionally revising the prompt first). The actor edits the inserted caption freely before saving or submitting. Each outcome (use / use\_then\_edited / dismiss / re\_generate, plus tone) is logged best-effort; a logging failure never blocks the UI. **Rate limit:** 30 caption requests per rolling hour per user. Over the limit, returns `429` with reset headers; the button shows a rate-limited state with the reset time. **Requested word count:** if the prompt names a word count, the largest number is parsed; over 2000 is rejected with `400`. A requested range triggers up to 3 retries to bring Claude's output into range. **Fancy Text Styling** The actor clicks the Fancy Text button in the caption field's action row. A panel opens. If text is selected, the panel shows that text transformed into each available style, previewed live on hover/focus. If nothing is selected, the panel indicates that no caption text is selected. Styles: Bold Serif, Italic Serif, Bold Sans-Serif, Italic Sans-Serif, Script/Cursive, and Plain (revert). The actor clicks a style; selected text is replaced with its styled Unicode equivalent. Re-styling already-styled text reverts to plain first, then applies the new style. The actor may style other selections or revert via Plain. |
| **Alternative Flow(s)** | **A1 — AI Request Timeout:** No response within 30s → timeout notice, stays clickable for retry, auto-returns to idle after \~5s (`504`). **A2 — AI Service Unavailable:** A failed non-timeout call returns `503`; the button shows "AI unavailable," disabled, auto-recovers to idle after \~5s. No health check or persistent disable — reactive only. UC-1.5 is unaffected. **A3 — No Image Attached:** Generation proceeds with an empty image list if there's an existing caption or typed prompt. **A4 — Empty Prompt:** Uses the selected tone (default professional), returns one caption in that tone. **A5 — Prompt Exceeds Length Limit:** 280-character cap; Generate disabled past it; also enforced server-side with a `400`. **A6 — Revert Fancy Text:** Selecting styled text and choosing Plain maps each character back to standard. **A7 — Unsupported Character Styling:** Only A–Z/a–z (and digits for bold styles) have Unicode equivalents; punctuation, emoji, accented letters, and spaces pass through unstyled. |
| **Postcondition(s)** | The caption field reflects the actor's chosen AI-suggested caption and/or fancy-styled Unicode text, fully editable and ready for continued drafting (UC-1.5) or submission (UC-1.9). AI interaction outcomes are recorded in the interaction log.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-5}

      4. ##### ***Wireframe*** {#wireframe-5}

      

   7. #### ***Media Attachment Rules***  {#media-attachment-rules}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-6}

      2. ##### ***Use Case Description*** {#use-case-description-6}

| Use Case ID | UC-1.7 |
| :---- | :---- |
| **Use Case Name** | Media Attachment Rules  |
| **Actor(s)** | Contributor, Moderator, Administrator — all three use the same composer (UC-1.5); there is no separate "Administrator acting as Contributor" mode.  |
| **Precondition(s)** | The actor is in an active composer session (UC-1.5).  |
| **Main Flow** | The actor initiates media attachment from the composer's Add Media step by choosing to upload a new file from their device or select from the existing media library. **Device Upload:** the actor selects one or more image (JPEG, PNG, WebP, GIF) or video (MP4, MOV, WebM) files from their device. The system validates each file: only the seven accepted formats (case-insensitive; `jpg` normalized to `jpeg`), and file size must be greater than 0 and no more than 50 MB. Valid files are uploaded through a backend-issued presigned R2 URL and attached to the draft, with a thumbnail preview shown in the composer. **Library Selection:** alternatively, the actor browses or searches the media repository (UC-2.2) and selects existing asset(s) to attach. **AI-Suggested Media:** once the actor has entered enough event context (event title, caption, and tags combined ≥10 characters), the system embeds that text, runs a nearest-neighbor search scoped to the submission's institution, re-ranks candidates with metadata/tag boosts, keeps only results scoring ≥0.40, and shows up to 8 ranked suggestions. Category is not collected by the composer. The actor may select any suggested asset directly, bypassing manual search. Selected library asset(s) are linked to the draft and shown as thumbnail previews. For any attached asset, the actor may open the Media Caption modal to set a per-asset caption (≤500 characters, live counter) scoped to that image or video. Within that modal, for a photo asset only (not shown for video), the actor may check "Skip watermark for this image" to exclude it from automatic watermarking at publish time. In the Organize & Schedule step (UC-1.5, Step 3), the actor assigns the draft's album via a combobox: filter/select an existing album, type a new name to create one, or click Auto-Match. Auto-Match ranks every root album in the institution by a blend of tag overlap and visual similarity to the closest existing asset in each album (not a centroid). A confident match (≥0.55) applies immediately with an "AI-matched" badge and its reasons; an ambiguous result (≥0.32 but below 0.55) lists up to 3 ranked candidates instead of auto-applying; no match (nothing clears 0.32, or the institution has no root album yet) leaves the field for manual entry with a brief notice. The actor may also add media tags; if none are added, the Event Title is used as the default tag on submit. The system enforces mandatory media attachment and organization: the draft cannot be submitted (UC-1.9) until at least one valid media file is attached and an album name is set. |
| **Alternative Flow(s)** | **A1 — Invalid File Type:** Rejected with an unsupported-format error listing accepted types; no asset record created. **A2 — File Exceeds Size Limit:** Rejected with a size-limit error; no asset record created. **A3 — Upload Network Failure:** The composer shows a "Retry upload" action; no partial asset record created. **A4 — Remove Attached Media:** The actor removes a previously attached file from the draft before submission. **A5 — No Relevant AI Suggestions Found:** If no candidate clears the 0.40 similarity floor after re-ranking, the suggestions row does not render; the actor proceeds via Device Upload or Library Selection. If the embedding call itself fails, the system falls back to a metadata-only suggestion list rather than showing nothing. **A6 — Insufficient Event Details for Suggestions:** The suggestions row does not appear until the combined event title \+ caption \+ category \+ tags text reaches 10 characters. **A7 — Mixed-Media Attachment:** The draft accepts both image(s) and video for drafting purposes, flagging the submission as requiring manual publishing. At the publishing stage, the system detects the mix and marks it failed instead of attempting automated publishing; the submission surfaces in the Approval Queue's Failed tab for manual recovery (UC-2.4). **A8 — Ambiguous Auto-Match:** When the top candidate scores ≥0.32 but below the 0.55 confident bar, the album field is left untouched and the same dropdown instead lists up to 3 ranked candidates (album name, match percentage, and reason — tag overlap and/or visual similarity) for the actor to select, choose a different existing album, or create a new one. **A8a — Auto-Match, No Confident Result:** When nothing clears 0.32 (or the institution has no root album yet), the field is left untouched with a notice that no confident match was found, and the actor proceeds as if Auto-Match were unavailable. **A9 — Attempted Submission Without Media or Album:** Submission is blocked with a combined error message listing everything missing (media attachment, album assignment, or both). **A10 — Per-Asset Caption Skipped:** Per-asset captions are optional; they surface only as a non-blocking Recommended readiness item and never block progression to submission. |
| **Postcondition(s)** | The draft contains at least one valid media attachment, meeting format and size requirements, with any per-asset captions and watermark preferences set, and an album name assigned — eligible to proceed to submission (UC-1.9). A media tag is always present in practice (the Event Title default), though this is an emergent guarantee from the Event Title being a required field, not an independently enforced "at least one tag" gate.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-6}

      4. ##### ***Wireframe*** {#wireframe-6}

   8. #### ***Engagement Helpers***  {#engagement-helpers}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-7}

      2. ##### ***Use Case Description*** {#use-case-description-7}

| Use Case ID | UC-1.8 |
| :---- | :---- |
| **Use Case Name** | Engagement Helpers  |
| **Actor(s)** | Contributor, Moderator, Administrator — all three use the same composer (UC-1.5). The panel is skipped only for an Admin/Moderator composer that hasn't yet picked an institution scope (guard rails cannot be evaluated without one).  |
| **Precondition(s)** | The actor is in an active composer session (UC-1.5), on the Organize & Schedule step, with a Standard (non-Fast-Track) draft. Live Event Fast-Track drafts skip scheduling entirely and never see this panel.  |
| **Main Flow** | The actor opens the scheduling panel within Organize & Schedule; it loads automatically for a Standard draft, with no separate "open" action. The system queries real historical Facebook engagement for the DASIG Page: the last 100 posts' timestamps plus reactions, comments, and shares, scored as their sum per post. Samples are bucketed by day-of-week and hour, restricted to 8 AM–8 PM local time (Asia/Manila), averaged per bucket, and the top 5 buckets become candidate windows — provided at least 20 samples exist; otherwise a fixed default of Tuesday/Wednesday/Thursday 6 PM is used (A1). For each candidate window, the system walks the next 30 days for the first matching weekday, validates it against the same scheduling guard rails as a manual pick, and keeps it only if not hard-blocked — up to 3 recommended slots total, each labeled with a single-hour window (e.g., "Best engagement: Tuesdays 6 PM \- 7 PM") and carrying any soft warnings and a rounded score. The actor selects a recommended slot (filling the date/time fields, same as typing manually), or ignores the recommendation and chooses a custom date/time in the standard picker shown below the panel. The system validates the selected slot — recommended or manual — against the same guard rails (conflict prevention, lead time, publish window, daily cap), debounced on every change. The selected schedule is saved to the draft on the next autosave/save, same as any other field. |
| **Alternative Flow(s)** | **A1 — Insufficient Historical Data:** Fewer than 20 samples (or no engagement in the 8 AM–8 PM window at all) falls back to the fixed default (Tuesday/Wednesday/Thursday 6 PM, descending weight), shown in the panel as "Best-practice guidance" with a notice that recommendations will improve as more Facebook history accumulates. **A2 — Recommended Slot Conflicts with Guard Rail:** A hard-blocked candidate slot (e.g., inside another post's ±30-minute buffer) is excluded from the list entirely — not shown with a "blocked" annotation. **A3 — Analytics Service Unavailable:** Any failure fetching Facebook data (no Page token configured, Graph API error, network failure) results in the panel rendering nothing; the date/time picker underneath is unaffected. **A4 — Manual Override of Recommendation:** The actor may pick any non-recommended time, blocked only by a hard guard rail (same as a recommended slot). If it clears the hard block but carries a soft warning, the composer shows it inline beneath the date/time fields, the same treatment used for the mixed-media notice (UC-1.7 A7). This applies only when the network-wide guard-rail toggle is enabled. |
| **Postcondition(s)** | The draft has an assigned scheduled date/time, either selected from a system recommendation or chosen manually by the actor, validated against the same scheduling guard rails either way.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-7}

      4. ##### ***Wireframe*** {#wireframe-7}

   9. #### ***Draft Submission***  {#draft-submission}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-8}

      2. ##### ***Use Case Description*** {#use-case-description-8}

| Use Case ID | UC-1.9 |
| :---- | :---- |
| **Use Case Name** | Draft Submission  |
| **Actor(s)** | Contributor, Moderator, Administrator — all three use the same composer (UC-1.5) and the same submission endpoint.  |
| **Precondition(s)** | A draft exists in `draft` (or `needs_revision`, for a resubmission — A3) state, with at least one valid media attachment (UC-1.7) assigned to an album, and — for a Standard (non-Fast-Track) draft — a selected scheduled date/time (UC-1.8).   |
| **Main Flow** | The actor reviews the completed draft in the composer, including caption, attached media, and scheduled time. The actor clicks Submit for Approval. The frontend first re-checks locally that event title, event date, caption, media, album, and (for Standard drafts) a schedule are all present — missing anything jumps back to the relevant step with an error toast, without a server round-trip. The system performs final server-side validation: event title, event date, and album assignment are checked alongside the caption (non-empty, ≤3000 characters — UC-1.6) and media (≥1 attached asset). For Standard drafts only, the scheduled slot is re-validated against guard rails (conflict prevention, daily cap, lead time, publish window) — skipped entirely for Fast-Track. If validation passes, the draft transitions from `draft` (or `needs_revision`) to `pending`, and further edits are blocked. The system notifies Moderators of the new submission (T-01: in-app notification \+ Messenger DM to every Moderator; no email for this trigger). Administrators are not notified on a standard submission. The submission appears in the network-wide Approval Queue (UC-2.4, reachable by both Moderators and Administrators), and the actor's own draft list reflects its new `pending` status. |
| **Alternative Flow(s)** | **A1 — Validation Failure at Submission:** Any failed check in step 3 occurs before the status changes or any media is reconciled, so the draft remains in `draft`/`needs_revision` with a specific error message describing what failed. **A2 — Withdraw Submission:** Withdrawal is blocked the instant a review lock exists for the submission (opening it in the queue for review creates the lock), returning a conflict error. Withdrawing is valid only from `pending` (not `in_review`) — returns the draft to `draft` and clears its submitted timestamp. **A3 — Resubmission After Revision:** A `needs_revision` submission is editable again (UC-1.5 A4) and resubmits through the exact same flow. **A4 — Concurrent Slot Conflict:** The backend re-validates guard rails at submit time and rejects a newly-conflicting slot with a conflict error. On this specific error, the composer closes the confirmation dialog and returns the actor to the Organize & Schedule step with a toast prompting them to select a new time. Any other submit-time error shows a generic toast. **A5 — Live Event Fast-Track Submission:** Scheduling-slot validation is skipped entirely for Fast-Track. The immediate high-priority notification (T-11, in-app \+ email \+ Messenger) goes to all Moderators. Fast-Track submissions sort to the top of the active Approval Queue (ordered ahead of the standard date-based sort); this prioritization applies only to the active queue view — the resolved-items history tab sorts by date only, since Fast-Track priority is meaningless for an already-resolved item. |
| **Postcondition(s)** | The submission exists in `pending`, visible in the Approval Queue (reachable by Moderators and Administrators), with content locked until approved, rejected, or returned for revision, and — for Fast-Track — sorted to the top of the active queue.  |

##### 

      3. ##### ***Activity  Diagram*** {#activity-diagram-8}

      4. ##### ***Wireframe*** {#wireframe-8}

   10. #### ***Moderator Account Management***  {#moderator-account-management}

       1. ##### ***Use Case Diagram*** {#use-case-diagram-9}

       2. ##### ***Use Case Description*** {#use-case-description-9}

| Use Case ID | UC-1.10 |
| :---- | :---- |
| **Use Case Name** | Moderator Account Management |
| **Actor(s)** | Administrator (full management of Moderator accounts), Moderator (invitation/management privileges over Contributor accounts they personally invited — see UC-1.3)  |
| **Precondition(s)** | The actor holds a valid, authenticated ACTIVE session with Administrator privileges. Moderators are network-wide and hold no institution assignment.  |
| **Main Flow** | The Admin navigates to Moderator Management and selects Invite Moderator. The system validates the request as a network-scoped Moderator invitation with no institution assignment. The system generates a unique, single-use, time-sensitive invitation token bound to the invitee's email address, valid for 72 hours, and stores only the token hash. The system creates or updates the invitee's account record in `PENDING` state, reusing an existing `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `CANCELLED`, or `EXPIRED` record for that email if one exists (an existing `ACTIVE` account, or a re-invitation targeting an `INACTIVE` account, returns a conflict error — the latter must go through Reactivation, A3), and marks any older unused invitation tokens for the same email as used. The system dispatches an activation email containing the activation link with the raw token. The invitee completes activation by setting their password. The account transitions to `ACTIVE`, receives the Moderator role, remains institutionless, and gains network-wide review privileges (UC-2.4) plus the Contributor-management privileges defined in UC-1.3. |
| **Alternative Flow(s)** | **A1 — Invitation Email Undelivered:** If dispatch fails after retries, the account remains `PENDING_EMAIL_UNDELIVERED` until an Admin verifies the address and triggers a resend (A8). **A2 — Deactivate Moderator Account:** *(Any active Admin.)* Deactivates an `ACTIVE` Moderator account (a non-active target returns a validation error). Revokes all active sessions, blocks login, retains historical review activity and audit data, and records the action in the audit log. **A3 — Reactivate Moderator Account:** *(Any active Admin.)* Reactivates a previously deactivated (`INACTIVE`) Moderator account. Existing credentials remain valid; no new session token is issued. A non-inactive target returns a validation error. Re-inviting a deactivated Moderator is rejected; reactivation is the only path back to `ACTIVE`. **A4 — Delete Moderator Account:** *(Any active Admin.)* Permanently removes a Moderator account only when it is `INACTIVE`, `CANCELLED`, or `EXPIRED` (an `ACTIVE` target is rejected, requiring deactivation first). If the account has any historical footprint — submissions, media uploads, validation logs/review actions, albums created, or any audit entry — it is not hard-deleted; it persists as an anonymized-at-rest inactive row with a `USER_REMOVED` audit entry. Only a completely footprint-free account is hard-deleted, recorded as `USER_DELETED`. **A5 — Erase Moderator Account (Right to Be Forgotten):** *(Admin Owner only.)* Target must be `INACTIVE` or `CANCELLED` and must not already be erased. Anonymizes the record in place and writes a `USER_ANONYMIZED` audit entry. **A6 — Moderator Invites Contributor:** A Moderator invites a Contributor to any institution but may only assign the Contributor role. Full behavior specified in UC-1.3. **A7 — Moderator Manages Own Sent Invitations:** A Moderator may resend, cancel, or (once cancelled/expired) delete a Contributor invitation they personally sent, per UC-1.3 A5/A6. A Moderator cannot deactivate, reactivate, or delete an already-`ACTIVE` Contributor account. **A8 — Cancel Pending Moderator Invitation:** *(Any active Admin.)* Cancels a pending Moderator invitation while the target account is `PENDING`, `PENDING_EMAIL_UNDELIVERED`, or `EXPIRED`. Deletes all outstanding tokens for that email and sets the account to `CANCELLED`. **A9 — Resend Pending Moderator Invitation:** *(Any active Admin.)* Resends an invitation for a Moderator account in `PENDING_EMAIL_UNDELIVERED`, `EXPIRED`, or `CANCELLED` state. Generates a fresh 72-hour token, invalidates all prior open tokens, and resets the account to `PENDING`. **A10 — Moderator as Promotion or Transfer Target:** A Moderator may be the target of an Admin promotion proposal (UC-1.1 A7) or an Admin Owner Transfer (UC-1.1 A8). Both are specified fully in UC-1.1 and are not duplicated here. **A11 — Change Moderator Role (Lateral Move):** Any active Admin may move an account directly between Contributor and Moderator via role change — no invitation, no confirmation step, distinct from the confirmation-gated Admin promotion path in A10/UC-1.1 A7. **Contributor → Moderator:** the account's institution assignment is cleared, and it gains network-wide Moderator privileges. **Moderator → Contributor:** requires assigning an active target institution. **Either direction:** invalidates the account's active sessions, requiring re-login, and releases any submissions the account currently holds under an active review lock back to the queue. Recorded as `USER_ROLE_CHANGED`. |
| **Postcondition(s)** | A new Moderator account exists in `PENDING` or `ACTIVE` state, whether created via invitation or via lateral role change from Contributor; an existing Moderator account has been deactivated, reactivated, hard-deleted (`USER_DELETED`, if footprint-free), anonymized (`USER_ANONYMIZED`), demoted to Contributor, or had its pending invitation cancelled or resent; and any review locks held at the time of a role change or deactivation have been released back to the Approval Queue. All state-changing actions are reflected in the audit log.  |

##### 

       3. ##### ***Activity  Diagram*** {#activity-diagram-9}

       4. ##### ***Wireframe*** {#wireframe-9}

2. ### ***Module 2: Media Library & Approval Pipeline***  {#module-2:-media-library-&-approval-pipeline}

   This module governs the institution-scoped media library — where assets are uploaded, organized into event-based albums, discovered through search and AI-assisted browsing, and tracked across their reuse history — along with the Administrator's approval pipeline for submitted content and the automated watermarking applied upon approval. 

   1. #### ***Library Uploads & Albums***  {#library-uploads-&-albums}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-10}

      2. ##### ***Use Case Description*** {#use-case-description-10}

| Use Case ID | UC-2.1 |
| :---- | :---- |
| **Use Case Name** | Library Uploads & Albums  |
| **Actor(s)** | Contributor, Moderator, Administrator — upload and album management endpoints are open to any authenticated user, not role-restricted.  |
| **Precondition(s)** | The actor is authenticated with an active session. The institution's media library exists (provisioned upon institution creation, UC-1.2).  |
| **Main Flow** | The actor initiates an upload from the Media Library screen, selecting one or more files from their device. The system validates each file against accepted formats (JPG, PNG, WEBP, GIF, MP4, MOV, WEBM) and the 50 MB size limit, checked both client-side and server-side. Before the upload is finalized, the actor specifies: **Album:** select an existing album, click Auto-Match, or type a new name to create one — or select/type a full folder path to file the upload into a nested album structure (see A8). Auto-Match scores existing albums by tag/filename word overlap and tiers the result — a confident match (≥0.6) auto-fills with a reasons badge; an ambiguous one (≥0.3) lists up to 3 ranked candidates for the actor to confirm or override; no match leaves the field for manual entry. **Tags:** the actor enters at least one tag (server-enforced). A tooltip suggests using the event name as a tag to improve search results. On confirmation, the system stores the asset(s) — an image is marked `PROCESSING`; a video is marked `READY` immediately, since nothing is queued for it — applies the selected/created album, and saves the actor-entered tags. The system triggers AI classification and embedding asynchronously, for image files only. A video upload is never classified or embedded. The asset appears in the library grid under its assigned album with a "Processing…" badge that clears once classification and embedding succeed. |
| **Alternative Flow(s)** | **A1 — Unsupported File Type:** Rejected client- and server-side. **A2 — File Exceeds Size Limit:** Rejected at the 50 MB limit, both ends. **A3 — Upload Network Failure:** Retry prompt, consistent with the composer's own pattern (UC-1.7 A3). **A4 — Ambiguous Album Match (Auto-Match):** Auto-Match is text-based (tag/filename overlap), not visual similarity — the file isn't yet uploaded or embedded at album-selection time, unlike the submission composer's Auto-Match (UC-1.7), which scores already-attached, already-embedded assets. A confident match (≥0.6) auto-applies with a reasons badge; an ambiguous result (≥0.3) presents up to 3 ranked candidates inline for the actor to confirm or choose differently. **A4a — Auto-Match, No Confident Result:** No text match found; the field is left for manual entry with an inline notice. Upload is blocked with no album name set. **A5 — Manual Album Reassignment:** The actor moves an asset to a different existing album. Every library asset always belongs to exactly one album — there is no "unfiled"/unassign state, by deliberate design. **A6 — Manual Album Creation:** The actor creates a new album independent of any upload. **A7 — Rename Album:** The actor renames an existing album. **A8 — Nested Album Organization:** An album may be created as a sub-album of another (a parent-child folder structure). The actor may either create/select a full folder path in one action (each segment created if it doesn't already exist), or re-parent an existing album under a different parent afterward. The system blocks any re-parenting action that would make an album a descendant of itself, preventing a circular folder structure. **A9 — Post-Upload Tag Management:** After upload, the actor may add or remove individual tags from an asset's detail view, independent of the tags entered at upload time. At least one tag must always remain — removing the last tag on an asset is blocked, consistent with the mandatory-tag rule enforced at upload (Main Flow, step 3). **A10 — Cross-Institution Asset Movement (Administrator, and Contributor/Moderator for their own uploads):** An asset may be moved into or out of the shared network-default institution's library (DASIG Central Visayas), in addition to reassignment within the actor's own institution (A5). An Administrator viewing the "All institutions" view may additionally reassign an asset across any two institutions. |
| **Postcondition(s)** | The uploaded asset exists in the institution's media library, assigned to an album (which may be nested within a folder structure) with at least one tag. An image asset is queued for AI classification and embedding, transitioning from `PROCESSING` to `READY` on success (or `FAILED` if classification or embedding fails). A video asset is stored and organized identically but marked `READY` immediately, without AI classification or embedding — a known scope limitation of the current pipeline, not a defect. Tags and album/folder placement may continue to be edited after upload (A8, A9).  |

      3. ##### ***Activity Diagram*** {#activity-diagram-10}

      4. ##### ***Wireframe*** {#wireframe-10}

   2. #### ***Semantic Search & Filtering***  {#semantic-search-&-filtering}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-11}

      2. ##### ***Use Case Description*** {#use-case-description-11}

      **Scope Note** – UC-2.2 governs browsing, discovery, and detailed inspection of the institution's media library — search, filtering, album navigation, and the Asset Detail Panel. It intersects with UC-1.7 when a Contributor browses the library from within the post composer, and with UC-2.1 for asset upload and album assignment. AI classification tags and embeddings displayed here are generated by the pipeline defined in UC-3.3. 

| Use Case ID | UC-2.2 |
| :---- | :---- |
| **Use Case Name** | Semantic Search & Filtering  |
| **Actor(s)** | Contributor, Moderator, Administrator  |
| **Precondition(s)** | The actor is authenticated with an active session. The institution media library exists (UC-1.2, UC-2.1).  |
| **Main Flow** | The actor navigates to the Media Library from the workspace sidebar. The system displays all media assets for the actor's institution in a grid view, grouped/filterable by album. Each asset card shows the asset code, title, upload date, file size and type, and either a "Processing…" badge or the asset's first AI-classified tag. The actor browses using text search, an album filter, sort (Newest/Oldest/Name/Size), and a grid/list view toggle. Text search covers filename, asset code, uploader email, and every tag — manual or AI-generated. Event name is not currently a searchable field, since it is not stored on the media asset itself. The result strip updates with the matching count and any active filter labels. The actor clicks an asset card to open the Asset Detail Panel: media preview; asset code (read-only); an editable title (click to reveal a text field; Enter or blur saves, Escape cancels); a metadata grid (filename, uploader, institution, upload date, album, file size, resolution/duration, file type); an AI Tags block with per-tag confidence percentages; a "Your tags" block with an add-tag field; and a "Used In" history list (UC-2.3). From the panel, the actor may select Use in New Social Media Post or Add to Existing Draft — both available to all three roles. |
| **Alternative Flow(s)** | **A1 — No Results Found:** The system displays an empty state ("No assets match your filters") with active filters remaining visible and clearable. **A2 — Cross-Institution Access Attempt:** Requesting an asset outside the actor's visible institutions returns a not-found response, reinforced by database-level row security. **Network View** — full cross-institution browsing — is available to Moderators and Administrators. Routine network browsing is not itself an audit event. **A3 — Custom Tag Search:** A newly added tag (manual or AI-generated) is immediately searchable via keyword matching, with no delay. Semantic similarity *ranking* (as distinct from keyword search) reflects tags present at the asset's original AI classification time; a tag added afterward affects keyword search results but not the semantic ranking order, since re-computing the embedding on every tag edit was deliberately scoped out to avoid an added AI service round-trip on every tag change. **A4 — Add to Existing Draft, No Active Drafts:** If the actor has no draft-status submissions, the system offers to start a new post with the selected media instead. **A5 — Add to Existing Draft, Asset Limit Reached:** If adding an asset would exceed the 10-media-per-submission limit, the system notifies the actor; when adding multiple assets in one action, any assets successfully attached before the limit was reached remain attached. |
| **Postcondition(s)** | The actor has located and, if applicable, reused a media asset; the asset's detail view accurately reflects its current metadata, AI tags, album, title, and available actions.  |

      3. ##### ***Activity Diagram*** {#activity-diagram-11}

      4. ##### ***Wireframe*** {#wireframe-11}

   3. #### ***Media History Tracking***  {#media-history-tracking}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-12}

##### 

      2. ##### ***Use Case Description*** {#use-case-description-12}

| Use Case ID | UC-2.3 |
| :---- | :---- |
| **Use Case Name** | Media History & Asset Lifecycle  |
| **Actor(s)** | Contributor, Moderator, Administrator  |
| **Precondition(s)** | The actor is authenticated with an active session and is viewing an asset via the Asset Detail Panel (UC-2.2) or the audit log.  |
| **Main Flow** | From the Asset Detail Panel, the actor views the Used In block: a chronological list of current submission relationships and historical reuse records, each showing submission title, date, current status badge, and a jump link subject to access permissions. Deleted submissions display as "\[Submission Deleted\]," and deleted assets display as "\[Asset Deleted\]" where appropriate. The system records reuse and media lifecycle events server-side. An Administrator may review the resulting history network-wide through the audit log. |
| **Alternative Flow(s)** | **A1 — Asset Deletion, Blocked:** If the asset is referenced in an active workflow state such as `PENDING`, `IN_REVIEW`, or `SCHEDULED`, the system blocks deletion and displays the conflicting submission. **A2 — Draft Reference:** If the asset is referenced only in `DRAFT` or `NEEDS_REVISION` submissions, deletion requires an explicit force confirmation warning that the reference will break; the affected draft surfaces a broken-reference warning before submission. **A3 — Asset Deletion:** If the asset is referenced only in terminal-state submissions or has no references, the system marks it deleted and retains the historical relationship information. The 30-day retention period is an internal purge buffer; the application does not provide a user-facing trash or restore screen. **A4 — Duplicate Asset Detected:** On upload, the system compares the file's SHA-256 content hash with existing assets. An exact hash match is flagged as a duplicate; visual-similarity duplicate detection is not part of this lifecycle feature. |
| **Postcondition(s)** | The asset's current relationships, historical reuse, album changes, and deletion status are accurately reflected in its Used In block and audit history. Deleted records remain represented by placeholders until the retention purge removes the underlying data. |

##### 

      3. ##### ***Activity Diagram*** {#activity-diagram-12}

      4. ##### ***Wireframe*** {#wireframe-12}

##### 

   4. #### ***Admin Approval Workflow***  {#admin-approval-workflow}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-13}

      2. ##### ***Use Case Description*** {#use-case-description-13}

| Use Case ID | UC-2.4 |
| :---- | :---- |
| **Use Case Name** | Approval Workflow  |
| **Actor(s)** | Administrator, Moderator |
| **Precondition(s)** | The actor is authenticated with an active session. The Approval Queue is defined as the union of `pending` and `in_review` submissions network-wide.  |
| **Main Flow** | The actor navigates to the Approval Queue, structured into four tabs: All, Pending, In Review, and Failed. The system displays submissions network-wide — Moderators and Administrators see the identical queue, with no per-institution scoping for either role. The actor opens a submission and clicks Review. The system transitions it from `pending` to `in_review` and acquires a review lock scoped to that reviewer, with a 15-minute duration that is renewed while the panel remains active. The Submission Detail panel displays the submitted media, AI tags, submitter information, event details, scheduled publication date/time, and caption. The actor reviews the content for completeness, accuracy, and appropriateness. The actor may optionally edit allowed submission fields before taking a terminal action (A9, A10). The actor selects one of three terminal actions: Approve, Request Revision, or Reject. Approve transitions the submission to `scheduled`, or publishes it immediately for Live Event Fast-Track submissions. Request Revision transitions the submission to `needs_revision` and releases the slot reservation. Reject transitions the submission to `rejected` and releases the slot reservation; a rejected submission may later be edited and resubmitted. The system records the actor's identity, action, timestamp, and any remarks, edits, or rejection notes in an immutable log. Edits that add media are recorded as a distinct action from other field edits. |
| **Alternative Flow(s)** | **A1 — No Submissions in Queue:** The system displays an empty state for that view. **A2 — Request Revision Without Valid Remarks:** Remarks must be between 10 and 1,000 characters; the system rejects anything outside that range. **A3 — Concurrent Review Attempt:** A second reviewer sees who currently holds the review lock, and the panel renders read-only until the lock is released or expires. **A4 — Review Abandoned:** If no terminal action is taken within the lock's active period, the submission automatically reverts from `in_review` to `pending` and the lock releases. **A5 — Self-Submission Review:** Unconditionally blocked. A reviewer cannot acquire a review lock on their own submission under any circumstance — another Moderator or Administrator must make the decision. There is no self-approval path, governed or otherwise. **A6 — Submission Approaching Publish Time Without Review:** The system raises an approaching-deadline warning; if the scheduled time passes without a review decision, the submission automatically transitions to `missed_review`. **A7 — Media Asset Load Failure:** If a media asset fails to load within the review panel, the system displays a "failed to load" placeholder with a Retry action that re-requests the asset. The reviewer is not blocked while this is unresolved — Request Revision and Reject remain available throughout. **A8 — Failed Submission Handling:** An Administrator may recover a `publish_failed` submission via manual publish, retry, or by changing its publishing mode in either direction. A Moderator may retry (within the same publishing mode) or reschedule a non-Fast-Track submission, but cannot change its publishing mode or convert a Scheduled submission to Fast-Track. **A9 — Edit Scope and Confirmation:** Edits to media and scheduling are validated against guard rails and the 10-media-per-submission limit before saving. There is no separate watermark validation rule at edit time; the only watermark-related capability available during review is the existing per-asset skip-watermark setting. **A10 — Edit Audit Trail:** The system stores a before/after diff of any edited fields, classified by severity (Quiet, Flagged, or Added Media), alongside the standard action log entry. This tracking spans the entire review cycle — from the last terminal action up to the next — so an edit is correctly counted even across an interrupted session where the review lock is released and reacquired before a final decision, and correctly resets after each terminal action. **A11 — Contributor Notification of Edit:** When a submission is edited during review, the Contributor receives a separate notification specific to the edit, in addition to the base approval notification — the base notification itself never mentions edits. The edit notification's wording reflects its severity tier; only Flagged and Added Media edits trigger an email, while a Quiet edit notifies in-app only. |
| **Postcondition(s)** | **On Approve:** the submission is `scheduled`, or published immediately for Fast-Track; the slot is confirmed (non-Fast-Track) or bypassed (Fast-Track); the Contributor is notified via the base approval notification, plus a separate edit notification if the review session included edits; the action and any edit diff are recorded. **On Request Revision:** the submission is `needs_revision`; the slot is released; the Contributor is notified with the reviewer's remarks (in-app and email); the action is recorded. **On Reject:** the submission is `rejected`; the slot is released; the Contributor is notified with the reason code and any notes (in-app and email); the rejected submission remains eligible for editing and resubmission under the submission workflow. |

      3. ##### ***Activity Diagram*** {#activity-diagram-13}

      4. ##### ***Wireframe*** {#wireframe-13}

   5. #### ***Automated Watermarking***   {#automated-watermarking}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-14}

      2. ##### ***Use Case Description*** {#use-case-description-14}

| Use Case ID | UC-2.5 |
| :---- | :---- |
| **Use Case Name** | Automated Watermarking  |
| **Actor(s)** | Administrator  |
| **Precondition(s)** | For configuration: the actor is an authenticated Administrator. For application: a submission containing at least one photo is being published.  |
| **Main Flow** | **Watermark Configuration** An Administrator navigates to Page Settings and opens the Watermark configuration panel: a transparent canvas editor with 1:1, 4:5, and 16:9 aspect-ratio preview toggles. The Administrator adds up to 3 elements — image, text, or shape (rectangle or line). Each element is positioned and sized as percentage-based coordinates and can be dragged or resized. The Administrator previews the design across the available aspect ratios. The Administrator saves; the system stores each element's position and size as percentage values, along with its type-specific properties (image reference, text content and font, or shape type and color). **Automatic Application** Watermarking is applied at the moment of publication, not at approval. This happens immediately for a Live Event Fast-Track submission, or at the scheduled time for a Standard submission. Because the configuration is read fresh at the moment of publishing, an Administrator updating the watermark design between a submission's approval and its actual publish time will affect that already-approved submission — the design in effect at publish time is always the one applied, not whatever was configured at approval. For each photo asset (JPEG, PNG, GIF, or WebP), the system reads the current network-wide watermark configuration, reads the photo's actual dimensions, and renders all configured elements at their proportionally scaled positions. The watermarked version is generated and used for publishing; the original, unwatermarked asset remains unchanged in the media library. Video assets are published without any watermark applied. |
| **Alternative Flow(s)** | **A1 — No Watermark Configured:** If no configuration exists at all, the system applies a built-in default text watermark rather than publishing unwatermarked. A submission publishes genuinely unwatermarked only if a configuration exists and has been explicitly disabled by an Administrator. **A2 — Update Existing Watermark Design:** An Administrator may edit the watermark configuration at any time. Because the design is read live at each publish attempt rather than fixed at approval time, an update takes effect on any submission not yet actually published, including ones already approved and scheduled. **A3 — Watermark Application Failure:** If rendering fails for any reason (an undecodable image, a download error, or any other exception in the render pipeline), the system publishes the original, unwatermarked image rather than blocking or delaying the post — a missing brand mark is treated as a minor cosmetic issue, not one worth risking a missed publish for. Every failure is recorded in the immutable audit log and triggers an in-app and email notification to every Administrator, linking to the affected submission. **A4 — Mixed-Media Submission:** For a submission containing both photos and video, only the photo assets are watermarked; the video publishes without a watermark. **A5 — Per-Asset Watermark Opt-Out:** If the Contributor marked a specific photo as exempt during drafting, that opt-out is honored before any configuration lookup or rendering occurs — the asset publishes exactly as uploaded. |
| **Postcondition(s)** | Approved photo submissions carry the network-wide watermark design, rendered proportionally to each photo's dimensions, at the moment they actually publish — except assets marked for opt-out. Original, unwatermarked assets remain unchanged in the media library. Video assets publish without a watermark. A rendering failure results in the unwatermarked original being published, always accompanied by an audit log entry and an Administrator notification.  |

      3. ##### ***Activity Diagram*** {#activity-diagram-14}

      4. ##### ***Wireframe*** {#wireframe-14}

### 

### 

3. ### ***Module 3: Scheduling, Publishing and Analytics*** {#module-3:-scheduling,-publishing-and-analytics}

   Module 3 covers the master calendar (with role-differentiated visibility), automated Facebook post publishing via Graph API using the verified two-step method and analytics. Delivery target: end of Module 3 sprint. 

   1. #### ***Master Calendar Visibility***  {#master-calendar-visibility}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-15}

      2. ##### ***Use Case Description*** {#use-case-description-15}

| Use Case ID | UC-3.1 |
| :---- | :---- |
| **Use Case Name** | Master Calendar Visibility  |
| **Actor(s)** | Contributor, Moderator, Administrator |
| **Precondition(s)** | The actor is authenticated. For reschedule: the target submission must currently be `scheduled`.  |
| **Main Flow** | **Calendar Visibility**  Any authenticated actor opens the calendar. **Moderator or Administrator:** sees every submission with a scheduled slot or publish time, network-wide, always rendered in full detail (caption, description, contributor identity, everything) — Moderator and Administrator have identical calendar visibility, with no scoping difference between them. **Contributor:** sees two combined sets of events: A **network view**, rendered in full detail for their own institution and the network-default institution ("DASIG Central Visayas"), and masked (caption, description, and contributor identity withheld) for every other institution. Their **own in-flight submissions** — including states not otherwise visible to anyone else on the calendar (Pending, In Review, Publish Failed, Missed Review) — always shown in full detail and clearly marked as their own, so a Contributor can track their own submissions through the whole pipeline even before they become calendar-visible to others. **Reschedule (Moderator/Administrator only)**  The actor drags a `scheduled` event to a new date/time. The system performs quick client-side checks (rejecting a same-slot drop or a drop within 1 hour of the current time). **For a Moderator:** the interface also pre-checks the reschedule cap and window (see A1) before allowing the action to proceed, giving fast feedback without a server round-trip. Administrators are exempt from both. A confirmation dialog opens requiring a written justification — this applies to every reschedule, regardless of whether the new slot triggers a guard rail warning, so that every calendar change carries a stated reason in the audit trail, not just contested ones.  On confirmation, the system re-validates on the server: the submission must still be `scheduled`, a Moderator's cap/window is re-checked authoritatively (the client-side check is a convenience, not the real enforcement), and the new slot is validated against the guard rails. If the guard rails are satisfied, or the actor is an Administrator supplying an override reason, the reschedule proceeds as an atomic, conflict-safe write (see A3). The slot is reserved, the Contributor is notified, and the updated event appears on the calendar. |
| **Alternative Flow(s)** | **A1 — Moderator Reschedule Cap & Window:** A Moderator may reschedule a given submission at most 2 times, and only within ±1 day of the slot it was originally approved at (not the most recently rescheduled slot — anchoring to the original approval prevents drift through repeated small moves). Either limit is a hard stop for a Moderator, with no override available to them; they must ask an Administrator, who is exempt from both limits. If a failed publication is later retried onto a new schedule, both the count and the anchor slot reset, since that retry is effectively a fresh start. **A2 — Guard Rail Hard Block on Reschedule:** If the new slot violates a hard guard rail, a Moderator cannot proceed under any circumstances, even with a written reason, and is directed to ask an Administrator. Only an Administrator may supply an override reason and proceed; doing so is recorded in the audit log with the violated rules, both the original and new slot, and the stated reason. **A3 — Concurrent Reschedule Protection:** Reschedule writes are atomic and conflict-safe: if two reschedule attempts for the same submission are made at nearly the same time, only one succeeds: the other is rejected with a clear conflict message rather than silently allowed to overwrite or bypass a Moderator's cap. **A4 — Slot Conflict Integrity (Network-Wide, GR-H1):** The ±30-minute network-wide conflict buffer between any two active posts is enforced at the database level, not just at the point a request is validated, so it cannot be bypassed by a timing race between two near-simultaneous requests. A submission's held slot is released once that submission actually publishes, so a completed post no longer occupies its buffer window indefinitely. This database-level enforcement includes one deliberate, narrowly scoped exemption: when an Administrator exercises their guard-rail override authority (A2) to reschedule into an otherwise-conflicting slot, that specific reservation is explicitly flagged as an authorized override and is excluded from the database-level block — every other reservation, including any other Administrator or Moderator action, remains fully enforced with no exception.  |
| **Postcondition(s)** | Every actor sees a calendar reflecting their role's true scope — Administrator and Moderator see the full network in full detail; Contributor sees their own institution and the network-default institution in full detail, every other institution masked, plus their own in-flight submissions regardless of status. A `scheduled` submission may be moved by a Moderator (within their cap and window) or an Administrator (unrestricted, with override justification where a guard rail is violated), with every write guaranteed correct even under concurrent requests — including protection against a race with any other path that could claim the same slot, not only another reschedule attempt — and network-wide slot-conflict integrity enforced independently of the application layer, except for the single, explicitly tracked case of an authorized Administrator override.  |

##### 

      3. ##### ***Activity Diagram*** {#activity-diagram-15}

      4. ##### ***Wireframe*** {#wireframe-15}

   2. #### ***Automated Facebook Post Publishing***  {#automated-facebook-post-publishing}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-16}

      2. ##### ***Use Case Description*** {#use-case-description-16}

      

| Use Case ID | UC-3.2 |
| :---- | :---- |
| **Use Case Name** | Automated Facebook Post Publishing  |
| **Actor(s)** | System  |
| **Precondition(s)** | At least one submission is `scheduled` with a slot that has arrived, or a Live Event Fast-Track submission has just been approved (UC-2.4).  |
| **Main Flow** | A background scheduler runs every minute, querying for due submissions whose slot falls within a 5-minute lookback window, accounting for brief server or scheduling delay. A Fast-Track submission bypasses this query entirely (A5). Each due submission is claimed — transitioned to an in-progress publishing state via an atomic database operation — before any external API call is made. If a submission has already been claimed by another scheduler pass, it is skipped. This claim is the actual mechanism preventing the same post from being published twice by two overlapping scheduler runs. The system fetches and decrypts the active Facebook Page token. If expired, see A3. The submission's media is inspected: image-only submissions use the two-step photo path (step 5); video-only submissions use the single-call path (step 6); mixed image-and-video submissions are immediately marked failed with no retry attempted at all. **Photo publishing:** each image is staged and attached to the feed post in a single combined operation per image — not staged in a separate batch first — so a failure on any individual image aborts immediately with no risk of other images remaining successfully staged but orphaned. Each image includes its own per-asset caption, if one was set during drafting. Watermarking is resolved and applied immediately before staging, using whatever configuration is active at that instant. Once every image has been successfully staged and attached, a single closing call finalizes the post using the submission's main caption.  **Video publishing (single call):** the video and its caption are submitted in one call, with no staging step. On success: the submission is marked `published` (or its direct-post equivalent), the platform's post identifier and publish timestamp are recorded, the Contributor is notified, and the calendar reflects the successful publication. On failure: up to 3 attempts run with increasing delay between them. Exhausting all attempts marks the submission as failed, notifies the Administrator, and makes it available for manual recovery (UC-2.4 A12). A separate job runs every 5 minutes, sweeping for submissions whose scheduled time has passed without being published — see A2 for how this outcome differs depending on the submission's prior state. |
| **Alternative Flow(s)** | **A1 — Photo Staging Failure:** If any individual image fails during its combined stage-and-attach operation, the entire publish attempt aborts at that point. Because staging and attaching now happen together per image rather than as two separate passes, no photo can end up staged on Facebook without also being attached — eliminating the orphaned-photo cleanup problem entirely. The submission proceeds through the standard retry sequence like any other publish failure.  **A2 — Missed Slot Detection:** The 5-minute sweep produces two distinct outcomes depending on the submission's prior state, not one: A submission that was already approved and scheduled, but whose scheduler pass never actually ran (for example, the system was briefly unavailable), transitions to the standard failed state and follows the normal manual-recovery path (UC-2.4 A12). A submission that was **never reviewed at all** before its scheduled time passed transitions instead to a distinct **Missed Review** state, releasing its slot reservation. This is not treated as a publishing failure, since it was never approved to publish in the first place — recovery sends it back into the Approval Workflow from the beginning (UC-2.4), not through the Failed tab's manual-publish recovery. The calendar displays this state with its own distinct color, separate from both a successful publish and a genuine publish failure. **A3 — API Token Expiry:** An expired token blocks publishing without consuming a retry attempt, since the underlying issue is the connected Page's authentication, not the post itself. The submission is suspended and Administrator alerts escalate in stages — an initial warning, a 24-hour escalation, and a final notice — culminating in the submission transitioning to the failed state and becoming available for manual recovery if the token is not reauthorized within 48 hours. Resolving the token itself is UC-3.5's territory, not this use case's. **A4 — Development Mode Visibility:** During the pilot period, posts published through the system are visible only to users holding a role on the connected Meta Developer Application. This is expected behavior under Facebook's Development Mode, not a system defect, and requires no code change to resolve — only DASIG's completion of Meta Business Verification, which is an organizational step outside this system's scope. **A5 — Live Event Fast-Track Immediate Publish:** An approved Fast-Track submission publishes immediately upon approval, independent of the scheduled-time-based scheduler entirely, since a Fast-Track submission has no scheduled slot for that scheduler to ever match against. **A6 — Mixed-Media Submission:** Detected immediately and routed straight to the failed state with no retry attempted, since Facebook does not support combining images and video in a single automated post. |
| **Postcondition(s)** | On success, the submission is `published` with the platform's post identifier recorded, the Contributor notified, and the calendar updated accordingly. Analytics reflects a new publication within roughly 60 seconds in typical use. Submissions whose scheduled time passes unpublished are detected within 5 minutes and resolved into one of two distinct outcomes depending on whether they had already been approved: a standard publish failure with manual recovery available, or a return to the Approval Workflow if they were never reviewed at all. Token expiry suspends publishing with escalating Administrator alerts, culminating in failure and manual recovery if unresolved within 48 hours.  |

##### 

      3. ##### ***Activity Diagram*** {#activity-diagram-16}

      4. ##### ***Wireframe*** {#wireframe-16}

   3. #### ***Automated Reminders & Alerts***  {#automated-reminders-&-alerts}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-17}

      2. ##### ***Use Case Description*** {#use-case-description-17}

| Use Case ID | UC-3.3 |
| :---- | :---- |
| **Use Case Name** | Automated Reminders & Alerts  |
| **Actor(s)** | Contributor, Moderator, Administrator.  |
| **Precondition(s)** | A triggering event has occurred (submission state change, publishing outcome, token health change, or a scheduling gap).  |
| **Main Flow** | The system detects a triggering event matching one of the defined notification triggers. The system determines the recipient set for that trigger based on its category, not a single fixed role: **Review-related triggers** (a new submission awaiting review, a submission returned to the queue) notify every active Moderator and every active Administrator — both roles, together, since either can act on the Approval Queue. **Content-outcome triggers** (approved, rejected, revision requested, edited, published, publish failed) notify the submitting Contributor. **System/infrastructure triggers** (token health, background job failures) notify Administrators only, since Moderators have no access to system configuration. The system dispatches the notification via in-app (always), email (for triggers configured to include it), and Facebook Messenger (only for a recipient who has linked their personal account, per the recipient's individual opt-in). The recipient's in-app notification badge updates in real time via a live connection, without requiring a page refresh, and without polling. The recipient may click a notification to navigate directly to the relevant submission, calendar entry, or settings screen. The recipient may view their full notification history, with read and unread state tracked per recipient. |
| **Alternative Flow(s)** | **A1 — Email Delivery Failure:** If an email fails to send, the system retries independently of the in-app notification, which is dispatched immediately regardless of the email outcome. **A2 — Notification Read State Sync:** Reading a notification through any entry point updates its read state consistently across every active session for that recipient. **A3 — High-Volume Notification Handling:** The system does not currently consolidate a burst of same-type notifications into a single summary; each triggering event generates its own individual notification. This is a known simplification, not a defect — a recipient receiving many notifications of the same type in a short window sees them individually rather than grouped. **A4 — Facebook Messenger Delivery:** For a trigger configured to include Messenger, delivery additionally goes to any recipient in that trigger's set who has personally linked their Facebook account. Messenger delivery is opt-in per individual, not per trigger or per role — two Administrators could have different Messenger delivery outcomes for the identical event, based solely on whether each has linked their own account. **A5 — Messenger Not Linked:** A recipient who has not linked their account is silently skipped for Messenger delivery on that trigger; their in-app and (where applicable) email delivery proceed normally and without error. |
| **Postcondition(s)** | Every recipient in the applicable set for a triggering event is notified via in-app delivery at minimum, with email and Messenger delivered according to that trigger's configuration and each individual recipient's Messenger opt-in status. Notification read state is accurate and consistent across all of a recipient's active sessions.  |

**Notification Trigger Matrix**

| Trigger ID | Event Name | Trigger Condition | Recipient | Channel |
| ----- | ----- | ----- | ----- | ----- |
| T-01 | New Draft Submitted | `DRAFT` → `PENDING_APPROVAL` | Administrator | In-app |
| T-02 | Post Approved & Scheduled | Admin approves → `SCHEDULED` | Contributor | In-app \+ Email |
| T-03 | Post Rejected | Admin rejects → `REJECTED` | Contributor | In-app \+ Email |
| T-04 | Post Published (Automated) | `PUBLISHED` via API | Contributor | In-app |
| T-05 | Post Published (Manual Fallback) | Admin marks `PUBLISHED_MANUAL` | Contributor | In-app |
| T-06 | Automated Publishing Failed | API fails after retries → `PUBLISH_FAILED` | Admin \+ Contributor | Admin: In-app \+ Email; Contributor: In-app |
| T-07 | Empty Schedule Warning | Weekly scan finds 0 posts scheduled upcoming | Admin \+ Contributor | In-app |
| T-08 | Token Expiry Warning | FB token expires within 7 days | Administrator | In-app \+ Email |
| T-09 | Token Validation Failure | Token check fails / publishing suspended | Administrator | In-app \+ Email |
| T-10 | Admin Reschedules Post | Admin moves post to new slot | Contributor | In-app |
| T-11 | Live Event Fast-Track Submission | Fast-Track draft → `PENDING_APPROVAL` | Administrator | In-app \+ Email |
| T-12  | Embedding Reconciliation Failure Diges  | Weekly scan of `EMBEDDING_FAILED` assets  | Administrator | In-app \+ Email   |

3. ##### ***Activity Diagram*** {#activity-diagram-17}

##### 

   4. ##### ***Wireframe***  {#wireframe-17}

##### 

   4. #### ***Social Engagement Analytics***  {#social-engagement-analytics}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-18}

      2. ##### ***Use Case Description*** {#use-case-description-18}

| Use Case ID | UC-3.4 |
| :---- | :---- |
| **Use Case Name** | Social Engagement Analytics  |
| **Actor(s)** | Contributor, Moderator, Administrator.  |
| **Precondition(s)** | The actor is authenticated. At least one submission has been published to generate meaningful analytics data.  |
| **Main Flow** | The actor navigates to the Analytics dashboard. The system displays two structurally distinct categories of data: **Workflow metrics** (posting frequency, submission-to-publish duration, content completeness rate, approval turnaround time) are computed live, directly from the database, on every dashboard load — these are always current to the second. **Facebook engagement metrics** (reach, reactions, comments, shares) are read from a periodically-refreshed cache, not fetched live from Facebook on every page load. The dashboard always displays the most recent successfully cached figures, alongside an indication of when that cache was last refreshed. **Contributor view:** workflow and engagement metrics scoped to their own institution's published posts only. **Moderator and Administrator view:** identical to each other — both see network-wide metrics across all institutions, with filtering by institution, date range, and content category, plus operational metrics not shown to Contributors: AI feature performance and approval-turnaround-time statistics. The actor may apply filters to narrow the displayed data. The actor may export the current view. Workflow metrics reflect new activity immediately on next load; engagement metrics reflect Facebook's actual figures only as of the last cache refresh, not the current moment. |
| **Alternative Flow(s)** | **A1 — Insufficient Data for Metric:** A filter combination with no published posts displays an empty state rather than an error. **A2 — Facebook Engagement Data Not Yet Cached:** A recently published post that hasn't yet been included in a cache refresh cycle displays with an "engagement data pending" indicator, rather than showing zero or a stale figure from before it existed. **A3 — Export Failure:** The system displays an inline error and allows retry without losing the current filter state. **A4 — Cross-Institution Access Attempt (Contributor):** A Contributor attempting to access another institution's analytics via a direct request receives an authorization error; their view is always scoped to their own institution server-side, regardless of any parameters in the request. |
| **Postcondition(s)** | The actor views analytics accurately scoped to their role. Workflow metrics are current as of the moment of viewing. Facebook engagement metrics are current as of the most recent cache refresh, clearly timestamped so the actor can judge their freshness rather than mistaking a cached figure for a live one.  |

##### 

      3. ##### ***Activity Diagram*** {#activity-diagram-18}

      4. ##### ***Wireframe*** {#wireframe-18}

   5. #### ***System Health Analytics***  {#system-health-analytics}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-19}

      2. ##### ***Use Case Description*** {#use-case-description-19}

| Use Case ID | UC-3.5 |
| :---- | :---- |
| **Use Case Name** | System Health Analytics  |
| **Actor(s)** | Administrator |
| **Precondition(s)** | The actor is authenticated with Administrator privileges. All endpoints on this screen are restricted to the Administrator role.  |
| **Main Flow** | The actor navigates to System Health from the admin console. This screen is distinct from both the Analytics dashboard and the Resolution Center. The screen displays current infrastructure and operational health, grouped into four sections: **Storage capacity.** Database storage is measured directly from the database engine; media storage is measured via a live scan of the object storage bucket (including derivatives and orphaned objects), with a database-derived estimate used as a fallback if that scan fails. Both are shown against configurable platform-tier limits, defaulting to the current free-tier caps for the underlying database and object storage providers. **External API connection status**, for four integrations: Facebook Graph API — live token status with a real expiry countdown. Anthropic Claude and Voyage AI — reachability and API-key-configured checks (not full authenticated capability calls). Email Service Provider — reachability check against the configured mail API. **Background job health**, covering 15 tracked jobs: Publishing Scheduler, Review Lock Cleanup, Stale Submission Detector, Abandonment Detector, Token Publishing Escalation, Validation Deadline Notification, Embedding Reconciliation, Social Engagement Sync, Media Asset Retention Purge, Generated Watermark Purge, Stale Draft Slot Release, Token Health Check, Scheduled Job Run Retention, Embedding Failure Digest, and Empty Schedule Warning. Each job has a known expected interval (ranging from one minute to weekly); a job is flagged stale only once its last run exceeds twice its expected interval, so infrequent jobs aren't misflagged for being quiet between runs. **Operational metrics**, computed over a rolling 30-day window: approval turnaround time, Edit & Approve rate, manual fallback resolution rate, publish success rate, and Live Event Fast-Track submission volume. Publish success rate uses the same calculation as the Analytics dashboard, so the two screens always agree on that figure. An overall page-level status rolls up the worst status across all storage, service, job, and metric rows, with counts of how many items fall into each of WARNING, UNHEALTHY, and UNAVAILABLE, shown before the Administrator reviews individual sections. The Administrator can trigger any tracked background job on demand via a per-job **Re-run** control, independent of its schedule. A dedicated shortcut is available specifically for re-running the Token Health Check job. Every re-run is audit-logged. Two jobs — Publishing Scheduler and Token Publishing Escalation — run asynchronously when manually triggered, since they may retry multiple submissions with backoff delays; the Administrator sees a "started" acknowledgment rather than an immediate result, and refreshes to see the outcome. The remaining 13 jobs are fast, database-only operations and return their result synchronously. Storage, external services, background jobs, and operational metrics all use a consistent status vocabulary — HEALTHY, WARNING, UNHEALTHY, UNAVAILABLE, or SCHEDULED — shown as an inline status indicator on each item. Storage status changes at configurable thresholds (default 80% warning, 95% critical). |
| **Alternative Flow(s)** | **A1 — Metric Unavailable.** Any metric whose underlying check fails (database unreachable, object-storage probe and fallback both fail, no Facebook token configured, an external API key missing, a job that has never run and isn't due) is shown as UNAVAILABLE — a distinct status from UNHEALTHY, never presented as a false healthy or unhealthy reading. **A2 — Storage Threshold Warning.** Storage status changes color at the configured warning/critical thresholds. This is shown as an inline indicator on the storage card itself (and reflected in the overall page status), remaining visible for as long as the condition holds. **A3 — Background Job Failure Detected.** A failed or stale job is flagged WARNING or UNHEALTHY in the job list. There is currently no dedicated notification trigger for job failures; visibility is limited to this screen. **A4 — Export System Health Snapshot.** The Administrator can export a point-in-time CSV snapshot of all storage, service, job, and metric rows. The export is audit-logged. **A5 — Token Re-authentication.** The Administrator can re-authenticate the Facebook integration via OAuth. This updates the stored token and is audit-logged. Resumption of any submissions suspended by the prior token failure, and clearing of their associated expiry-warning flags, happens per-submission as the escalation job's next scheduled check (or an individual submission's own retry) succeeds — not as an immediate effect of the re-authentication step itself, which typically completes within a few minutes. |
| **Postcondition(s)** | The actor views current, accurate infrastructure and operational health status, with threshold-based warnings on storage, background jobs, external services, and operational metrics. Any metric that cannot be computed is shown as UNAVAILABLE rather than a false reading.  |

      3. ##### ***Activity Diagram*** {#activity-diagram-19}

      4. ##### ***Wireframe*** {#wireframe-19}

   6. #### ***Audit Log Review***   {#audit-log-review}

      1. ##### ***Use Case Diagram*** {#use-case-diagram-20}

      2. ##### ***Use Case Description*** {#use-case-description-20}

| Use Case ID | UC-3.6 |
| :---- | :---- |
| **Use Case Name** | Audit Log Review  |
| **Actor(s)** | Administrator |
| **Precondition(s)** | The actor is authenticated with Administrator privileges. At least one auditable action has occurred in the system.  |
| **Main Flow** | The actor navigates to System & Audit → Audit Log from the admin console. The screen displays a table of audit entries, newest first, with these columns: Actor, Timestamp (Asia/Manila), a combined Category/Action badge, Affected Entity, and a generated one-line Summary. Each row also carries additional data available in the detail view: the actor's role, institution, and Admin Owner flag; the target entity's live/deleted state; and the client IP address and user agent recorded at the time of the action. The actor can filter the log server-side by date range, actor (by ID or free-text name/email), category, specific action code, entity type, resource ID, and a general free-text search across actor email/name/action/IP — combinable, with paginated results. **Categories** (11): Approval, Rejection, Edit & Revision, Reschedule & Override, Publishing, Account Management, Institution Management, Media Lifecycle, Configuration, Security, Other. There is no standalone "Deletion" category — asset and album deletions fall under Media Lifecycle, and user or institution deletions fall under Account Management / Institution Management respectively. **Entity types** (8): Submission, Media Asset, Media Album, User, Institution, Facebook Token, Watermark Configuration, System. Selecting an entry opens a detail view showing: Actor & Execution details (name, email, role, institution, action code, client IP) Target Entity (type, a jump link to the live record or a "Deleted/Unavailable" badge, entity ID) A generated summary, including any override or justification reason provided at the time of the action A structured diff table (Field / Previous Value / Updated Value) for actions that changed data — covering edit/caption changes, schedule changes, status transitions, and account role changes — with a raw-detail inspector available for anything outside the structured diff types. The actor can export the currently filtered results as a CSV, which is itself recorded as an audited event. |
| **Alternative Flow(s)** | **A1 — No Matching Entries.** The table shows a standard empty state when the current filters return no results. **A2 — Entry References a Deleted Entity.** If the entity an entry points to no longer exists, the table and detail view show a "Deleted/Unavailable" badge instead of a jump link. Live jump links are available for Submission, User, Media Asset, and Institution entities; Facebook Token, Watermark Configuration, and Media Album entities link to their general management screen rather than a per-entity deep link; System-typed entries have no jump link. **A3 — Export Failure.** A failed export surfaces as a toast notification. The current filter state is preserved, so the actor doesn't need to reconstruct their filters to retry. **A4 — Large Date Range Query.** The table view uses server-side pagination and is unaffected by range size. The CSV export caps at 5,000 rows; a filtered result set larger than that is truncated in the export without a user-facing warning. |
| **Postcondition(s)** | The audit log is append-only at the database level: the audit table's row-level security policy permits inserts only, with no update or delete policy defined, so entries cannot be modified or removed regardless of application-layer behavior. The only sanctioned mutations are a scheduled retention purge (see below) and a right-to-be-forgotten scrub that removes only the actor's email fields from an entry's metadata, without deleting the entry itself.  |

      3. ##### ***Activity Diagram*** {#activity-diagram-20}

      4. ##### ***Wireframe*** {#wireframe-20}

3. ## ***Business Rules and Guard Rails***

This section defines system-enforced business rules that prevent scheduling conflicts on the shared DASIG Facebook Page and enforce minimum content-quality gates before publication. Rules are enforced automatically by the application, replacing what previously required manual coordination by a single Administrator. All configurable thresholds are adjustable by the Administrator via the Guard Rail Configuration panel without requiring code changes, and are expected to be tuned based on operational experience during and after the pilot period.


1. ### ***Guard Rail Rules*** {#guard-rail-rules}

   1. #### *Hard Rules* {#hard-rules}

Hard rules are absolute. The system rejects any action that violates them and provides an explanatory message with suggested alternatives. 

| Rule ID | Rule Name | Threshold | Enforcement Point | Rationale |
| :---- | :---- | :---- | :---- | :---- |
| GR-H1 | **Conflict Buffer** | No two posts within ±30 minutes on the shared DASIG Facebook Page  | At slot selection (UC-1.8); at Administrator drag-rescheduling (UC-3.1 A2)  | Prevents audience saturation and Facebook algorithm penalties on the shared single page  |
| GR-H2 | **Minimum Lead Time** | Scheduled time must be ≥2 hours in the future  | At slot selection; re-validated at submission (UC-1.9)  | Ensures the Administrator has sufficient time to review content before publication. Does not apply to Live Event Fast-Track submissions (UC-1.5), which have no scheduled time.  |
| GR-H3 | **Maximum Lead Time** | Scheduled time must be ≤30 days in the future  | At slot selection; re-validated at submission  | Prevents calendar chaos and stale content  |
| GR-H4 | **Approval-Before-Publish Gate**  | A submission cannot be published until an Administrator has approved it (state must be `SCHEDULED`, not `PENDING_APPROVAL` or `IN_REVIEW`)  | At publishing scheduler query (UC-3.2)   | Guarantees a quality control step before public posting  |

**Configurable threshold table — Hard Rules:**

| Rule ID | Configurable | Default  |
| :---- | :---- | :---- |
| GR-H1 | Yes — conflict buffer duration (default: 30 min) | 30 minutes  |
| GR-H2 | Yes — minimum lead time (default: 2 hours) | 2 hours |
| GR-H3 | Yes — maximum lead time (default: 30 days) | 30 days |
| GR-H4 | No — structural rule, not a threshold | \-  |

#### 

2. #### *Soft Rules* {#soft-rules}

Soft rules generate warnings but allow the action to proceed without blocking. Contributors and Administrators may proceed through a soft rule warning without explicit confirmation. Hard rule violations block the action entirely for Contributors; an Administrator may override a hard rule during their own actions (e.g., drag-rescheduling, UC-3.1 A2) with a mandatory written justification, but there is no Contributor-initiated override request mechanism in this version of the system. 

| Rule ID | Rule Name | Threshold | Enforcement Point | Rationale |
| ----- | ----- | ----- | ----- | ----- |
| GR-S1 | Per-Institution Active Quota | Each institution may have at most 3 scheduled-but-unpublished posts at any time. Does not apply to Live Event Fast-Track submissions, which do not enter a scheduled-but-unpublished state. | At slot selection (UC-1.8)  | Prevents one institution from monopolizing the calendar; ensures fairness across DASIG member institutions  |
| GR-S2 | Daily Volume Cap | The DASIG Facebook Page may have at most 6 scheduled posts per calendar day across all institutions, inclusive of Live Event Fast-Track publications counted on their actual publish date. | At slot selection (UC-1.8); at approval for Fast-Track submissions (UC-2.4 A8)  | Prevents audience fatigue from burst posting on the shared page  |

**Configurable threshold table — Soft Rules:**

| Rule ID | Configurable | Default |
| ----- | ----- | ----- |
| GR-S1 | Yes — per-institution active post quota | 3 posts |
| GR-S2 | Yes — daily volume cap | 6 posts |

#### 

3. #### *Trigger Rules* {#trigger-rules}

Trigger rules cause the system to act autonomously — dispatching notifications, escalating priority, transitioning states, or performing cleanup — without human request. 

| Rule ID | Rule Name | Trigger Condition | System Action |
| ----- | ----- | ----- | ----- |
| GR-T1  | Approaching Publish Time Priority Flag  | submission.state IN ('PENDING\_APPROVAL', 'IN\_REVIEW') AND scheduled\_at \< NOW() \+ 30 minutes  | Raise the submission's priority flag in the Approval Queue (UC-2.4), sorting it above non-urgent items. No escalation notification is sent at this stage. If the submission remains unreviewed past its scheduled time, it is handled as a missed-publish case per GR-T9 and becomes available for manual resolution via the Approval Queue's Failed tab (UC-2.4 A8)  |
| GR-T2  | Slot Reservation Cleanup  | `DRAFT` submission with held slot reservation; no activity for 7 days  | Automatically release the slot reservation. The `DRAFT` record is retained in the Contributor's Submission Queue. No notification is sent.  |
| GR-T3 | Token Expiry Warning | facebook\_token.expires\_at \< NOW() \+ 7 days | Send Administrator in-app \+ email notification (T-08).  |
| GR-T4 | Token Validation Failure  | Daily token health check API call fails or returns an invalid response  | Mark token as inactive. Send Administrator in-app and email alert (T-09). Suspend automated publishing for all `SCHEDULED` submissions until the token is reauthorized (UC-3.5 A5).  |
| GR-T5 | Publishing Retry Sequence | Publishing API call (UC-3.2) fails  | Retry up to 3 times with exponential backoff (5s, 25s, 125s). If all retries fail, transition submission to `PUBLISH_FAILED` and alert Administrator (T-06).  |
| GR-T6 | High-Volume Failure Consolidation | More than 5 PUBLISH\_FAILED events within 1 hour | Consolidate Administrator alerts into a single high-priority notification (T-06, with Messenger delivery per UC-3.3 A4). Individual `PUBLISH_FAILED` records are still created and visible in the Approval Queue's Failed tab (UC-2.4 A8). Consolidation window resets after 1 hour of no new failures.  |
| GR-T7 | Embedding Reconciliation | Reconciliation`asset.embedding IS NULL AND asset.created_at < NOW() − 1 hour AND asset.asset_type IN ('image')`  | **Scope:** Image assets only. Video assets are permanently excluded from the classification and embedding pipeline supporting the media library, drafting, and album-matching features (UC-2.1, UC-1.7, UC-2.2) and will always have `embedding = NULL` by design. The asset type filter prevents the job from repeatedly attempting to embed video assets.  **Execution:** A scheduled job runs hourly, queries qualifying assets, re-fetches each asset's stored Claude Vision image description, and resubmits it to the Voyage AI API.  **On success:** the vector embedding is stored in the asset's `embedding` column; the asset becomes immediately available for semantic similarity search, including AI-Suggested Media during drafting (UC-1.7).  **On failure:** the asset remains unembedded; `reconciliation_attempt_count` is incremented. Assets with fewer than 10 failed attempts are retried in subsequent hourly cycles. Assets reaching 10 failed attempts are flagged `EMBEDDING_FAILED` and excluded from future cycles. The Administrator receives a weekly digest notification (**T-12**) listing all `EMBEDDING_FAILED` assets with asset codes and a manual retry link from the Media Library. No user-facing error is shown to Contributors. All failures are logged in the audit log (UC-3.6).  |
| GR-T8 | Invitation Token Expiry Detection | account.state IN ('PENDING', 'PENDING\_EMAIL\_UNDELIVERED') AND account.invitation\_token\_expires\_at \< NOW()  | Transition account to `EXPIRED` state. Retain account record. Surface in the account management panel with reason: "Invitation token expired without activation." No user-facing notification is sent. The Administrator may reissue a new token at any time (`EXPIRED` → `PENDING`, per the Account State Machine). Schedule: hourly. |
| GR-T9 | Stale Submission Detection | submission.state IN ('SCHEDULED', 'PENDING\_APPROVAL', 'IN\_REVIEW') AND submission.scheduled\_at \< NOW() − INTERVAL '5 minutes'   | If `state = 'SCHEDULED'`: transition to `PUBLISH_FAILED`, reason `"missed_publish_window — server dormancy or cron failure."` If `state IN ('PENDING_APPROVAL', 'IN_REVIEW')`: transition to `MISSED_REVIEW`, reason `"scheduled time passed without Administrator review."` Alert Administrator (T-06) in both cases. Make submission available via the Approval Queue's Failed tab (UC-2.4 A8). Schedule: every 5 minutes.  |

Configurable threshold table — Trigger Rules:

| Rule ID | Configurable | Default |
| ----- | ----- | ----- |
| GR-T1 | Yes — escalation window before scheduled time | 30 minutes (linked with GR-H2) |
| GR-T2 | Yes — slot reservation inactivity window | 7 days |
| GR-T3 | Yes — token expiry advance warning window | 7 days |
| GR-T4 | No — structural check, fires on failure | — |
| GR-T5 | Yes — retry count and backoff sequence | 3 retries; 5s, 25s, 125s |
| GR-T6 | Yes — failure consolidation threshold | 5 failures per hour |
| GR-T7 | Yes — minimum asset age before reconciliation; max attempt count | 1 hour; 10 attempts |
| GR-T8 | No — fires when token expiry timestamp is crossed | — |
| GR-T9 | Yes — stale window beyond which a SCHEDULED submission is failed | 5 minutes |

#### 

4. #### *Override & Administrative Action Audit Requirements*  {#override-&-administrative-action-audit-requirements}

All hard rule overrides by an Administrator (e.g., drag-rescheduling into a conflict, UC-3.1 A2) and all Edit & Approve actions that modify submitted content before publication (UC-2.4 A9–A10) are recorded in the immutable audit log with the following fields: 

| Field | Description |
| ----- | ----- |
| Actor | User ID and name of the Administrator performing the action |
| Action type | Reschedule Override, Edit & Approve, or other logged action category |
| Rule(s) affected (if applicable) | Rule ID and name for any hard rule overridden (e.g., GR-H1), with threshold value at time of action |
| Before/after values | For Edit & Approve: the diff of changed fields. For reschedule overrides: original and new scheduled time |
| Written justification | Mandatory reason text entered by the Administrator before the action is confirmed |
| Timestamp | ISO 8601 |
| Affected submission ID | The submission the action applies to |

> **Audit Log Surfacing:** All entries are accessible via **System & Audit → Audit Log** (UC-3.6), viewable and filterable by Administrators, with export available for DASIG/DOST Region 7 governance reporting.

2. ### ***Application Business Rules*** {#application-business-rules}

This section consolidates operational rules extracted from the use case descriptions, collected here so that threshold values, constraints, and behavioral specifications have a single authoritative location in the SRS. 

#### 

1. #### *User Provisioning and Account Management*  {#user-provisioning-and-account-management}

   **BR-ACC-01 — Contributor Provisioning Status Restriction.** Contributor invitation (UC-1.3) is restricted to institutions in `ACTIVE` status only. Contributors may not be added to an institution that is inactive or pending setup. 

   **BR-ACC-02 — Password Complexity Requirements.** Passwords must satisfy all of the following simultaneously: minimum 8 characters; at least one uppercase letter; at least one lowercase letter; at least one digit; at least one special character. Enforced in real time with inline validation feedback per rule; the Submit button remains disabled until all requirements are met. Applies at account activation (UC-1.1/UC-1.3) and both password paths (UC-1.4 A5, A6). 

   **BR-ACC-04 — Invitation Token Validity.** Invitation Tokens expire 72 hours after generation, are single-use, and are scoped to the recipient's institution (for Contributors) or network-wide (for Administrators). A token generated for one institution's Contributor cannot activate an account at a different institution; mismatch returns a generic invalid-token error. When a token is reissued, the previous token is explicitly invalidated before the new one is generated. 

   **BR-ACC-05 — Password Reset Token Validity.** Password Reset Tokens expire 1 hour after generation and are single-use. Any previously issued, unused reset token for the same account is invalidated when a new one is generated. 

   **BR-ACC-06 — On successful login:** Contributors → Institutional Workspace (`/workspace/{institution_code}/dashboard`); Moderators and Administrators → the authenticated application workspace, with Administrator-only surfaces available only to Administrators.

   **BR-ACC-07 — Post-Login Feature Access by Role.** On first login following account activation, the system grants the following role-specific access:

| Feature | Contributor | Moderator | Administrator |
| ----- | ----- | ----- | ----- |
| Content submission form (Propose New Post) | ✓ | ✓ | ✓ (may also submit as Contributor) |
| Master Calendar | Read-only, network-wide | Network-wide, editable within moderator limits | Full edit access |
| Media Library | Institution-scoped | Network-wide where permitted | Network-wide |
| Analytics Dashboard | Institution-wide, read-only | Network-wide, no institution drilldown | Network-wide, full access with institution drilldown |
| Validation Queue | — | Full access | Full access |

   2. #### *Content Submission*   {#content-submission}

   **BR-SUB-01 — Accepted File Types and Upload Size Limit.** Accepted image formats: JPEG, PNG, WebP, GIF. Accepted video formats: MP4, MOV, WebM. Each file is validated for both MIME type and magic bytes before storage. Maximum file size: 50 MB per file. 

   **BR-SUB-02 — Media Asset Limit per Submission.** A submission may include a combined maximum of 10 media assets (device-uploaded, repository-linked, or AI-suggested). Enforced at the point of adding each asset, across all attachment methods (UC-1.7). 

   **BR-SUB-03 — Draft Slot Reservation Timeout.** A tentatively reserved slot held by a `DRAFT` submission is automatically released after 7 consecutive days of inactivity (GR-T2). The `DRAFT` record is retained with no active slot; the Contributor must reselect a slot on next open. Does not apply to Live Event Fast-Track drafts, which hold no slot. 

   **BR-SUB-04 — AI Processing Windows.** AI classification tags and caption suggestions are expected within 10 seconds of upload or request. Media recommendations (AI-Suggested Media, UC-1.7) appear within 3 seconds of event-detail input. Target windows only; if AI services are unavailable, the system degrades silently (UC-1.6 A2). 

      3. #### *Media and Asset Management*    {#media-and-asset-management}

   **BR-MED-01 — Network Media View.** Moderators and Administrators may browse the network-wide media library according to their role permissions. Contributors remain institution-scoped, apart from the shared network-default institution where explicitly exposed by the feature. Routine activation of a network view is not itself an audit event; audit entries are reserved for workflow decisions, destructive actions, and security or governance events.

   **BR-MED-02 — Asset Deletion Precedence.** Before deleting an asset: if referenced in a `PENDING_APPROVAL`, `IN_REVIEW`, or `SCHEDULED` submission, deletion is blocked. If referenced only in `DRAFT` or `NEEDS_REVISION` submissions, deletion is allowed with a confirmation prompt warning the reference will break. If referenced only in terminal-state submissions (`REJECTED`, `PUBLISHED`, `PUBLISHED_MANUAL`, `PUBLISH_FAILED`) or unreferenced, deletion is allowed with a simple confirmation. The physical file is retained in storage during the pilot period. 

         4. #### *Content Approval*    {#content-approval}

   **BR-APP-01 — Review Lock Timeout.** If the reviewer holding a review lock takes no action for 15 consecutive minutes, the submission automatically reverts from `IN_REVIEW` to `PENDING_APPROVAL` and the lock releases, available for another Moderator or Administrator (UC-2.4 A4).

   **BR-APP-02 — Revision Remarks Requirements.** Revision remarks are mandatory when requesting a revision. Minimum length: 10 characters. Maximum length: 1,000 characters. The Send Revision Request action is blocked until the minimum is met; the field stops accepting input beyond the maximum with a character count indicator. 

   **BR-APP-03 — Rejection Reason Codes.** When rejecting a submission, the Administrator must select from the following standardized reason codes. Written notes are optional for all codes except OTHER, which requires a written explanation.

| Code | Label | Written Notes |
| ----- | ----- | ----- |
| INAPPROPRIATE\_CONTENT | Content violates DASIG posting guidelines | Optional |
| OUT\_OF\_SCOPE | Content is outside DASIG's posting scope | Optional |
| DUPLICATE\_EVENT | This event has already been submitted or published | Optional |
| NO\_LONGER\_RELEVANT | The event or content is no longer relevant | Optional |
| RIGHTS\_OR\_PRIVACY | Content raises rights, consent, or privacy concerns | Optional |
| WRONG\_INSTITUTION | Content does not represent this institution | Optional |
| OTHER | Other | Required |

         5. #### *Notification Behavior*      {#notification-behavior}

   **BR-NOT-01 — Notification Delivery Timing.** In-app: within 30 seconds. Email: within 5 minutes in ≥95% of cases. Facebook Messenger (where configured, UC-3.3 A4): best-effort, subject to Meta's Messenger Platform delivery. All notifications include a direct deep-link to the affected submission, queue tab, or relevant view — never a generic dashboard link. 

   **BR-NOT-02 — Notification Bell and Read-State.** The notification bell badge increments by one for each unread in-app notification and persists across page refreshes and sessions (read-state is server-persisted). A notification is marked as read when the user clicks the item or uses Mark All Read. Opening the notification panel alone does not mark notifications as read. The badge count decrements only as individual notifications are clicked or all are bulk-marked. 

   **BR-NOT-03 — Notification Panel Behavior.** The panel displays the 50 most recent notifications per user, newest first. View All Notifications opens a paginated full-history view. Notifications are retained in-panel for 90 days, then archived. The full notification audit log is retained indefinitely, viewable via Audit Log Review (UC-3.6). 

   **BR-NOT-04 — Notification Preferences.** Per-channel and per-event-type preference toggles are out of scope for the pilot. All notifications defined in the UC-3.3 trigger matrix (T-01–T-12) are dispatched to all specified recipients without opt-out during the pilot, except Messenger delivery, which is opt-in per eligible Moderator or Administrator who links a personal account (UC-1.4 A8). The T-01–T-12 matrix names the principal triggers and is not an exhaustive list of every notification event type implemented by the system.

         6. #### *Analytics*      {#analytics}

   **BR-ANA-01 — KPI Definitions and Formulas.**

* **VG POSTING DELAY** — `AVG(published_at − first_pending_at)` where state IN (`PUBLISHED`, `PUBLISHED_MANUAL`) within the selected period. `first_pending_at` is the first transition to `PENDING_APPROVAL` (revision cycles included). `published_at` is the automated publish timestamp, or the manually entered timestamp for `PUBLISHED_MANUAL` (UC-2.4 A8). Display: hours, or days+hours beyond 48h. Target: ≤24 hours.   
* **CONTENT COMPLETENESS** — `COUNT(posts meeting all required criteria) ÷ COUNT(all published posts) × 100%`. Required fields: non-empty event title, non-null event date, non-empty caption, at least one valid media asset. Target: ≥95%.  
* **TOTAL POSTS PUBLISHED** — `COUNT(PUBLISHED) + COUNT(PUBLISHED_MANUAL)` within the selected period. Target: ≥4 posts per institution per month.

  **BR-ANA-02 — Period-over-Period Delta Comparison Periods.**

| Selected Range | Comparison Period |
| ----- | ----- |
| 7d | Previous 7 days |
| 30d | Previous 30 days |
| 90d | Previous 90 days |
| YTD | Jan 1 to same date, prior calendar year |

  If no data exists for the comparison period, the delta displays "N/A." 

  **BR-ANA-03 — Sparkline Behavior.** Each KPI tile shows a 12-week sparkline. For deployments with fewer than 12 weeks of data, only weeks with actual data are rendered; weeks prior to deployment are shown as blank (not zero) to avoid misleading trend appearance. A tooltip on the earliest data point shows the system deployment date. Once 12 full weeks of data have accumulated, the sparkline shows the standard 12-week rolling window. 

  **BR-ANA-04 — AI Performance Metrics Scope by Role.**

| Metric | Contributor | Administrator |
| :---- | :---- | :---- |
| AI caption acceptance rate | Own submissions only | Network-wide (filterable) |
| AI tag manual-correction rate | Own submissions only | Network-wide (filterable) |

* Minimum threshold for display: 20 AI caption suggestion events within the selected period/scope; otherwise dimmed with an insufficient-data notice. 

  **BR-ANA-05 — Operational Health Metrics (Administrator Only).**

| Metric | Definition | Target |
| ----- | ----- | ----- |
| Approval turnaround time  | Average time from a submission's first `PENDING_APPROVAL` timestamp to its Approve/Reject/Request Revision decision  | Decreasing trend  |
| Edited approval rate  | % of approved submissions that included at least one Administrator edit during review before approva  | Informational, no target  |
| Manual fallback resolution rate  | Count of `PUBLISH_FAILED` submissions resolved via the Approval Queue's Failed tab (UC-2.4 A8) per week  | Decreasing trend  |
| Missed review rate | Count of submissions transitioned to `MISSED_REVIEW` (GR-T9) per week | Decreasing trend  |
| Publishing success rate | PUBLISHED ÷ (PUBLISHED \+ PUBLISH\_FAILED) | ≥ 98% |
| Live Event Fast-Track volume  | Count of Fast-Track submissions approved and published per week  | Informational, no target  |

  These metrics are not visible to Contributors or Validators. 

  **BR-ANA-06 — Full Report Submission-Level Table Column Visibility.**

| Column | Contributor | Administrator |
| ----- | ----- | ----- |
| Submission ID | ✓ | ✓ |
| Event title | ✓ | ✓ |
| Contributor name | — | ✓ |
| Institution | — | ✓ |
| First submitted date | ✓ | ✓ |
| Published date | ✓ | ✓ |
| Publication state | ✓ | ✓ |
| Posting delay (hours) | ✓ | ✓ |
| Completeness | ✓ | ✓ |
| Revision cycles count | — | ✓ |

  Exported CSV files are named: DASIGConnect\_Analytics\_\[Role\]\_\[Institution or Network\]\_\[DateRange\].csv. 

  7. #### *Publishing*      {#publishing}

  **BR-PUB-01 — Calendar Card State Specification.**

| Submission State | Card Label | Visual Indicator |
| ----- | ----- | ----- |
| PENDING\_APPROVAL | "\[Institution\] — Pending Approval" | Light grey, dashed border |
| NEEDS\_REVISION | "\[Institution\] — Needs Revision" | Amber, dashed border  |
| SCHEDULED | "\[Institution\] — \[Title\]" | Institution color, solid border |
| PUBLISHED | "\[Institution\] — \[Title\]" | Green, solid border |
| PUBLISHED\_MANUAL | "\[Institution\] — \[Title\] (Manual)" | Teal, solid border |
| PUBLISH\_FAILED | "\[Institution\] — \[Title\] (Failed)" | Red, solid border |

  Live Event Fast-Track submissions do not appear with a reserved slot (UC-3.1 A4); if shown, they appear as an unscheduled marker on their publish date. Each card shows institution badge, title (truncated to 40 characters), and scheduled time. . 

  **BR-PUB-02 — Publishing Scheduler Windows.** The primary publishing scheduler runs every minute and picks up SCHEDULED submissions whose scheduled time falls within a 5-minute lookback window, accounting for brief server lag or cron delay. The secondary stale-submission scheduler (GR-T9) runs every 5 minutes and detects SCHEDULED submissions missed beyond the 5-minute window, transitioning them to PUBLISH\_FAILED. 

  **BR-PUB-03 — Automated Publishing Asset Type Support.**

| Asset Type | Publishing Path |
| ----- | ----- |
| Image-only (JPEG, PNG, WebP, GIF) | Multi-photo two-step method: stage each image individually, then publish a single feed post with all photo IDs attached |
| Video-only (MP4, MOV, WebM) | Single-call video publish method via /{PAGE\_ID}/videos |
| Mixed (images \+ video in same submission) | Mixed-media submissions transition to `PUBLISH_FAILED`; recovery via the Approval Queue's Failed tab (UC-2.4 A8).  |

  **BR-PUB-04 — Manual Publishing Audit Log Fields.** The audit log entry on `PUBLISHED_MANUAL` transition includes: Administrator identity, submission ID, original scheduled time (if any), `PUBLISHED_MANUAL` timestamp, platform post URL, written notes, and prior state (`PUBLISH_FAILED`). 

  **BR-PUB-05 — Manual Publishing Analytics Impact.** `PUBLISHED_MANUAL` submissions are included in all three primary KPIs (BR-ANA-01): counted in TOTAL POSTS PUBLISHED, included in AVG POSTING DELAY, and evaluated in CONTENT COMPLETENESS. 

     8. #### *AI Features*     {#ai-features}

  **BR-AI-01 — Caption Generation Prompt Parameters.**

| Parameter | Value |
| ----- | ----- |
| Language | English |
| Role instruction | Social media content writer for a Philippine government-affiliated academic network (DOST DASIG)  |
| Task | Generate 1–3 Facebook caption variants for the provided image(s), event context, and the Contributor's optional prompt  |
| Tone variants | Default (no prompt): (1) Professional, (2) Community, (3) Energetic. If a Contributor prompt is provided, variants are generated according to the prompt's guidance instead of the fixed three tones.  |
| Length guidance | 80–280 characters per variant |
| Output format | JSON array only: `[{"tone": "...", "caption": "..."}]`  |
| Safety constraints | No profanity, hate speech, discriminatory language, political opinions, unsupported factual claims, or unrelated commercial endorsements; appropriate for a government-affiliated academic audience  |
| Context inputs | Base64-encoded image(s), event title (empty string if unfilled), AI tags (empty array if not yet generated), Contributor prompt text (empty string if not provided)  |

  **BR-AI-02 — Caption Suggestion Re-generate Debounce.** Re-generate requests are debounced at 3 seconds. Repeated clicks within 3 seconds of a prior call trigger only one request. A loading indicator is shown during the debounce and API call period. 

  **BR-AI-03 — Caption Interaction Logging Schema.** Each caption suggestion interaction is logged with: whether a suggestion was shown, action taken (use / use\_then\_edited / edit / re-generate / dismiss), tone selected if applicable, submission ID, and timestamp. If the Contributor applies a variant and subsequently modifies the caption field before submitting, the logged action is updated from use to use\_then\_edited to distinguish clean acceptance from accepted-but-revised for KPI accuracy. 

  **BR-AI-04 — Image Classification Prompt Parameters.**

| Parameter | Value |
| ----- | ----- |
| Task | Classify the image into exactly one of 8 predefined categories and provide a brief factual image description  |
| Category taxonomy | Research, Awards and Recognition, Events, Community Outreach, Partnerships, Youth Programs, Media Coverage, Administrative  |
| Output format | JSON only — no prose or markdown: `{"category": "...", "confidence_score": 0.00, "image_description": "..."}`  |
| Confidence score | Float between 0.00 and 1.00 representing classification certainty  |
| Image description | 1–3 sentences in English describing the image content factually and specifically. Used as input for embedding generation — specificity improves semantic search quality.  |
| Fallback rule | Always return the closest matching category even at low confidence. Never return null or an empty category field.  |
| Safety constraint | Descriptions must not include personal information beyond visible roles or institutional contexts (e.g., "faculty member presenting an award" is acceptable; a person's full name is not, unless visible on a banner or placard).  |
| Language | English |

  **BR-AI-05 — Media Recommendation Similarity Parameters.**

| Parameter | Value |
| ----- | ----- |
| Similarity metric | Cosine similarity via pgvector cosine distance operator |
| Relevance threshold | Cosine distance ≤ 0.35 (equivalent to cosine similarity ≥ 0.65) |
| Search scope | Institution-scoped.  |
| Result count | Top 5 assets above threshold, ordered by ascending cosine distance |
| Title input debounce | 500ms inactivity after each keystroke |
| Panel refresh trigger | Event details change by 10+ characters from the last query, subject to the same debounce  |
| Configurability | Threshold configurable post-pilot based on recommendation quality evaluation  |

  **BR-AI-06 — Recommendation Interaction Logging Schema.** Each recommendation panel interaction is logged with: event type (recommendation\_shown / asset\_previewed / asset\_added / asset\_ignored), asset ID if applicable, cosine distance, event title text at time of query, submission ID, and timestamp.

  **BR-AI-07 — Caption Generation Rate Limit.** AI caption generation endpoints are rate-limited to 30 requests per hour per user. 

  4. ## ***Non-functional requirements***

     1. ### ***Performance***

* Content submission form response time shall not exceed 3 seconds under normal operating load (up to 20 concurrent users).

* Master Calendar load time shall not exceed 2 seconds for displaying a 30-day window with up to 200 scheduled events.

* Guard rail validation (conflict check, quota check, lead time check) shall complete within 500 milliseconds at slot selection time, providing real-time feedback to the Contributor.

* Media asset search shall return results in ≤2 seconds for a media library of up to 1,000 assets.

* AI caption generation shall complete and display one caption for the selected tone within 10 seconds of the user clicking "Suggest Caption," in ≥90% of cases where the service is available.

* AI image classification shall complete within 10 seconds of image upload in ≥90% of cases.

* Media recommendation results (AI-Suggested Media, UC-1.7) shall be returned and displayed within 3 seconds of qualifying event-detail input, debounced at 500ms.

* Automated post publishing shall occur within ±5 minutes of the scheduled time in ≥95% of scheduled publications, subject to the keep-alive mitigation specified in Section 2.3 being active. During any period where the mitigation is inactive, missed publish windows shall surface as `PUBLISH_FAILED` items and are resolved via the Approval Queue's Failed tab (UC-2.4 A8). Missed publications due to documented server dormancy do not count against the ±5 minute SLA for pilot evaluation purposes, provided they are resolved within 30 minutes of the missed window.

* In-app notification badges shall update within 30 seconds of a triggering event.

* Email notifications shall be dispatched within 5 minutes of a triggering event in ≥95% of cases.

* The analytics summary shall reflect updated workflow metrics within the 60-second cache lifetime after a relevant system event (submission, approval, publication). Facebook engagement metrics are subject to the latest successful synchronization.

* The Approaching Publish Time Priority Flag (GR-T1) shall be evaluated and applied within 60 seconds of the 30-minute threshold being crossed.

  2. ### ***Security***

* All client-server communication shall be encrypted via HTTPS using TLS 1.2 or higher. Unencrypted HTTP connections shall be rejected.  
* User authentication shall use a secure token-based mechanism (JWT). Tokens shall have a defined expiration period of 8 hours of inactivity (the inactivity timer resets on every authenticated API call). Tokens shall be invalidated upon explicit logout. A token refresh endpoint shall be provided to allow silent re-issuance of a new JWT without requiring credential re-entry; this endpoint requires a valid, non-expired JWT and shall be rate-limited to prevent abuse (max 10 refresh calls per hour per user). Session expiry handling (warning banner before expiry, re-authentication prompt, draft recovery) is defined in UC-1.4 A7 and applies to all authenticated users across all roles.  
* Invitation Tokens shall be generated as cryptographically random 32-byte values, encoded as a 64-character lowercase hexadecimal string (or equivalent URL-safe encoding). Only a SHA-256 hash of the token shall be stored in the database; the raw token shall be transmitted in the activation email and never persisted. Contributor tokens shall be scoped to the target institution and shall not be usable to activate accounts at a different institution.  
* Passwords shall be hashed using bcrypt or an equivalent industry-standard adaptive hashing algorithm with cost factor ≥12. Plaintext passwords shall never be stored.  
* All API endpoints shall enforce role-based access control. Requests that do not match the required role for an endpoint shall return a 403 Forbidden response.  
* Tenant scope (institution data isolation) shall be enforced at the database level via row-level security policies and at the application interceptor level. No user shall be able to access submissions, media assets, or workspace data belonging to a different institution.  
* The Facebook Page Access Token shall be encrypted at rest using AES-256-GCM as specified in Section 3.1.2. The encryption key (`FB_TOKEN_ENCRYPTION_KEY`) shall be stored as an environment variable and shall never be committed to version control, included in the repository, or exposed in any API response, log output, or error message. In a production environment beyond the pilot, this key shall be migrated to a dedicated secrets management service.  
* File uploads shall be validated for MIME type AND file content magic bytes (not just declared MIME type) before storage. Only image (JPEG, PNG, WebP, GIF) and video (MP4, MOV, WebM) file types shall be accepted. Maximum file size limit: 50 MB per file.  
* Rate limiting shall be applied as follows:  
  * Login endpoints: 5 attempts per 15 minutes per IP address. Account lockout after 5 consecutive failed attempts within 15 minutes; lockout duration 15 minutes.  
  * AI endpoints (on-demand only): 30 requests per hour per user, applied exclusively to user-initiated AI calls (caption generation and re-generate, UC-1.6). This protects against API cost abuse. When the limit is reached, "Suggest Caption" and "Re-generate" are disabled with a message indicating the time until reset. Automatic AI calls triggered by system events — image classification on upload and media recommendation queries on event-detail input (UC-1.7) — are not subject to the per-user rate limit and are governed by the API provider's own limits. If automatic classification calls approach provider-level limits, the GR-T7 reconciliation job provides recovery for assets that fail embedding generation.  
  * Client-side debounce for AI buttons: "Suggest Caption" and "Re-generate" shall implement a 3-second client-side debounce; repeated clicks within 3 seconds trigger only one request, with a loading indicator shown throughout.  
  * Upload endpoints: 20 uploads per 15 minutes per user.  
  * All other endpoints: 100 requests per 15 minutes per user.  
* Account lockout shall be enforced after 5 consecutive failed login attempts within 15 minutes; lockout duration is 15 minutes. The failed-attempt counter resets after the lockout period expires or upon a successful login. The user receives an email alert and the system displays a lockout message with the remaining duration.  
* All state-changing actions (submissions, approvals, rejections, edits, publications, reschedule overrides) shall be recorded in an immutable audit log including actor ID, action, timestamp, IP address, and resource identifier.  
* Hard rule overrides during Administrator rescheduling (UC-3.1 A2) and Edit & Approve actions (UC-2.4 A9–A10) shall require explicit written justification and shall be subject to the audit fields defined in the Override & Administrative Action Audit Requirements.

  3. ### ***Reliability***

* The system shall target ≥95% uptime during the pilot deployment period within the academic semester.  
* All database write operations for submission state changes and slot reservations shall be executed within ACID-compliant transactions to ensure data integrity.  
* The publishing workflow shall be able to continue via manual recovery (UC-2.4 A8) in the event of sustained Facebook Graph API unavailability.  
* The system shall handle Facebook Graph API failures gracefully: automated publishing failures shall trigger up to three retry attempts with exponential backoff (5s, 25s, 125s) per GR-T5. If all retries fail, the submission transitions to `PUBLISH_FAILED` status and the Administrator receives in-app and email alerts (T-06). Manual resolution becomes available via the Approval Queue's Failed tab (UC-2.4 A8).  
* The system shall handle AI service unavailability (caption generation, image classification, embedding) gracefully without displaying errors to the user. All AI features degrade silently, and the submission workflow shall remain fully functional without AI assistance.  
* Email notification delivery failures shall be retried up to three times at 1-minute intervals and logged for administrative review. In-app notifications shall function independently of email delivery.  
* Slot reservations shall be guaranteed atomic: two Contributors attempting to reserve the same slot simultaneously shall result in only one successful reservation, with the second receiving a clear "slot no longer available" message and a list of alternatives (UC-1.9 A4).  
* The Approaching Publish Time Priority Flag (GR-T1) shall ensure no submission's urgency is silently missed: submissions nearing their scheduled time without a review decision are surfaced at the top of the Approval Queue (UC-2.4). If a submission's scheduled time passes without a decision, it is handled as a missed-publish case (GR-T9) and becomes available for manual resolution (UC-2.4 A8).  
* The system shall implement database backup procedures to prevent data loss. Backup frequency shall be at minimum once per day during the deployment period (provided automatically by Supabase managed backups).  
* Background cron jobs shall be monitored and restarted automatically on failure. Job execution history shall be logged for audit and debugging. The full set of monitored jobs is:  
  * Publishing scheduler (every minute — UC-3.2)  
  * Stale-submission detection (every 5 minutes — GR-T9)  
  * Notification queue processor  
  * Token health check (daily — GR-T4)  
  * Embedding reconciliation (hourly — GR-T7)  
  * Invitation token expiry detection (hourly — GR-T8)  
* The system shall automatically attempt cleanup of orphaned staged Facebook photos (photos uploaded via the two-step publishing method where the subsequent feed post call failed) via `DELETE /{photo_id}` before transitioning a submission to `PUBLISH_FAILED`. If cleanup fails, the `photo_id` shall be recorded in the audit log and surfaced to the Administrator in a consolidated alert, ensuring no silent accumulation of orphaned content on the DASIG Facebook Page (UC-3.2 A1). 

  4. ### ***Scalability*** {#scalability}

* The system shall support at least 50 concurrent users without degradation in primary workflow performance.

* The system shall support at least 12 active member institutions, with the design accommodating future expansion to 25 institutions without architectural changes.

* The database shall support at least 10,000 submissions and 50,000 media assets without query performance degradation, given appropriate indexes on: `submissions.institution_id`, `submissions.state`, `submissions.scheduled_at`, `assets.institution_id`, `assets.embedding` (pgvector IVFFlat or HNSW index), `users.institution_id`, and `notification_logs.recipient_id`.

* AI feature usage shall scale with the configured rate limits; cost is bounded by the per-user rate limits defined above.

  5. ### ***Maintainability and Auditability*** {#maintainability-and-auditability}

* All system actions involving state changes shall be auditable through the audit log table, viewable via Audit Log Review (UC-3.6).

* Operational health metrics (approval turnaround time, Edit & Approve rate, manual fallback resolution rate, publishing success rate, Live Event Fast-Track volume) shall be exposed in the analytics dashboard for governance review (UC-3.4).

* Guard rail thresholds (conflict buffer duration, quota limits, lead-time bounds, daily volume cap, trigger rule thresholds) shall be configurable by the Administrator without requiring code changes, supporting iterative tuning based on operational experience. These thresholds are exposed in a Guard Rail Configuration panel within Page Settings (UC-1.4). Each threshold displays its current value, a valid input range, and a plain-language description of its operational effect. All threshold changes are recorded in the immutable audit log with the previous value, new value, Administrator identity, and timestamp. Changes take effect immediately on save and apply to all subsequent slot selections and guard rail evaluations network-wide.

* The system architecture (three-layer self-service / automation / exception model) is documented to support future maintainers in understanding the design philosophy and making consistent enhancement decisions.

  6. ### ***Data Retention*** {#data-retention}

* Submission records and media asset metadata shall be retained for the duration of the pilot deployment period plus six months.  
* In-app notification panel entries shall be displayed for 90 days from dispatch, after which they shall be archived and accessible only via the paginated full-history view (UC-3.3). This is a display retention policy, not a deletion policy; the underlying notification audit log is retained separately per the rule below.  
* Notification audit log entries (event type, target user ID, channel, message template ID, timestamp, delivery status) shall be retained for the duration of the pilot deployment period plus six months, consistent with other audit log entries.  
* Audit log entries (state changes, hard rule overrides, access events, guard rail threshold changes) shall be retained for the duration of the deployment period plus one year.  
* Raw media files stored in Supabase Storage shall be retained until manually deleted by an Administrator or until the project's Supabase instance is decommissioned. No automated deletion of user-submitted media files shall occur without explicit Administrator action during the pilot period.

  7. ### ***Accessibility*** {#accessibility}

* The system's web interface shall conform to WCAG 2.1 Level AA success criteria for all primary user-facing pages, including: the login page, content submission form, validation queue, master calendar, media library, and analytics dashboard.  
* All form fields shall include descriptive, programmatically associated labels.  
* All informational images shall include meaningful alternative text. Decorative images shall use empty alt attributes (`alt=""`).  
* Color shall not be used as the sole means of conveying information. Submission status badges shall include both a color indicator and a text label (e.g., "PENDING" text alongside the yellow badge).  
* All interactive elements (buttons, links, form inputs) shall be keyboard-navigable and shall display a visible focus indicator.  
* The system shall not rely on functionality that requires a specific input device. Drag-and-drop on the Master Calendar (Administrator rescheduling — UC-3.1 A2) shall have a keyboard-accessible alternative: a "Reschedule" button on each post card that opens a date-time picker dialog. The dialog shall be fully keyboard-navigable and shall display guard rail validation feedback inline before confirmation.

  8. ### ***Usability*** {#usability}

* The system shall achieve a minimum System Usability Scale (SUS) score of ≥68 (the "above average" threshold per Bangor, Kortum, and Miller, 2009\) as measured through formal SUS evaluation during the pilot deployment window.  
* SUS evaluation shall be conducted with a minimum of 10 participants drawn from the pilot user population (Contributors and Administrators combined).  
* Participants shall complete evaluation tasks covering the primary workflows: content submission, content approval, and master calendar access.  
* A SUS score below 68 shall trigger a mandatory usability review and targeted revision cycle before any full deployment proceeds.  
* SUS scores shall be recorded per role (Contributor, Administrator) in addition to the aggregate score, to identify role-specific usability issues.
* The system shall provide optional, contextual, view-only onboarding guides for supported screens. Guides shall not mutate submissions, review decisions, or workflow state. The system shall persist the user's guide-enabled preference and dismissed-screen identifiers per account, synchronize those preferences through the authenticated API, and provide a way to disable or reset the guides.

  5. ## ***Tenant Isolation (Multi-Institution Data Separation)*** {#tenant-isolation-(multi-institution-data-separation)}

Because DASIGConnect is a multi-tenant system serving multiple independent institutions, the most critical security boundary in the system is the strict separation of data between institutions. A Contributor at CIT-U must never see a submission from Silliman; a Contributor at USC must never see media from CIT-U. This subsection defines how tenant isolation is enforced. 

1. ### ***Core Principle*** {#core-principle}

Every Contributor user is permanently associated with exactly one institution at the time of account creation, recorded as `users.institution_id`. Every submission, asset, and institution-scoped record carries an `institution_id` foreign key. The system enforces that users can only access records whose `institution_id` matches their own.

Moderators and Administrators have `institution_id = NULL`, signifying network-wide authority. They bypass institution tenant filters by design according to their role permissions; Contributors remain institution-scoped.

2. ### ***Enforcement Mechanism (Defense in Depth)*** {#enforcement-mechanism-(defense-in-depth)}

Tenant isolation is enforced through four complementary layers, ensuring that no single point of failure can result in cross-institution data leakage: 

* **Layer 1 — JWT-Based Tenant Binding.** When a user authenticates, the JWT token issued contains their `institution_id` as a verified claim, signed by the server using HS256 and a secret key (`JWT_SECRET`), making it tamper-proof. On every authenticated request, the server verifies the token signature and reads the `institution_id` claim, attaching a tenant filter to the request context before any controller logic executes. Administrators receive a null filter (full access); all other roles receive a filter restricted to their own institution.  
* **Layer 2 — Application Query Filters.** Every database query that accesses institution-scoped data (submissions, assets, notifications, audit logs) must apply the tenant filter from the request context. This is enforced through code review and a dedicated test suite that verifies every controller method respects the filter. Queries are written using parameterized statements to prevent SQL injection: `WHERE institution_id = $1`, with the value bound to the `institution_id` resolved from the authenticated user's security context.  
* **Layer 3 — PostgreSQL Row-Level Security.** As a database-level safeguard, Row-Level Security (RLS) policies are enabled on all institution-scoped tables. The policies enforce that even if a developer mistakenly omits the application-layer tenant filter, the database itself returns zero rows for cross-institution queries. The RLS policies reference application-set session variables (`app.current_institution_id` and `app.current_role`) that are set at the start of each request based on the authenticated user.  
* **Layer 4 — Server-Derived Institution Identity.** The system never trusts client-supplied `institution_id` values. When a Contributor creates a new submission, the `institution_id` of the new record is automatically set from the authenticated user's session identity, not from the request body. Even if a malicious client attempts to specify a different institution in the request payload, the server overrides it.

  3. ### ***Visibility by Role and Feature*** {#visibility-by-role-and-feature}

The following table summarizes how tenant isolation manifests at each user-facing feature:  
	

| Feature | Contributor | Moderator | Administrator |
| ----- | ----- | ----- | ----- |
| Validation Queue | No access | Network-wide | Network-wide |
| Master Calendar (timing) | Network-wide, read-only | Network-wide, editable within moderator limits | Network-wide, editable |
| Master Calendar (content detail) | Own institution only | Network-wide | Network-wide |
| Media Library | Own institution only | Network-wide | Network-wide |
| Analytics Dashboard | Institution-wide, read-only | Network-wide, no institution drilldown | Network-wide with optional institution filter |
| Audit Logs | Not accessible | Not accessible | Network-wide |
| User Management | Not accessible | Contributor invitations and permitted contributor management | Network-wide |

Master Calendar (content detail): Own institution only *(plus the "DASIG Central Visayas" network-wide default institution, visible in full to all Contributors)*.

The Master Calendar's timing layer is intentionally network-wide for read access, even for Contributors. This deliberate exception supports the self-service scheduling model: Contributors must see what slots are taken across all institutions to choose realistic times. However, full content (caption, media, description) of posts from other institutions is never returned in API responses to Contributors; Moderators and Administrators may view network-wide content according to their role permissions.

4. ### ***Cross-Institution Access Attempts*** {#cross-institution-access-attempts}

When a Contributor attempts to access a resource belonging to another institution (via URL guessing, manipulated API requests, or other means), the system responds with HTTP 404 Not Found rather than HTTP 403 Forbidden. Returning "Forbidden" would confirm the existence of the resource, which is itself an information leak across institutional boundaries. Returning "Not Found" reveals nothing about whether the resource exists. 

5. ### ***Audit Logging of Administrator Cross-Institution Actions*** {#audit-logging-of-administrator-cross-institution-actions}

Administrator actions that make workflow decisions, perform destructive operations, or change security/governance state for a specific institution — such as resolving a failed publication on an institution's behalf (UC-2.4 A8) — are recorded in the audit log with the affected institution's ID and, where applicable, the reason for the action. Routine analytics viewing, media browsing, and network-view activation are not themselves audit events.

6. ### ***Tenant Isolation Testing*** {#tenant-isolation-testing}

Tenant isolation is verified through dedicated automated tests in the integration test suite. These tests create users at multiple institutions, attempt cross-institution access through every API endpoint, and verify that:

* All query results are correctly filtered by institution.  
* All direct-access attempts by ID return 404\.  
* All write attempts to other-institution resources are rejected.  
* Administrator users correctly bypass filters when their role is verified.  
* Audit log entries are written for cross-institution Administrator actions.

These tests run on every code change in the CI pipeline and must pass before merging to the main branch.
