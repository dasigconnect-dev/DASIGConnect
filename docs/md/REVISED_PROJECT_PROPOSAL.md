# Project Proposal

## Team Information

**Project Title:** DASIGConnect

**Project Short Description:** A centralized platform for coordinating, validating, and scheduling DASIG social media content across multiple member institutions using structured role-based workflows and AI-assisted tools.

**Team Code:** 2526-sem2-it332-38

**Members:**

1. Richemmae V. Bigno
2. Jay Lord C. Bayonas
3. Chris Daniel P. Cabatana
4. Mark Anton L. Camoro
5. Lerah A. Caones

---

## PART 1: Introduction

The management of organizational social media presence has become a critical operational function in academic and research institutions. Effective and timely communication through social media platforms directly influences stakeholder awareness, institutional visibility, and community engagement (Kaplan & Haenlein, 2010). For multi-organizational networks such as the DOST Acadême–Science and Innovation Group (DASIG), which consolidates participation from multiple Higher Education Institutions (HEIs) including the Cebu Institute of Technology–University (CIT-U), Silliman University, and other member schools under DOST Region 7, the challenge of coordinating and publishing content across diverse contributors is particularly pronounced.

Preliminary observations of the DASIG Facebook page reveal recurring patterns of delayed event coverage, incomplete posts, and irregular publishing activity. Events held by member institutions are frequently reported several days after their occurrence, and some activities receive no coverage at all due to missing media assets or unclear submission responsibilities. This irregularity has resulted in reduced audience engagement and inadequate representation of the organizations' activities on the platform. The root cause of these issues can be traced not to a lack of content, but to the absence of a structured workflow for collecting, validating, and scheduling content from multiple independent sources.

Existing literature consistently demonstrates that structured content workflows and scheduling tools significantly improve the timeliness and consistency of organizational social media outputs (Turban et al., 2018). Studies on collaborative digital publishing further indicate that approval-based moderation mechanisms reduce errors and improve content quality in multi-contributor environments (O'Reilly, 2007). Additionally, research on social media management in academic organizations confirms that irregular posting patterns negatively affect institutional credibility and audience retention (Aral et al., 2013).

Recent advancements in artificial intelligence, particularly in image recognition and natural language generation, offer practical opportunities to further improve content workflow efficiency. AI-assisted caption generation — which analyzes uploaded images and any existing draft text to produce contextually relevant caption suggestions on demand — along with intelligent media suggestion drawing from the institution's media repository through semantic similarity search, can reduce the manual burden on contributors and improve the consistency of post quality without requiring significant technical expertise from end users (Radford et al., 2021; Li et al., 2022).

Despite these established findings, no documented system specifically addresses the multi-institution content coordination challenge faced by academic consortia such as DASIG, particularly one that integrates AI-assisted tools to support contributors throughout the content preparation process while preserving full editorial oversight at each institutional level.

---

## PART 2: Objectives

### General Objectives

This project aims to achieve the following general objectives, structured by module, within the duration of two academic semesters:

**1. Foundation, Access Control, and Content Submission.** To increase content submission completeness to a ≥95% completeness rate on submitted records, through a secure, multi-organization portal with three-role access control (Contributor, Validator, Administrator) supporting at least three (3) DASIG member institutions, per-institution data isolation, and a self-service content submission and scheduling workflow governed by automated guard rails — to be delivered by the end of Module 1.

**2. Validation Workflow, Notification System, and Analytics.** To eliminate unapproved content publication and reduce coordination delay, through a content validation, notification, media repository, and analytics subsystem that (a) enforces a 100% Validator-reviewed approval step before any post is scheduled for publication, (b) delivers in-app notifications within 30 seconds and email notifications within 5 minutes of state change in ≥95% of cases across 17 defined trigger events, and (c) provides a centralized searchable media repository and role-scoped analytics dashboard — achieving a System Usability Scale (SUS) score of ≥68 from at least 10 pilot users by the end of Module 2.

**3. Scheduling, Automated Publishing, and AI-Assisted Content Support.** To improve posting timeliness and reduce caption drafting effort, through a Master Calendar, automated publishing, AI-assistance, and exception-handling subsystem that (a) achieves a ≥95% scheduled-post publish success rate within ±5 minutes of the assigned time via the Facebook Graph API, (b) achieves a ≥60% contributor acceptance or accept-with-edits rate on AI caption suggestions, and (c) provides AI-assisted media suggestion and Administrator exception-handling tools — by the end of Module 3.

---

### Specific Objectives

The specific objectives below map one-to-one to the twelve system use cases (UC-1.1 through UC-3.5). They serve as the feature-specific sub-objectives that realize the three general objectives.

**1. For General Objective 1: Foundation, Access Control, and Content Submission**

**1.1 (UC-1.1 — User Provisioning, Onboarding, and Authentication).** To enable secure, independent participation from at least three distinct DASIG member institutions, by implementing three distinct access roles (Contributor, Validator, Administrator) with an invitation-token onboarding flow, password-complexity-enforced account activation, stateless JWT authentication with an 8-hour inactivity session, account lockout after repeated failed logins, and role-based routing to the appropriate workspace upon login.

**1.2 (UC-1.2 — Institution Onboarding and Workspace Provisioning).** To ensure institution-level data isolation and eliminate unauthorized cross-institution access, by provisioning an isolated workspace per institution enforced at both the application layer and the database layer via PostgreSQL Row-Level Security, managing the institution status lifecycle (inactive → pending → active), and provisioning the institution's first Validator. Subsequent day-to-day roster management (inviting, resending invitations to, and activating or deactivating Contributor accounts within the institution) is performed by that institution's Validator(s) as well as the Administrator, so routine membership administration does not require network-level intervention.

**1.3 (UC-1.3 — Content Submission and Self-Service Scheduling).** To allow contributors to prepare and self-schedule content within system-enforced rules, by developing a content submission form with server-enforced completeness validation on four mandatory fields — event title, event date, caption, and at least one media asset — rejecting incomplete submissions with an HTTP 422 response and field-level error messaging (the recommended 80–280 character Facebook caption length is enforced in the submission interface). The form supports drag-and-drop and library media attachment, auto-save of drafts, and a self-service scheduling step in which contributors select publication slots directly. Scheduling is governed by automated guard rails: Hard Rules that block conflicting slots, insufficient lead time, or excessive advance scheduling; and Soft Rules that warn but permit scheduling. Contributors may submit an override request to the Administrator for Hard Rule violations. The objective targets a verified ≥95% completeness rate on submitted records during pilot testing.

**2. For General Objective 2: Validation Workflow, Notification System, and Analytics**

**2.1 (UC-2.1 — Content Validation and Approval).** To eliminate unapproved or erroneous posts, by implementing a validation interface that enforces a 100% Validator-reviewed approval step before content is scheduled for publication. Each institution's designated Validator(s) may approve a submission (transitioning it to a scheduled state), request revisions with mandatory written remarks (10–1,000 characters), or reject submissions with a standardized reason. A review-lock mechanism prevents two Validators from reviewing the same submission concurrently and releases automatically after a 30-minute inactivity timeout. For exception scenarios — validation deadline escalation, Validator absence, or override requests — the Administrator may act as a fallback reviewer through the Resolution Center.

**2.2 (UC-2.2 — Media Repository Management).** To improve media asset retrieval speed to ≤2 seconds and increase asset reuse across submissions, by building a centralized digital media repository that (a) stores uploaded photos and videos with per-institution isolation enforced via row-level scoping, (b) supports keyword search and filtering by AI-generated category, file type, and date for libraries of up to 1,000 assets, (c) enables asset reuse via direct "Use in New Post" and "Add to Draft" actions with reuse tracked in submission records, and (d) applies a soft-delete lifecycle with a configurable retention window (default 30 days) before permanent purge, with deletion authority scoped by role.

**2.3 (UC-2.3 — System and Submission Notifications).** To reduce contributor response time and minimize workflow delays, by implementing a dual-channel notification system — in-app via Server-Sent Events for real-time delivery and email via SMTP — covering 17 defined trigger events across submission state changes, validation deadlines, publishing outcomes, guard rail override decisions, token health alerts, and institutional status transitions. In-app notifications shall appear within 30 seconds via the persistent SSE connection; email notifications (for the applicable subset of triggers) shall be delivered within 5 minutes of the triggering event in ≥95% of cases. A mandatory revision-remarks field surfaces Validator feedback to the contributor, and a revision-history block records all prior remarks chronologically.

**2.4 (UC-2.4 — Analytics Dashboard).** To improve workflow visibility, by creating a role-scoped analytics dashboard that presents metrics appropriate to each user's scope: Contributors view their own submission history and AI feature usage; Validators view institution-level submission volume, validation workload, and queue aging; Administrators view network-wide KPIs including posting frequency (posts/month), average submission-to-publish delay (days), content completeness rate (%), publishing success rate (%), and AI feature adoption. The dashboard auto-refreshes every 60 seconds, supports time range filtering (7d, 30d, 90d, YTD), and offers CSV export of detailed reports — targeting a minimum of 4 posts per month and a completeness rate of ≥95%.

**3. For General Objective 3: Scheduling, Automated Publishing, and AI-Assisted Content Support**

**3.1 (UC-3.1 — Master Calendar and Automated Publishing).** To publish approved content reliably and provide network-wide scheduling visibility, by implementing a Master Calendar that renders submissions color-coded by state with role-differentiated visibility (read-only network view for Contributors and Validators; full edit for the Administrator, including drag-and-drop rescheduling with guard rail re-evaluation and audit trail), and an automated publishing pipeline that posts due content to the connected DASIG Facebook Page via the Facebook Graph API v25.0. Image-only submissions use the verified two-step method (stage each photo unpublished, then a single feed post referencing all staged photo IDs); video-only submissions use the single-call video method. The pipeline applies an atomic publish claim to prevent duplicate posting and exponential-backoff retry (5s, 25s, 125s; maximum 3 attempts), targeting a ≥95% publish success rate within ±5 minutes of the scheduled time across at least 20 test publications during pilot testing.

**3.2 (UC-3.2 — AI Caption Generation).** To reduce caption drafting effort, by integrating an AI caption generation module using a vision-capable language model (Anthropic Claude with Vision) that, on explicit Contributor request, analyzes the attached image assets and any existing caption text to produce up to three tone-labeled caption variants (Professional, Community, Energetic) within the 80–280 character range. When existing caption text is present, the module determines whether it is a creative directive or a draft for refinement and responds accordingly; contributors may apply, edit, regenerate, or dismiss each suggestion. The endpoint is rate-limited to 30 requests per hour per user. The objective targets a ≥60% contributor acceptance or accept-with-edits rate across at least 20 evaluation submissions.

**3.3 (UC-3.3 — AI Media Suggestion).** To help contributors discover relevant existing media without manually browsing the full library, by developing an AI media suggestion feature that, given a submission's event context (title, caption, and tags), returns the top 5 related assets from the institution's repository using semantic vector similarity search (cosine similarity over 1,024-dimensional Voyage AI `voyage-4-lite` embeddings stored in a pgvector index), re-ranked using category alignment, shared tags, and recency, and presented through a dedicated "AI Suggestions" tab with similarity scores and match explanations. This feature is powered by a supporting background image-classification pipeline (Anthropic Claude with Vision) that, on upload, enriches each image with a primary content category (from a controlled vocabulary of fourteen general categories), an asset-type label, a confidence score, an AI-generated description, and descriptive tags — persisted in the media repository to drive suggestion quality and library search. The objective targets ≥70% of contributors rating the top recommendation as "relevant" or "highly relevant" in a usability test with at least 10 participants, and ≥80% classification agreement on a held-out set of at least 50 manually labeled images for the supporting pipeline.

**3.4 (UC-3.4 — Manual Publishing Fallback).** To ensure publishing continuity when automated publishing is unavailable (API failure, token expiry, missed publish window, or mixed-media submissions not supported by the automated pipeline during the pilot), by implementing a guided manual publishing fallback for failed submissions. The Administrator follows a three-step workflow — copy the approved caption and download media, open the DASIG Facebook Page, then record the live post URL and notes — within a 2-hour working session, after which the submission is marked manually published with an immutable audit record. Abandoned sessions are detected and reset automatically.

**3.5 (UC-3.5 — Administrator Exception Handling).** To resolve the exceptions that fall outside the routine self-service workflow, by implementing a central Administrator-only Resolution Center with five categories: (A) retrying or manually publishing failed posts, (B) acting as fallback reviewer for submissions whose validation deadline is escalating, (C) adjudicating contributor guard rail override requests (approve, suggest a compliant alternative slot, or deny with reason), (D) composing direct posts that bypass the standard workflow with mandatory written justification and immutable audit logging, and (E) monitoring Facebook Page Access Token health with OAuth re-authentication. Every exception action is recorded in an immutable audit log surfaced in the analytics Administrator-workload metric.

---

## Research Questions

The following research questions will guide the evaluation of the proposed system:

- To what extent does the implementation of the DASIGConnect system reduce the average posting delay (days from event to publication) compared to the pre-implementation baseline?

- By what percentage does the structured submission and validation workflow increase the content completeness rate (percentage of posts published with all required fields) compared to the baseline?

- What SUS score does the system achieve among DASIG content contributors, validators, and administrators, and does this meet the target threshold of ≥68?

- To what extent does the system increase the posting frequency (posts per month) and consistency of the DASIG Facebook page over the observed deployment period compared to pre-implementation activity?

- To what degree do the AI-assisted caption generation and media suggestion features reduce contributor effort, and how do contributors rate the relevance and quality of AI-generated suggestions?

---

## PART 3: Methods

### Proposed Solution Concept

The proposed solution is a web-based Social Media Content Workflow and Scheduling Management System designed specifically for the DASIG multi-institutional network. The system addresses the identified problem by replacing the current informal and ad hoc coordination approach with a structured, three-role digital workflow. Content contributors from each member institution — including faculty, student organization officers, and institutional communications officers — use the system to submit event content including photos, captions, event details, and optional descriptions, and self-schedule their submissions within system-enforced guard rail rules that prevent slot conflicts, enforce fair posting quotas, and validate lead times, eliminating the need for manual administrative scheduling coordination.

Each institution's designated Validator reviews submitted content through a dedicated queue interface, acquiring a review lock to prevent concurrent review, and may approve content (transitioning it to the scheduled pipeline), request revisions with written remarks, or reject submissions. Validators additionally manage their own institution's roster — inviting, resending invitations to, and activating or deactivating Contributor accounts within their workspace. The Administrator onboards new member institutions, provisions each institution's first Validator, and otherwise operates at the network level reserved for exception handling: resolving publication failures, addressing validation deadline escalations, adjudicating guard rail override requests, composing direct posts, and maintaining the Facebook integration token.

At the designated publication time, the system automatically publishes approved submissions to the DASIG Facebook Page via the Facebook Graph API using a verified two-step method for photo posts and a single-call method for video posts, with an atomic publish claim and exponential backoff retry. If automated publishing fails, the Administrator resolves the failure through a guided manual publishing fallback workflow in the Resolution Center.

To further support contributors during the content preparation stage, the system incorporates AI-assisted features in a targeted and non-intrusive manner. When a contributor explicitly requests AI assistance while composing a post, the system uses the Anthropic Claude Vision API to analyze attached images and any existing caption text, producing tone-labeled caption suggestions or refining the draft based on detected intent. The system also offers an AI media suggestion panel that surfaces related assets from the repository based on the submission's event context using semantic vector similarity powered by Voyage AI embeddings. Supporting this, a background classification pipeline automatically enriches each uploaded image with a category, asset-type label, description, and tags, which are stored in the media repository and used to improve suggestion quality and library search. These AI features reduce friction in the submission process while maintaining full editorial oversight by contributors.

### Development Methodology

The project adopted the Agile Software Development methodology, utilizing iterative sprint cycles guided by the Scrum framework. This approach was appropriate given the need for continuous stakeholder feedback from multiple member institutions and the iterative refinement of features based on real-world usage patterns. Development proceeded in two-week sprints, with each sprint producing a testable increment of the system. Sprint reviews involved designated DASIG stakeholders to validate progress and realign priorities. This methodology supported structured development, promoted early detection of functional gaps, and allowed the team to adapt to changing requirements without disrupting the overall project timeline.

### Validation Approach

The system was evaluated through a combination of functional testing, performance measurement, and user acceptance testing (UAT). Functional testing verified that each module — submission, validation, scheduling, notifications, analytics, AI assistance, and automated publishing — operates correctly under defined use cases, including the guard rail enforcement rules and exception-handling scenarios.

Performance measurement compared pre-implementation baseline data, gathered through observation of the DASIG Facebook page posting history, against post-implementation metrics. Key performance indicators measured include: (1) average posting delay, defined as the number of days between an event occurrence and its publication on the DASIG page; (2) content completeness rate, measured as the percentage of posts published with all required fields (event title, event date, caption, and at least one media asset) as recorded by the system; and (3) posting frequency, measured as the number of posts published per month.

AI feature effectiveness is evaluated through contributor feedback collected via a supplementary survey measuring perceived usefulness and suggestion relevance on a 5-point Likert scale. AI image-classification agreement (the pipeline supporting media suggestion) is assessed against a held-out evaluation set of manually labeled images. Media suggestion quality is measured through a usability test asking participants to rate the relevance of the top-ranked recommendation.

User acceptance is measured using the System Usability Scale (SUS), administered to designated DASIG contributors, validators, and administrators, targeting a minimum score of ≥68 — the "above average" usability threshold (Bangor, Kortum, & Miller, 2009). For the purpose of establishing the pre-implementation baseline, content completeness is assessed using externally observable criteria — specifically, the presence of at least one photo and a caption of at least 50 characters — to enable retrospective analysis of the DASIG Facebook page history. Post-implementation completeness additionally includes server-enforced fields as recorded by the system. This distinction is noted explicitly when comparing pre- and post-implementation completeness rates in the evaluation.

---

## PART 4: Expected System

### Minimum Viable Product (MVP) Features

The MVP of DASIGConnect includes the following core functionalities, organized by the twelve use cases:

- **(UC-1.1)** Multi-organization user accounts with three access roles (Contributor, Validator, Administrator), invitation-token onboarding, JWT authentication, and role-based routing.

- **(UC-1.2)** Per-institution isolated workspaces enforced via Row-Level Security, institution status lifecycle, and Validator-administered institution rosters.

- **(UC-1.3)** Content submission form with server-enforced mandatory-field validation, auto-save, drag-and-drop and library media attachment, and self-service slot scheduling with Hard/Soft guard rail enforcement and override requests.

- **(UC-2.1)** Per-institution Validator queue with review-lock management and approve/request-revision/reject actions with mandatory remarks.

- **(UC-2.2)** Centralized media library with per-institution isolation, search and filter by AI category and tags, asset reuse, multi-select actions, and soft-delete with a configurable retention window.

- **(UC-2.3)** Dual-channel notification system (in-app via Server-Sent Events, email via SMTP) covering 17 trigger events across the full submission and publishing lifecycle.

- **(UC-2.4)** Role-scoped analytics dashboard displaying KPIs at contributor, institution, and network levels, with time-range filtering and CSV export.

- **(UC-3.1)** Master Calendar with role-differentiated visibility and automated Facebook publishing (two-step photo method; single-call video method) with idempotent publish claiming and exponential-backoff retry.

- **(UC-3.2)** On-demand AI caption generation producing up to three tone-labeled variants with intent detection (refine draft vs. follow directive).

- **(UC-3.3)** AI media suggestion via semantic vector similarity, presented in a dedicated "AI Suggestions" tab, powered by a background image-classification and auto-tagging pipeline.

- **(UC-3.4)** Manual publishing fallback with a guided 3-step Administrator workflow and a 2-hour working session.

- **(UC-3.5)** Administrator Resolution Center for exception handling: API failures, validation timeouts, override requests, direct posts, and Facebook token management.

### High-Level System Workflow

A content Contributor from a DASIG member institution logs into the system and creates a new content submission by completing the required event details and attaching media assets (uploaded from the device or selected from the institution library). The system's AI classification pipeline runs asynchronously after each image upload, enriching the asset with a category, asset-type label, description, and tags in the media repository without blocking the submission flow. While composing the caption, the Contributor may optionally request AI caption suggestions; the system analyzes the attached images and any existing caption text to generate tone-labeled suggestions, which the Contributor may apply, edit, or discard. The Contributor may also open the AI Suggestions tab to surface semantically related media from the repository based on the event context. The Contributor then selects a preferred publication slot; the system immediately evaluates the slot against all guard rail rules and either allows it, warns, or blocks it with compliant alternatives suggested.

Upon submission, the content enters the pending queue visible to the institution's Validators. A Validator opens the submission, acquiring a review lock, and approves it (transitioning it to scheduled status and locking the slot), requests revisions with written remarks (releasing the slot), or rejects it with a reason. Approved submissions are picked up by the automated publishing scheduler at the designated time and published to the DASIG Facebook Page via the Facebook Graph API.

If automated publishing fails after all retry attempts, or if a validation deadline passes without Validator action, the Administrator is notified and resolves the exception through the Resolution Center — retrying publication, executing a manual publishing workflow, acting as a fallback Validator, managing override requests, or posting directly on behalf of an institution. The analytics dashboard continuously reflects updated metrics on submission status, publishing outcomes, AI feature usage, and workflow health, providing each role with role-appropriate visibility into the system's performance.

---

## PART 5: Discussion

### Scope

The system is scoped to support the social media content coordination operations of the DASIG network under DOST Region 7, specifically for its official Facebook page. The system accommodates content submission from a defined set of DASIG member institutions. Supported content types include event announcements, event highlights, and activity recaps, with media support for image uploads (JPEG, PNG, WebP, GIF) and video uploads (MP4, MOV, WebM).

The scheduling model is self-service: Contributors select publication slots directly, governed by system-enforced guard rails (conflict buffer, per-institution quota, daily volume cap, minimum and maximum lead time). The Master Calendar provides a network-wide scheduling view for all roles, with full edit reserved for the Administrator. The automated publishing pipeline covers image-only posts via the verified two-step Facebook Graph API method and video-only posts via the single-call video method; mixed-media submissions (images and video combined) follow the Manual Publishing Fallback workflow during the pilot period.

The AI-assisted features are limited to: (a) on-demand caption suggestion via the Anthropic Claude Vision API, which analyzes attached images and existing draft text; (b) a background image-classification and auto-tagging pipeline via the Anthropic Claude Vision API, enriching the media repository; and (c) semantic media suggestion via the Voyage AI API, which generates and queries 1,024-dimensional vector embeddings to find contextually related assets. The system does not include AI-generated images, autonomous content publishing decisions, or automated content moderation.

The notification system covers 17 defined trigger events delivered via in-app Server-Sent Events and SMTP email for applicable triggers. The analytics dashboard covers role-scoped KPIs relevant to posting timeliness, content completeness, workflow health, and AI feature adoption.

The system does not include integration with social media platforms other than the DASIG official Facebook page. Facebook API integration uses Page Access Tokens with the permissions `pages_manage_posts`, `pages_read_engagement`, and `pages_show_list`. Full public post visibility is contingent on the completion of Meta Business Verification by the DASIG organization and the transition of the application to Live mode on the Meta Developer Platform, which falls outside the scope of this capstone project. The codebase requires no changes to support this transition.

### Limitations

The project is subject to the following constraints. First, the system requires reliable internet connectivity at contributor and administrator endpoints, which may affect usability in areas with poor network infrastructure. Second, the evaluation period for post-implementation KPI measurement is limited to the available deployment window within the second semester, which may not capture long-term usage patterns. Third, the baseline data for pre-implementation metrics is gathered through retrospective observation of the DASIG Facebook page posting history, which may not fully reflect all coordination activities that occurred outside of the public page record.

Fourth, the automated publishing functionality depends on the Facebook Graph API operating under conditions governed by Meta's platform policies. During the development and evaluation phases, the system's Facebook application operates under Development mode, which restricts post visibility to authorized developer and administrator accounts on the registered Meta application. Transitioning to full public visibility requires Meta Business Verification by DASIG, a process governed by Meta's policies and contingent on DASIG's organizational decision to formally adopt the system. This transition requires no codebase changes. Fifth, the quality of AI-generated caption suggestions depends on the Anthropic Claude Vision API and may vary based on image quality, content clarity, and the specificity of event context provided; contributors retain full editorial control over all published captions. Sixth, the quality of AI media suggestions depends on the availability of the Voyage AI embedding service and the richness of the institution's indexed media library; the system falls back to metadata-ranked results when embeddings are unavailable for queried assets.

### Expected Contribution

The proposed system is expected to contribute to the DASIG network's operations by replacing the current informal, fragmented coordination approach with a structured, traceable, and efficient digital workflow. A core design contribution is the self-service scheduling model: Contributors operate within system-enforced guard rail rules that automatically enforce fair posting quotas and slot constraints, eliminating the administrative bandwidth bottleneck that previously required all scheduling decisions to flow through a single coordinator.

The primary measurable contribution is the reduction in posting delays and improvement in content completeness, directly addressing the identified operational problem. The AI-assisted caption generation and semantic media suggestion features reduce the manual effort required from contributors while maintaining editorial oversight. The role-scoped analytics dashboard provides each stakeholder tier with actionable visibility into workflow performance.

Beyond operational improvement, the system contributes a replicable workflow model applicable to other multi-institutional academic networks facing similar social media coordination challenges. From an academic standpoint, this project addresses a documented gap in existing literature by producing a purpose-built solution for multi-organization content coordination in an academic consortium context — one that integrates AI-augmented assistance with structured human-review governance, supporting future research on AI-assisted digital workflow systems in similar organizational structures.

---

## PART 6: Traceability Matrix

| RRL Finding / Theme | Identified Gap | Research Question | Proposed Function (Use Case) |
| :---- | :---- | :---- | :---- |
| Social media content scheduling improves posting consistency and audience engagement (Kaplan & Haenlein, 2010) | Lack of structured scheduling in multi-contributor organizations | To what extent does the absence of a scheduling workflow affect posting timeliness? | Self-service scheduling with guard rail enforcement (UC-1.3); Master Calendar and automated publishing via Facebook Graph API v25.0 (UC-3.1) |
| Centralized digital workflow systems reduce coordination errors in multi-stakeholder environments (Turban et al., 2018) | No centralized submission and validation process across member institutions | How does a centralized submission workflow affect content completeness and accuracy? | Submission portal with three-role access control (UC-1.1) and per-institution workspace isolation (UC-1.2) |
| Approval-based content moderation improves quality and reduces errors in collaborative publishing (O'Reilly, 2007) | No validation or approval layer before content is published | Does an approval mechanism reduce incomplete or erroneous posts? | Per-institution Validator review workflow with review locks (UC-2.1); Administrator fallback via Resolution Center (UC-3.5) |
| Inconsistent social media presence negatively affects organizational visibility and stakeholder trust (Aral et al., 2013) | DASIG page activity is irregular due to uncoordinated contributions | What is the measurable impact of the system on posting frequency and consistency? | Role-scoped analytics dashboard with KPI tracking and CSV export (UC-2.4) |
| Media asset management systems improve retrieval and reuse of organizational content (Baca, 2016) | Photos and media files are dispersed across contributors with no centralized repository | How does a centralized media repository affect content availability for posts? | Centralized media library with AI category/tag indexing, reuse actions, and soft-delete retention (UC-2.2) |
| Large language models and vision-language models can generate contextually relevant text from images (Brown et al., 2020; Radford et al., 2021) | Contributors lack support tools for drafting captions, leading to inconsistent post quality | To what degree does AI-assisted caption generation reduce drafting effort and improve contributor satisfaction? | On-demand AI caption generation with three tone-labeled variants and intent detection (UC-3.2) |
| Timely feedback mechanisms in digital workflow systems reduce bottlenecks and improve responsiveness (Turban et al., 2018) | No automated status notification system exists to alert contributors when submissions are reviewed | To what extent does automated submission-status notification reduce contributor response time and revision round-trips? | Dual-channel notification system (in-app SSE, email SMTP) covering 17 trigger events (UC-2.3) |
| The System Usability Scale provides a validated instrument for measuring perceived usability (Brooke, 1996) | No standardized usability evaluation method has been applied across diverse DASIG users | What SUS score does DASIGConnect achieve, and does it meet or exceed ≥68? | SUS-based user acceptance evaluation administered to all three roles (cross-cutting; see Validation Approach) |
| Vision-language models can assign categorical labels to images with high accuracy (Radford et al., 2021) | Uploaded media assets have no automated categorization, making retrieval and reuse inefficient | To what degree does the supporting classification pipeline achieve ≥80% agreement on a held-out set of 50 labeled images? | Background AI image classification/auto-tagging pipeline (Anthropic Claude Vision) feeding media suggestion and search (UC-3.3) |
| Dense vector representations enable semantically accurate retrieval in large content repositories (Li et al., 2022) | No intelligent media discovery mechanism exists; contributors manually browse the full library | To what degree does the AI media suggestion feature achieve ≥70% relevance rating from contributors? | AI media suggestion via Voyage AI 1,024-dimensional pgvector cosine similarity with hybrid re-ranking, in the AI Suggestions tab (UC-3.3) |

---

## PART 7: References

Aral, S., Dellarocas, C., & Godes, D. (2013). Introduction to the special issue—Social media and business transformation: A framework for research. *Information Systems Research*, *24*(1), 3–13. https://doi.org/10.1287/isre.1120.0470

Baca, M. (Ed.). (2016). *Introduction to metadata* (3rd ed.). Getty Publications.

Bangor, A., Kortum, P. T., & Miller, J. T. (2009). Determining what individual SUS scores mean: Adding an adjective rating scale. *Journal of Usability Studies*, *4*(3), 114–123.

Brooke, J. (1996). SUS: A "quick and dirty" usability scale. In P. W. Jordan, B. Thomas, B. A. Weerdmeester, & I. L. McClelland (Eds.), *Usability evaluation in industry* (pp. 189–194). Taylor & Francis.

Brown, T. B., Mann, B., Ryder, N., Subbiah, M., Kaplan, J., Dhariwal, P., ... & Amodei, D. (2020). Language models are few-shot learners. *Advances in Neural Information Processing Systems*, *33*, 1877–1901.

Kaplan, A. M., & Haenlein, M. (2010). Users of the world, unite! The challenges and opportunities of social media. *Business Horizons*, *53*(1), 59–68. https://doi.org/10.1016/j.bushor.2009.09.003

Li, J., Li, D., Xiong, C., & Hoi, S. (2022). BLIP: Bootstrapping language-image pre-training for unified vision-language understanding and generation. *Proceedings of the 39th International Conference on Machine Learning*, *162*, 12888–12900.

O'Reilly, T. (2007). What is Web 2.0: Design patterns and business models for the next generation of software. *Communications & Strategies*, *65*(1), 17–37.

Radford, A., Kim, J. W., Hallacy, C., Ramesh, A., Goh, G., Agarwal, S., ... & Sutskever, I. (2021). Learning transferable visual models from natural language supervision. *Proceedings of the 38th International Conference on Machine Learning*, *139*, 8748–8763.

Schwaber, K., & Sutherland, J. (2020). *The Scrum Guide: The definitive guide to Scrum — the rules of the game*. Scrum.org. https://scrumguides.org

Turban, E., Whiteside, J., King, D., & Outland, J. (2018). *Introduction to information systems: Supporting and transforming business* (6th ed.). Wiley.

Van der Aalst, W. M. P. (2016). *Process mining: Data science in action* (2nd ed.). Springer.
