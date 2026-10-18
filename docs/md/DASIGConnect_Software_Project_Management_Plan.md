

**CEBU INSTITUTE OF TECHNOLOGY**

**UNIVERSITY**

COLLEGE OF COMPUTER STUDIES

**Software Project Management Plan**

for

**DASIGConnect**

# **DOCUMENT CONTROL** {#document-control}

## ***Document Status*** {#document-status}

| Field | Value |
| :---- | :---- |
| Document title | Software Project Management Plan for DASIGConnect |
| Version | 1.1 |
| Status | Updated after MVP validation: actual sprint history, milestone progress, test status, and refactoring sprints for Priorities \#1–\#5 |
| Prepared by | DASIGConnect Project Team |
| Publication Date | October 16, 2026 |

## ***Revision History*** {#revision-history}

| Version | Date | Description | Authors |
| :---- | :---- | :---- | :---- |
| 1.0 | 17 Sep 2026 | Initial complete SPMP aligned with the refactored requirements. | DASIGConnect Project Team |
| 1.1 | 10 Oct 2026 | Updated project organization, budget allocation, service providers, deployment considerations, and management documentation. | DASIGConnect Project Team |
| 2.0 | 16 Oct 2026 | Replaced the schedule with the verified sprint history from Git (Sprints 1–6, PRs \#1–\#403); added milestone progress with planned-versus-actual dates; marked Refactoring Priorities, updated SRS v7.0 / RTM v2.0 / SDD v4.0 / STD v2.0 as complete; added Sprints 7–9 for Refactoring Priorities \#1–\#5, a re-validation round, and a one-month pilot with log-based measurement; added test-status metrics from the 16 Oct run; added new risks (open regression INC-11, untested GR-T1/GR-T6, priority slippage); updated the evaluation plan to use system logs for GO1, GO3, and RQ5. | DASIGConnect Project Team |

## ***Project Team*** {#project-team}

| Name | Project participation |
| :---- | :---- |
| Richemmae V. Bigno | Development team member; shared analysis, implementation, testing, documentation, and review responsibilities. |
| Jay Lord C. Bayonas | Development team member; shared analysis, implementation, testing, documentation, and review responsibilities. |
| Chris Daniel P. Cabatana | Development team member; shared analysis, implementation, testing, documentation, and review responsibilities. |
| Mark Anton L. Camoro | Development team member; shared analysis, implementation, testing, documentation, and review responsibilities. |
| Lerah A. Caones | Development team member; shared analysis, implementation, testing, documentation, and review responsibilities. |

# **TABLE OF CONTENTS** {#table-of-contents}

[DOCUMENT CONTROL	2](#document-control)

[Document Status	2](#document-status)

[Revision History	2](#revision-history)

[Project Team	2](#project-team)

[TABLE OF CONTENTS	3](#table-of-contents)

[1\. OVERVIEW	5](#1.-overview)

[1.1 Project Summary	5](#1.1-project-summary)

[1.1.1 Purpose, Scope, and Objectives	5](#1.1.1-purpose,-scope,-and-objectives)

[1.1.2 Exclusions, Assumptions, and Constraints	5](#1.1.2-exclusions,-assumptions,-and-constraints)

[1.1.3 Project Deliverables	6](#1.1.3-project-deliverables)

[1.1.3.1 Completed MVP Validation Outputs	6](#1.1.3.1-completed-mvp-validation-outputs)

[1.1.4 Schedule and Budget Summary	6](#1.1.4-schedule-and-budget-summary)

[1.2 Evolution of the Plan	7](#1.2-evolution-of-the-plan)

[2\. REFERENCES	7](#2.-references)

[3\. DEFINITIONS	8](#3.-definitions)

[4\. PROJECT ORGANIZATION	8](#4.-project-organization)

[4.1 External Structure	8](#4.1-external-structure)

[4.2 Internal Structure	8](#4.2-internal-structure)

[4.3 Roles and Responsibilities	9](#4.3-roles-and-responsibilities)

[5\. MANAGERIAL PROCESS PLANS	9](#5.-managerial-process-plans)

[5.1 Start-up Plan	9](#5.1-start-up-plan)

[5.1.1 Estimation Plan	9](#5.1.1-estimation-plan)

[5.1.2 Staffing Plan	9](#5.1.2-staffing-plan)

[5.1.3 Resource Acquisition Plan	9](#5.1.3-resource-acquisition-plan)

[5.1.4 Project Staff Training Plan	10](#5.1.4-project-staff-training-plan)

[5.2 Work Plan	10](#5.2-work-plan)

[5.2.1 Work Activities and WBS	10](#5.2.1-work-activities-and-wbs)

[5.2.2 Schedule Allocation	10](#5.2.2-schedule-allocation)

[5.2.2.1 Verified Development Phase Schedule	10](#5.2.2.1-verified-development-phase-schedule)

[5.2.3 Resource Allocation	11](#5.2.3-resource-allocation)

[5.2.4 Budget and Expense Allocation	11](#5.2.4-budget-and-expense-allocation)

[5.3 Control Plan	11](#5.3-control-plan)

[5.3.1 Requirements Control	11](#5.3.1-requirements-control)

[5.3.1.1 Validation-Driven Refactoring Control	11](#5.3.1.1-validation-driven-refactoring-control)

[5.3.2 Schedule Control	12](#5.3.2-schedule-control)

[5.3.3 Budget Control	12](#5.3.3-budget-control)

[5.3.4 Quality Control	12](#5.3.4-quality-control)

[5.3.5 Reporting Plan	12](#5.3.5-reporting-plan)

[5.3.6 Metrics Collection Plan	12](#5.3.6-metrics-collection-plan)

[5.3.7 Risk Management Plan	13](#5.3.7-risk-management-plan)

[5.3.8 Project Closeout Plan	13](#5.3.8-project-closeout-plan)

[6\. TECHNICAL PROCESS PLANS	13](#6.-technical-process-plans)

[6.1 Process Model	13](#6.1-process-model)

[6.2 Methods, Tools, and Techniques	14](#6.2-methods,-tools,-and-techniques)

[6.3 Infrastructure Plan	14](#6.3-infrastructure-plan)

[6.4 Product Acceptance Plan	14](#6.4-product-acceptance-plan)

[7\. SUPPORTING PROCESS PLANS	14](#7.-supporting-process-plans)

[7.1 Configuration Management Plan	14](#7.1-configuration-management-plan)

[7.2 Verification and Validation Plan	14](#7.2-verification-and-validation-plan)

[7.3 Documentation Plan	15](#7.3-documentation-plan)

[7.4 Quality Assurance Plan	15](#7.4-quality-assurance-plan)

[7.5 Reviews and Audits	15](#7.5-reviews-and-audits)

[7.6 Problem Resolution Plan	15](#7.6-problem-resolution-plan)

[7.7 Subcontractor Management Plan	16](#7.7-subcontractor-management-plan)

[7.8 Process Improvement Plan	16](#7.8-process-improvement-plan)

[8\. ADDITIONAL PLANS	16](#8.-additional-plans)

[8.1 Security, Privacy, and Data Governance	16](#8.1-security,-privacy,-and-data-governance)

[8.2 Deployment and Transition	16](#8.2-deployment-and-transition)

[8.3 Subscription and AI Credit Plan	16](#8.3-subscription-and-ai-credit-plan)

[9\. PLAN ANNEXES	17](#9.-plan-annexes)

[9.1 Refactoring Impact Summary	17](#9.1-refactoring-impact-summary)

[9.2 Requirements-to-Management Traceability	17](#9.2-requirements-to-management-traceability)

[9.3 Original May 2026 Module 3 Use-Case History	18](#9.3-original-may-2026-module-3-use-case-history)

[9.4 Current SRS Use-Case Responsibility Matrix	18](#9.4-current-srs-use-case-responsibility-matrix)

[9.5 Cross-Cutting Development Work	19](#9.5-cross-cutting-development-work)

[9.6 Current Management Assessment	19](#9.6-current-management-assessment)

[10\. INDEX	19](#10.-index)

# **1\. OVERVIEW** {#1.-overview}

## ***1.1 Project Summary*** {#1.1-project-summary}

DASIGConnect is a web-based social media content workflow and scheduling management system for the DOST  Academe-Science and Innovation Group (DASIG) under DOST Region 7\. It coordinates content from multiple  member institutions and replaces fragmented manual communication with a traceable workflow for submission,  moderation, scheduling, publishing, media management, notifications, analytics, and AI-assisted content  preparation.  

This SPMP is the first complete project management plan for DASIGConnect. It uses the supplied IEEE  1058-style template and incorporates verified requirements and implementation changes from the 19-29 May  2026 Module 3 baseline through 4 October 2026\. It complements the proposal, SRS, SDD, implementation plans,  test evidence, validation deliverables, and Git development-history audit.

### ***1.1.1 Purpose, Scope, and Objectives*** {#1.1.1-purpose,-scope,-and-objectives}

The project purpose is to improve the timeliness, completeness, governance, and measurability of DASIG  Facebook content operations while preserving human editorial authority. The current implementation recognizes  three roles: Contributor, Moderator, and Admin. Contributors prepare content; Moderators perform network-wide  editorial review; Admins govern accounts, institutions, configuration, exceptions, and operational oversight.  In-scope capabilities are authentication and invitation; institution and account management; content drafting and  autosave; review and revision; scheduling guard rails and calendar views; Facebook Page connection and  publishing; failed-publication resolution; media albums, search, reuse, lifecycle, and watermarking; notifications  and Messenger connection; analytics and engagement synchronization; AI captioning, classification,  embeddings, and recommendations; audit logs; system health; security; data retention; and role-aware  performance caching.  

The plan retains the measurable objectives documented in the proposal and SRS.

* Support at least three DASIG member institutions with role-based access and institution-level data  separation.  
* Achieve at least 95% complete submitted records through server-enforced fields and actionable  validation.  
* Require an explicit Moderator or Admin approval before publication.  
* Target email status delivery within five minutes in at least 95% of cases and near-real-time in-app status  updates where specified.  
* Return media searches within two seconds for a library of up to 1,000 assets.  
* Target at least 95% successful scheduled publication within plus/minus five minutes over at least 20 pilot  publications.  
* Evaluate AI caption availability, use, quality, image classification accuracy, and media recommendation  relevance against the approved proposal metrics.  
* Evaluate system usability through task completion measures and the System Usability Scale (SUS),  targeting a minimum SUS score of 70 out of 100 to demonstrate acceptable usability and user  satisfaction.

### ***1.1.2 Exclusions, Assumptions, and Constraints*** {#1.1.2-exclusions,-assumptions,-and-constraints}

The initial product is scoped to Facebook and does not provide a general multi-platform publisher. AI does not  autonomously approve or publish content and does not generate images. Hardware procurement, institutional  internet infrastructure, Meta policy decisions, and long-term production operations are outside the student team's  direct control.

* Public publishing depends on DASIG Page authorization, valid Meta permissions and tokens, suitable  app mode/review, and Meta platform policy.  
* The system depends on internet access and external hosting, database, storage, email, Facebook,  Anthropic Claude, Gemini, and Voyage AI services.  
* The available academic pilot window may not reveal long-term posting patterns.  
* Historical Facebook observations may not capture the private coordination that produced each post.  
* AI services must degrade without blocking the manual workflow.  
* Exact post-September defense, production transition, and turnover dates remain subject to the academic  calendar and stakeholder approval.

### ***1.1.3 Project Deliverables*** {#1.1.3-project-deliverables}

| Deliverable | Due date | Required evidence | Current status |
| :---- | :---- | :---- | :---- |
| MVP Validation package | 12 Sep 2026, 11:59 PM | Google Form; framework/model PDF; linked response sheet; highlights PDF; organized Google Drive evidence folder. | COMPLETED \- all five outputs |
| Updated SRS and RTM | 19 Sep 2026, 11:59 PM | Refactored SRS based on validation findings with the required Requirements Traceability Matrix incorporated. | COMPLETED |
| Updated SDD | 19 Sep 2026, 11:59 PM | Design aligned with the refactored SRS, including affected architecture, data, interfaces, and workflows. | COMPLETED |
| Updated SPMP | 19 Sep 2026, 11:59 PM | Updated scope, schedule, WBS, deliverables, resources, roles, risks, and other affected management plans. | COMPLETED |
| Refactoring Priorities | 10 Oct 2026 | Findings → objectives → requirement/design changes → expected improvement | COMPLETED |
| Software Test Documents | 10 Oct 2026, Week 9 | Test Plan, Test Cases, and Test Reports. | COMPLETED |
| Deployed System | 17 Oct 2026, Week 10 | Pilot or production deployment with test execution and usability test results. | IN PROGRESS |
| Refactoring implementation (RP-01–RP-05) | Sprints 7–9 (proposed) | Merged PRs with acceptance tests (STD Case-29, Case-30) | IN PROGRESS (RP-01 merged into dev, Case-30a PASS; RP-02 implemented on `refactor/p2-first-time-independence`, Case-30b PASS; RP-03–RP-05 scheduled) |
| Source code and migrations | Continuous | Reviewed pull requests, reproducible builds, migration chain, test evidence, and identified baselines. | GitHub development baseline |

#### ***1.1.3.1 Completed MVP Validation Outputs*** {#1.1.3.1-completed-mvp-validation-outputs}

The team confirms that the following required MVP Validation outputs are complete. These records must remain  available as the source evidence for refactoring and documentation updates.

* Google Form validation instrument aligned with the selected framework, SMART objectives, measurable  outcomes, respondent roles, and qualitative feedback needs.  
* Validation Framework or Model PDF explaining the framework, its suitability, objective-to-construct  mapping, questionnaire groups, and respondent roles.  
* Google Sheet linked to the form, with organized responses and identifiable respondent roles where  appropriate.  
* MVP Validation Highlights PDF summarizing meaningful findings, issues, recommendations, positive  aspects, and proposed improvements.  
* Organized Google Drive evidence folder containing appropriate proof of deployment, participation,  recordings, interviews, consultations, or communications.

### ***1.1.4 Schedule and Budget Summary*** {#1.1.4-schedule-and-budget-summary}

***Schedule Summary***

| Period/Milestone | Primary output | Basis |
| :---- | :---- | :---- |
| Early 2026 | Problem definition, proposal, objectives, and initial requirements. | Proposal and early repository history |
| May 2026 | SRS/SDD baseline and Module 3 implementation baseline. | SRS/SDD dated 16 May; commit ce776d2 dated 29 May |
| June-August 2026 | Requirements refactoring, workflow expansion, platform hardening, and integration work. | Git history and migrations |
| August-September 2026 | Use-case refactor, performance caching, security, account governance, media lifecycle, monitoring, regression testing, and defect correction. | Merged PRs 74-374 |
| 12 Sep 2026 | MVP Validation package completed. | Five instructor-required outputs |
| 19 Sep 2026 | Updated SRS/RTM, SDD, and SPMP milestone; document are completed | Weeks 1-6 deliverables |
| 5–17 Oct 2026 | Configurable guard rails, GR-T1/GR-T6, Admin Owner transfer fix, v3.0.0 and v3.0.1 releases, demo-readiness test and lint cleanup, controller test coverage. | Merged PRs 376-408 |
| 10 Oct 2026 | Refactoring Priorities and Software Test Documents due. | Week 9 instructor milestone |
| 17 Oct 2026 | Deployed System with test execution and usability results due. | Week 10 instructor milestone |
| 19 Oct–29 Nov 2026 (Sprints 7–9, proposed) | Refactoring Priorities \#1–\#5 | Section 5.2.5 |
| Post-deployment | Final revisions, production authorization, acceptance, transition, and turnover. | TBD \- stakeholder and academic approval |

***Budget Summary***

| Item / Service | Provider | Billing Cycle | Cost (Php) |
| :---- | :---- | :---- | :---- |
| Domain Registration | Namecheap | Yearly | ₱440.60 |
| Backend Server | Railway Hobby | 2 Months | ₱624.60 |
| Database | Supabase Pro | 2 Months | ₱3,123.00 |
| API usage | Gemini and Voyage | 2 Months | ₱1,300.00 |
| Total Projected Infrastructure Cost |  | 2 Months \+ Domain | ₱5,488.20 |
| Total Project Budget Allocation |  | Entire Project | ₱10,000.00 |
| Actual Expenses to Date |  | As of October 10, 2026 | ₱1,740.60 |
| Remaining Unspent Budget |  |  | ₱8,259.40 |

## ***1.2 Evolution of the Plan*** {#1.2-evolution-of-the-plan}

This SPMP is controlled in Git with the project documentation. Material changes to scope, schedule, roles, risks,  external integrations, acceptance criteria, or resource commitments require a documented change, team review,  and an updated version entry. Urgent updates may be made between regular reviews but must be reconciled at  the next team review.

| Trigger | Required action | Approval/record |
| :---- | :---- | :---- |
| Approved requirement change | Assess scope, schedule, risk, tests, and affected documents. | Issue/PR and updated traceability |
| Third-party policy or API change | Review permissions, data handling, fallback, and delivery impact. | Technical review and risk update |
| Milestone variance | Re-estimate remaining work and agree corrective action. | Sprint review/status record |
| Security or privacy issue | Triage immediately, contain exposure, test correction, and record lessons. | Incident/defect record and reviewer sign-off |
| Final acceptance feedback | Prioritize required changes and baseline the accepted release. | Acceptance record and release tag |
| New validation or pilot finding | Map to objective; decide priority; update SRS/SDD/STD/RTM | Refactoring record and revision row |

# **2\. REFERENCES** {#2.-references}

| Reference | Version/Date | Use in this SPMP |
| :---- | :---- | :---- |
| DASIGConnect Project Proposal | 2026 | Purpose, objectives, methodology, MVP, scope, limits, and evaluation measures. |
| MVP Validation Findings & Refactoring Priorities | 12 Oct 2026 | Findings VF-01–VF-05, Priorities RP-01–RP-05, required document updates |
| DASIGConnect Software Requirements Specification | Version 7.0, 16 October 2026 | Functional/non-functional requirements, business rules, security, reliability, and acceptance thresholds. |
| DASIGConnect Software Design Description | Version 4.0, 16 October 2026 | Architecture, modules, data, APIs, security mechanisms, and external integrations. |
| Software Test Document | v2.0, 16 Oct 2026 | Test plan, cases, 16 Oct execution results, incidents |
| Requirements Traceability Matrix | v2.0, 16 Oct 2026 | Requirement → test coverage |
| Module 3 Git baseline | ce776d2, 29 May 2026 | Pre-refactor implementation baseline. |
| Refactored dev baseline | 3976afb, 4 Oct 2026 | Current implementation comparison baseline at the Git audit date. |
| Current release and dev baseline | Release v3.0.1 (main e5d7b55, 16 Oct 2026); dev fc916d7, 17 Oct 2026 | Baseline verified for this version of the plan. |
| DASIGConnect Git Development History Audit | 4 Oct 2026 | Verified periods, use-case ownership, PR evidence, and integration milestones. |
| IT411 Weeks 1-6 Deliverables and Week 9 Notice | Sep-Oct 2026 | Required validation, SRS, SDD, SPMP, refactoring, testing, and deployment outputs and deadlines. |

# **3\. DEFINITIONS** {#3.-definitions}

| Term | Definition |
| :---- | :---- |
| Admin | Current system role responsible for network governance, privileged configuration, account/institution management, and exception handling. |
| Moderator | Current network-wide editorial role. This replaces the legacy Validator terminology used by the May baseline. |
| Contributor | User who creates, edits, schedules, and tracks content submissions. |
| DASIG | DOST Academe-Science and Innovation Group. |
| MVP | Minimum Viable Product used for structured stakeholder validation. |
| VF / RP | Validation Finding / Refactoring Priority from the 12 Oct 2026 findings document |
| Re-validation | A repeat role-task validation after refactoring, using the same ISO 9241-11 instrument plus system logs |
| SUS | System Usability Scale. |
| UAT | User Acceptance Testing. |
| RTM | Requirements Traceability Matrix. |
| STD | Software Test Documents consisting of the Test Plan, Test Cases, and Test Reports. |
| SPMP / SRS / SDD | Software Project Management Plan / Software Requirements Specification / Software Design Description. |
| Guard rail | Configured scheduling or workflow rule that blocks, warns, or governs an action. |
| Baseline | An identified version used for controlled comparison and acceptance. |

# **4\. PROJECT ORGANIZATION** {#4.-project-organization}

## ***4.1 External Structure*** {#4.1-external-structure}

The development team operates within Cebu Institute of Technology \- University, College of Computer Studies.  DASIG/DOST Region 7 and participating institutions supply operational context, user feedback, Page  authorization, and acceptance input. External service providers supply hosting, database, object storage, email,  AI, and Meta platform capabilities under their own policies and service limits.

| External entity | Interface with the project |
| :---- | :---- |
| DASIG / DOST Region 7 | Operational owner and principal stakeholder; confirms workflow, Page access, validation feedback, and transition decisions. |
| Member institutions | Supply Contributors and pilot users; provide representative content and workflow feedback. |
| CIT-U CCS / instructor / adviser | Academic governance, review, milestone evaluation, and acceptance guidance. |
| Meta / Facebook | OAuth, Page permissions, publishing, engagement data, app review, and platform policy. |
| Vercel, Railway, Supabase, Cloudflare | Frontend hosting, backend hosting, PostgreSQL service, and object storage. |
| Anthropic, Gemini, Voyage AI, Resend | AI assistance, embeddings, and email delivery. |

## ***4.2 Internal Structure*** {#4.2-internal-structure}

The DASIGConnect development team consists of five members with designated responsibilities in project  management, frontend development, backend development, Scrum coordination, and quality assurance.  Richemmae V. Bigno serves as the Team Lead and Project Manager, overseeing project planning, task  assignments, development progress, and milestone completion. Jay Lord C. Bayonas and Chris Daniel P.  Cabataña serve as Frontend and Backend Developers, respectively, while jointly fulfilling Scrum Master  responsibilities to coordinate sprint activities and monitor development progress. Mark Anton L. Camoro and  Lerah A. Caones serve as Frontend and Backend Developers, respectively, while sharing Quality Assurance  responsibilities, including software testing, defect identification, and verification.

Although each member has designated primary roles, responsibilities may be temporarily rotated or shared  depending on individual availability, workload, and project priorities to maintain development continuity and meet  deadlines. All team members collaborate through Git-based version control, peer reviews, documentation, and  iterative Agile development to ensure project quality and timely delivery.

## ***4.3 Roles and Responsibilities*** {#4.3-roles-and-responsibilities}

| Role | Responsibilities | Accountability |
| :---- | :---- | :---- |
| Project Manager / Team Lead | Oversees project planning, development progress, task assignments, technical decisions, and milestone completion. | Overall project delivery and technical direction |
| Scrum Master | Facilitates sprint planning, monitors task progress, resolves blockers, and coordinates development activities. | Sprint coordination and Agile process compliance |
| Frontend Developer | Develops user interfaces, implements frontend functionality, integrates backend APIs, and ensures responsive design. | Frontend functionality and usability |
| Backend Developer | Develops APIs, database operations, business logic, authentication, security, and external service integrations. | Backend functionality and system reliability |
| Quality Assurance (QA) | Prepares and executes test cases, identifies defects, verifies fixes, and documents testing results. | Software quality and testing compliance |
| DASIG Stakeholder Representative | Validates operational requirements, provides feedback, and coordinates Facebook Page authorization. | Operational acceptance |
| Instructor / Adviser | Evaluates project deliverables, documentation, academic compliance, and milestone completion. | Academic evaluation |
| Pilot Participants | Evaluate system functionality and usability and provide structured feedback. | User validation evidence |

| Names | Roles |
| :---- | :---- |
| Richemmae Bigno | Team Lead / Project Manager |
| Jay Lord Bayonas | Frontend Developer / Scrum Master |
| Chris Daniel Cabataña | Backend Developer / Scrum Master |
| Mark Anton Camoro | Frontend Developer / Quality Assurance |
| Lerah Caones | Backend Developer / Quality Assurance |

# **5\. MANAGERIAL PROCESS PLANS** {#5.-managerial-process-plans}

## ***5.1 Start-up Plan*** {#5.1-start-up-plan}

### ***5.1.1 Estimation Plan*** {#5.1.1-estimation-plan}

Work is decomposed into user-visible outcomes, backend/API changes, migrations, frontend behaviour, tests,  documentation, and deployment tasks. Estimates use relative complexity, historical PR size, dependency risk,  and academic deadlines, and are revised at each sprint start. Sprint history (Section 5.2.3) shows an average of  about 70 merged PRs per active two-week sprint since August; refactoring sprints are planned at lower volume  because each priority needs design, tests, and re-validation evidence.

### ***5.1.2 Staffing Plan*** {#5.1.2-staffing-plan}

All five team members remain part-time student developers. Work is assigned by competency and available  capacity while maintaining peer review and knowledge sharing. A work item should have a primary implementer  and at least one reviewer; high-risk security, migration, publishing, or tenant-isolation work requires explicit test  evidence.

### ***5.1.3 Resource Acquisition Plan*** {#5.1.3-resource-acquisition-plan}

| Resource | Acquisition/control | Contingency |
| :---- | :---- | :---- |
| Development tools and GitHub | Use existing team accounts and repository access. | Local development and repository archive. |
| Vercel / Railway/ Supabase / R2 | Use approved project environments and secret-managed configuration. | Documented redeployment and provider export/backup where available. |
| Meta Page and app access | DASIG Page administrator authorizes the app/account and required permissions. | Development/test Page and manual publishing fallback. |
| Anthropic / Gemini / Voyage AI / Resend | Enable only with approved keys, limits, and budget controls. | Manual captioning/tagging and in-app workflow remain usable. |
| Pilot participants | Recruit role-appropriate users and record consent/evidence. | Supplement with scheduled follow-up interviews where needed. |

### ***5.1.4 Project Staff Training Plan*** {#5.1.4-project-staff-training-plan}

* Review SRS/SDD, current role terminology, state transitions, and tenant boundaries before changing  workflows.  
* Use secure secret handling, migration review, least privilege, and safe test data practices.  
* Review Meta permissions, token lifecycle, app mode, publishing constraints, and fallback procedures.  
* Review React Query cache keys/invalidation and avoid cross-session or cross-role stale data.  
* Practice build, automated-test, deployment, rollback, and evidence-capture procedures before  acceptance.

## ***5.2 Work Plan*** {#5.2-work-plan}

### ***5.2.1 Work Activities and WBS*** {#5.2.1-work-activities-and-wbs}

| WBS | Work package | Principal outputs |
| :---- | :---- | :---- |
| 1.0 | Project governance | SPMP, schedule, status, risk, change and acceptance records. |
| 2.0 | Validation and refactoring | Completed validation package, findings, evidence-based priorities, and objective alignment. |
| 3.0 | Requirements and design | Updated SRS, RTM, SDD, traceability, and architecture decisions. |
| 4.0 | Identity and institutions | Authentication, invitations, account states, roles, institutions, tenant controls. |
| 5.0 | Content workflow | Drafting, autosave, submission, moderation, revision, scheduling, templates. |
| 6.0 | Publishing and communication | Facebook connection, scheduler, retries, fallback, notifications, Messenger. |
| 7.0 | Media and AI | R2 storage, library, albums, lifecycle, watermarking, captioning, classification, recommendations. |
| 8.0 | Analytics and operations | Workflow/engagement analytics, audit logs, system health, background jobs. |
| 9.0 | Quality and security | STD, automated/manual tests, security checks, performance/caching, accessibility, defects. |
| 10.0 | Deployment and transition | Pilot deployment, test/usability results, guidance, handover, and archive. |

### ***5.2.2 Schedule Allocation*** {#5.2.2-schedule-allocation}

| Milestone | Exit condition | Timing |
| :---- | :---- | :---- |
| Requirements/design baseline | Proposal, SRS, and SDD available for implementation. | May 2026 |
| Module 3 baseline | Three-module implementation merged. | 29 May 2026 |
| Refactor/hardening increments | Approved PRs merged with tests and updated behavior. | June-September 2026 |
| MVP validation package | All five required outputs complete and retained as refactoring evidence. | Completed \- 12 September 2026 milestone |
| Updated SRS, SDD, and SPMP | Validation findings traced into updated requirements, design, and management plans. | Completed \- 19 September milestone |
| Refactoring Priorities and STD | Evidence-based priorities plus Test Plan, Test Cases, and Test Reports. | 10 October 2026 |
| Deployed System | System ready for testing with execution and usability results. | 17 October 2026 |
| Refactoring implemented (RP-01–RP-05) | Priorities merged; Case-29 and Case-30 executed | RP-01 merged into dev (Case-30a PASS); RP-02 implemented (`refactor/p2-first-time-independence`, Case-30b PASS); RP-03–RP-05 scheduled |
| Re-validation and final acceptance | SUS ≥ 70; first-attempt completion ≥ 95 %; critical defects closed | TBD |
| Final acceptance and transition | Critical defects closed; evidence accepted; handover material complete. | TBD \- academic calendar |

#### ***5.2.2.1 Verified Development Phase Schedule*** {#5.2.2.1-verified-development-phase-schedule}

| Period | Verified activity | Repository evidence |
| :---- | :---- | :---- |
| 19-29 May 2026 | Repository foundation and the original 12-use-case Module 3 MVP implementation. | PRs 1-72; commit ce776d2 |
| 30 May-8 Jun 2026 | Advanced media-library/Facebook-insight experiments and UX hardening. | capstone2-advance-dev elopment history |
| 21-30 Aug 2026 | Current 21-use-case refactor, role/account redesign, workflow expansion, R2 migration, analytics, system health, and audit log. | Principal PRs 74-147 |
| 31 Aug-12 Sep 2026 | Security hardening, account and moderator corrections, caching architecture, responsive behavior, and requirements reconciliation. | PRs 145-212 |
| 13-24 Sep 2026 | Performance, pagination, cancellation, progressive loading, media lifecycle, review hardening, and analytics improvements. | PRs 213-332 |
| 24 Sep-1 Oct 2026 | Multi-image media AI, durable processing, Voyage ingestion/search/ranking, evaluation, monitoring, and metadata integration. | PRs 333-373 |
| 4 Oct 2026 | Admin/moderator transfer confirmation correction. | PR 374 |
| 8-12 Oct 2026 | Configurable posting window and guard-rail thresholds, GR-T1 priority flag and GR-T6 failure consolidation, watermarking and transaction fixes, reviewer notifications to Administrators, use-case document sync, Admin Owner transfer restricted to active Administrators. | PRs 376-396 |
| 16-17 Oct 2026 | v3.0.0 and release v3.0.1; demo readiness: backend test-suite fix, frontend lint cleanup, stale demo code removed, controller tests for all 11 previously untested controllers, refactoring evidence report, documentation consolidation. | PRs 397-408 |

Main integration milestones were Module 3 to main on 29 May; Dev PRs 152 and 154 on 1 September; v2.1 PR  224 on 13 September; v2.2 PR 257 on 15 September; v2.3.2 PR 305 on 21 September; v3.0.0 PR 397 and  release v3.0.1 on 16 October 2026\.

### ***5.2.3 Resource Allocation*** {#5.2.3-resource-allocation}

Team effort is prioritized in this order: critical security/data-integrity defects; blocking workflow defects;  acceptance-critical features; test and evidence completion; performance and maintainability improvements;  optional enhancements. External-service effort includes configuration, permission review, integration testing,  monitoring, and fallback verification.

### ***5.2.4 Budget and Expense Allocation*** {#5.2.4-budget-and-expense-allocation}

| Expense category | Planned amount | Actual amount |
| :---- | :---- | :---- |
| Hosting / database / storage | ₱ 4,000.00 | ₱ 0.00 |
| AI and external API usage | ₱ 1,500.00 | ₱ 0.00 |
| Domain / communication / email | ₱ 500.00 | ₱ 440.60 |
| Printing and documentation | ₱ 500.00 | ₱ 0.00 |
| Transportation / meetings | ₱ 1,000.00 | ₱ 500.00 |
| MVP respondent tokens / snacks | ₱ 1,000.00 | ₱ 800.00 |
| Contingency / other | ₱ 1,500.00 | ₱ 0.00 |
| TOTAL | ₱10,000.00 | ₱1,740.60 |

## ***5.3 Control Plan*** {#5.3-control-plan}

### ***5.3.1 Requirements Control*** {#5.3.1-requirements-control}

The SRS is the requirements source of record, subject to approved change. Each change is linked to an issue or  task, reviewed for role/tenant/state-transition impact, implemented on a branch, tested, and merged through a  pull request. Current implementation terminology prevails where the approved refactor intentionally supersedes  legacy names, and the affected documentation must be aligned.

#### ***5.3.1.1 Validation-Driven Refactoring Control*** {#5.3.1.1-validation-driven-refactoring-control}

Major requirements and design refactoring must be justified by MVP validation evidence and its effect on a  SMART objective or measurable outcome. The controlled trace is: MVP validation evidence; stakeholder finding;  improvement opportunity; affected objective; updated SRS requirement; updated SDD element; implementation  change; test case and result; acceptance decision.  

Accuracy, performance, efficiency, effectiveness, reliability, and outcome quality are valid refactoring targets  when supported by evidence. Bug fixes, cosmetic changes, minor UI adjustments, correction of implementation  defects, and completion of an existing requirement are not automatically treated as major refactoring. A new  feature qualifies only when evidence shows it is necessary to achieve an approved objective.

| Required question | Required record |
| :---- | :---- |
| What did stakeholders reveal? | Finding plus response, recording, observation, interview, or consultation evidence. |
| Which objective is affected? | Exact SMART objective, metric, and current/target outcome. |
| What must change? | Affected SRS requirement and SDD architecture, component, data, interface, or workflow. |
| How should the outcome improve? | Expected measurable effect, test method, test case, and acceptance condition. |

### ***5.3.2 Schedule Control*** {#5.3.2-schedule-control}

Progress is reviewed per sprint and before each milestone. Sprints 1–6 delivered every use case; two milestones  slipped by 1–2 days (Refactoring Priorities, STD v1.0). Corrective actions are reducing optional scope,  parallelizing independent work, reassigning reviewers, or invoking a documented fallback. Security and  data-integrity controls are never removed to recover time. If Sprints 7–9 slip, the order of protection is RP-01 and  RP-03 first (they gate GO1 and GO3 measurement in the pilot), then RP-02, RP-04, RP-05.

### ***5.3.3 Budget Control*** {#5.3.3-budget-control}

Only recorded, approved project expenses are entered. Paid APIs must use provider limits where available.  Cost-affecting scope changes require team review and an updated expense record. No secret, billing credential,  or full token is stored in project documents.

### ***5.3.4 Quality Control*** {#5.3.4-quality-control}

A work item is complete when its acceptance conditions are met, relevant automated/manual tests pass,  authorization and tenant scope are verified, errors and loading states are handled, documentation is updated,  and the change is reviewed. Database migrations must be forward-safe and tested against representative data.

### ***5.3.5 Reporting Plan*** {#5.3.5-reporting-plan}

| Report/event | Frequency | Audience | Content |
| :---- | :---- | :---- | :---- |
| Sprint status | At least once per sprint | Project team | Completed work, blockers, decisions, next priorities. |
| Pull request | Per change set | Implementer and reviewers | Scope, files, tests, migration/security impact, manual checks. |
| Refactoring-priority review | Per priority | Team, adviser | Evidence, objective, requirement/design impact, metric, test |
| Validation/refactoring review | Per proposed priority | Team, adviser/instructor as applicable | Evidence, objective, requirement/design impact, expected metric, test mapping. |
| Milestone review | At academic milestones | Team, instructor/adviser, stakeholders as applicable | Deliverables, evidence, variance, risk, acceptance. |
| Incident/critical defect | Immediately | Team and affected reviewer/stakeholder | Impact, containment, correction, verification, follow-up. |

### ***5.3.6 Metrics Collection Plan*** {#5.3.6-metrics-collection-plan}

| Metric | Source | Target/use |
| :---- | :---- | :---- |
| Submission completeness | Submission records at pending transition | At least 95% complete. |
| Drafts passing all required checks but not submitted within 24 h (RP-01) | Draft records and readiness state | Downward trend |
| Independent task completion (SO 1.3) | Task-event log; help requests coded from recordings | Toward 95 % |
| Notification latency | Event and delivery logs | Email within 5 minutes in at least 95% of cases; specified in-app latency. |
| Publish timeliness/success | Publication attempts and scheduled time | At least 95% within plus/minus 5 minutes over pilot set. |
| Media search latency | Measured search requests | At most 2 seconds up to 1,000 assets. |
| AI caption use/quality | AI interaction log and questionnaire | At least 60% use/use-with-edits; average at least 4.0/5.0. |
| Classification/recommendation | Held-out labels and role questionnaire | At least 80% accuracy; at least 70% top-result relevance. |
| Usability | SUS responses by role | Target a minimum System Usability Scale (SUS) score of 70 out of 100 to demonstrate acceptable system usability. |
| Defects and performance | Test runs, issue log, browser/API observations | No open critical defect at release; regression trends reviewed. |

### ***5.3.7 Risk Management Plan*** {#5.3.7-risk-management-plan}

| Risk | L/I | Mitigation and contingency | Owner |
| :---- | :---- | :---- | :---- |
| Meta authorization, app review, token, permission, or policy delay | H/H | Use a test Page in development; track required permissions; validate tokens; retain manual publishing fallback; treat production activation as stakeholder-dependent. | Integration owner / DASIG Page admin |
| Tenant or authorization defect | M/H | Defense in depth, server-derived scope, endpoint tests, RLS where configured, audit access denials, security review before release. | Backend/securit y reviewer |
| Third-party outage or latency | M/H | Timeouts, bounded retries, health monitoring, user-safe errors, queued/manual fallback, no hard dependency on optional AI. | Feature owner |
| AI output is inaccurate or unsuitable | M/M | Human review, editable/dismissible outputs, quality sampling, prompt/version records, rate/cost limits. | AI feature owner |
| Railway resource limitations, service interruptions, or delayed background-job execution | M/H | Monitor resource consumption, application health, and background-job execution; configure appropriate retry and recovery mechanisms; and maintain documented redeployment procedures. | Deployment Owner / Backend Developer |
| Migration or data-loss regression | M/H | Versioned Flyway migrations, representative tests, backup/export before risky changes, no destructive ad hoc production edits. | Backend/data reviewer |
| Cache exposes stale/cross-session data | M/H | User/role-aware query keys, mutation invalidation, logout clearing, regression tests, no sensitive persistent cache. | Frontend reviewer |
| E2E tests flaky under parallel workers (INC-11 to INC-13, INC-15); possible submit-before-autosave timing on slow devices (INC-11) | M / M | Run the E2E suite of record with one worker (33/33 pass); stabilize the three tests; in Sprint 7 check whether Submit can fire before a pending autosave completes and add a backend test for album and fastTrack persistence on save/submit | Composer owners / QA |
| Merged features without tests (GR-T1, GR-T6, PostTemplate, 11 controllers) | M / M | Add tests in Sprint 7; require STD/RTM update per feature PR. (PostTemplate and the 11 previously untested controllers were covered by PRs 404–406.) | QA |
| Refactoring priorities slip past the pilot start | H / H | Protect RP-01 and RP-03 first; run the pilot with whatever instrumentation is merged and report the gap | Project Manager |
| AI cost rises with caption-on-upload | M / M | Generate only after an image and event title exist; keep per-user limits; weekly cost review | AI feature owner |
| CI does not build the integration branch — backend-ci.yml triggers on pushes to main/develop and PRs into main; the team integrates on dev | M / M | Add dev to the push trigger (or rename the trigger branch) so every merge into dev runs the 952-test suite | QA / repository owner |
| Academic deadline / shared workload | H/M | Small PRs, visible blockers, shared review, milestone buffers, prioritize required deliverables. | Project team |

### ***5.3.8 Project Closeout Plan*** {#5.3.8-project-closeout-plan}

* Confirm required deliverables and acceptance evidence are complete.  
* Close or explicitly defer defects with documented impact and ownership.  
* Tag/archive the accepted source and record environment/configuration requirements without secrets.  
* Transfer operational guidance, Page authorization steps, backup/export notes, and fallback procedures.  
* Revoke temporary access and tester credentials that are no longer required.  
* Record lessons learned, remaining risks, and recommended post-capstone work.

# **6\. TECHNICAL PROCESS PLANS** {#6.-technical-process-plans}

## ***6.1 Process Model*** {#6.1-process-model}

The project uses iterative Agile development with Scrum-informed two-week cycles. Each increment follows:  review validation evidence and approved requirements; clarify acceptance conditions; assess architecture,  security, data, UI, and deployment impact; implement on a branch; run focused and regression tests; peer review  through a pull request; merge into dev; verify in the integrated or deployed environment; collect stakeholder  feedback; update the SRS, RTM, SDD, SPMP, STD, and priorities when affected.  

The process is risk-adjusted. Authentication, authorization, tenant isolation, migrations, publishing state  transitions, and external-token handling require broader test and review evidence than isolated presentation  changes.

## ***6.2 Methods, Tools, and Techniques*** {#6.2-methods,-tools,-and-techniques}

| Category | Current method/tool |
| :---- | :---- |
| Requirements/design | Proposal, SRS, SDD, implementation plans, use cases, diagrams, traceability. |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 7, TanStack Query 5, Axios, FullCalendar, Tailwind CSS, v4, shadcn/ui, Radix UI |
| Backend | Java 21, Spring Boot 4.0.6, Spring MVC/Security/Validation/Data JPA/Cache. |
| Data/storage | PostgreSQL/Supabase, Flyway, pgvector-dependent retrieval, Cloudflare R2. |
| External services | Facebook Graph API v25.0,Anthropic Claude, Gemini, Voyage AI, Resend, Messenger integration. |
| Testing/quality | Spring tests, H2 test support, Playwright, ESLint, TypeScript build, bundle budgets, manual role workflows. |
| Configuration/deployment | Git/GitHub pull request workflow, Vercel for frontend deployment, Railway for backend deployment, environment-managed secrets, and application health monitoring. |

## ***6.3 Infrastructure Plan*** {#6.3-infrastructure-plan}

The frontend is built and deployed independently from the Spring Boot REST backend. PostgreSQL stores  transactional and audit data; R2 stores media objects; external APIs provide Page publishing, email, AI,  embeddings, and messaging. Development, test, and deployment configuration is separated through  environment variables. Secrets are never committed or copied into documentation.  Operational controls include health endpoints, background-job records, audit logs, bounded connection  resources, retention jobs, publishing recovery paths, and performance regression checks. Backup, export,  credential rotation, and production support ownership must be confirmed during transition.

## ***6.4 Product Acceptance Plan*** {#6.4-product-acceptance-plan}

| Acceptance area | Required evidence |
| :---- | :---- |
| Functional | Role-based primary workflows complete; state transitions and fallbacks behave as approved. |
| Validation-driven changes | STD Case-29 and Case-30 pass for each implemented priority |
| Security/privacy | Authentication, authorization, tenant boundaries, token/secret handling, auditability, and logout/session behavior verified. |
| Reliability | Publishing/email/AI failures degrade safely; retries are bounded; manual workflow remains available. |
| Performance | Critical SRS targets and frontend regression checks are measured; no invented results are reported. |
| Usability | Role-based usability tasks completed, task success rates and user feedback analyzed, and System Usability Scale (SUS) results evaluated against the minimum target score of 70 out of 100\. |
| Validation traceability | Findings and approved priorities trace through SMART objectives, SRS, SDD, implementation, tests, and acceptance. |
| Documentation | SRS, RTM, SDD, SPMP, STD, validation artifacts, deployment guidance, and known limitations align with the accepted build. |
| Stakeholder | Required academic and operational reviewers record acceptance or actionable conditions. |

# **7\. SUPPORTING PROCESS PLANS** {#7.-supporting-process-plans}

## ***7.1 Configuration Management Plan*** {#7.1-configuration-management-plan}

Git is the configuration-management system. Work begins from the latest dev baseline on a named branch.  Commits remain scoped, pull requests describe behavior and files, checks are completed, and reviewed work is  merged. Database migrations are immutable once shared. Release baselines identify the source commit,  migration state, deployed environments, and matching document versions.

## ***7.2 Verification and Validation Plan*** {#7.2-verification-and-validation-plan}

Verification checks that requirements are represented in design, code, migrations, tests, and documentation.  Validation checks that intended users can complete representative tasks and that measured outcomes address  the project objectives. Testing covers unit/service/controller behavior, integration boundaries, frontend end-to-end  paths, role/tenant access, failure paths, slow-network behavior where applicable, and regression checks after  refactoring.  The five MVP Validation deliverables are complete. Their findings and evidence are the required input to the  Refactoring Priorities, Updated SRS and RTM, Updated SDD, Updated SPMP, full implementation, deployment,  and testing. The Week 9 Software Test Documents consist of the Test Plan, Test Cases, and Test Reports. The  Week 10 deployed-system evidence includes test execution and usability results.

| Stage | Verification/validation activity | Output |
| :---- | :---- | :---- |
| Requirements | Review clarity, consistency, traceability, and measurable acceptance. | Approved requirement/change record |
| Design | Review architecture, data, interfaces, security, failure handling, and deployability. | SDD/decision update |
| Implementation | Automated tests, lint/build, peer review, migration checks, focused manual scenarios. | PR evidence and test results |
| Integrated system | Role workflows, Page/AI/email/storage boundaries, recovery, performance, and accessibility checks. | System test evidence and defect log |
| MVP/UAT | Assigned role tasks, task success/time, SUS, AI usefulness/quality, and open feedback. | Responses, highlights, recommendations |
| Re-validation (new) | Same instrument after Sprints 7–9, ≥ 10 users across 3 roles, phone and desktop | Per-role completion, SUS (with/without straight-lined), AI acceptance |
| Acceptance | Trace requirements and critical defects to final evidence. | Acceptance record and release baseline |

## ***7.3 Documentation Plan*** {#7.3-documentation-plan}

| Document | Status | Control |
| :---- | :---- | :---- |
| Proposal / objectives | Existing baseline | Update only through approved academic revision. |
| MVP Validation package | COMPLETED | Preserve original form, framework, responses, highlights, and evidence. |
| Refactoring Priorities | COMPLETED | Each priority cites evidence and an objective |
| Updated SRS and RTM | COMPLETED | Requirements source of record; trace findings, objectives, features, design, and tests. |
| Updated SDD | COMPLETED | Align architecture, data, interfaces, workflows, security, and integrations with the accepted SRS. |
| Updated SPMP | COMPLETED | Update scope, schedule, WBS, resources, roles, risks, validation, and transition. |
| Software Test Documents | COMPLETED | Include Test Plan, Test Cases, Test Reports, requirement links, and measured results. |
| Deployment / user guidance | IN PROGRESS / UPCOMING | Match the deployed build, include operational guidance, and exclude secrets. |

## ***7.4 Quality Assurance Plan*** {#7.4-quality-assurance-plan}

Quality assurance combines preventive standards and evidence-based review: scoped changes, established  repository patterns, authorization at the server, safe migrations, clear error/loading behavior, tests proportional to  risk, review before merge, and documentation alignment. The team does not accept a passing build as proof of  functional, security, or usability acceptance.

## ***7.5 Reviews and Audits*** {#7.5-reviews-and-audits}

* Peer code review for each pull request.  
* Requirements/design review when behavior or architecture changes.  
* Security and tenant-scope review for identity, role, institution, token, and media access changes.  
* Migration review for schema/data changes.  
* Performance and cache review for route data, refetch, invalidation, and cross-session behavior.  
* Milestone audit of deliverables, evidence, known limitations, and unresolved risks.

## ***7.6 Problem Resolution Plan*** {#7.6-problem-resolution-plan}

Problems are recorded with reproducible steps, affected role/data, severity, environment, expected and observed  behavior, evidence, and owner. Critical security, data-loss, cross-tenant, or publication-integrity issues receive  immediate containment. Corrections include regression tests where practical and are verified in the integrated  environment before closure.

## ***7.7 Subcontractor Management Plan*** {#7.7-subcontractor-management-plan}

The project has no software-development subcontractor. Cloud and API providers are managed as external  dependencies through documented configuration, least-privilege access, service limits, privacy review, fallback  behavior, and monitoring. Provider terms do not transfer project acceptance responsibility away from the team.

## ***7.8 Process Improvement Plan*** {#7.8-process-improvement-plan}

At sprint and milestone reviews the team identifies recurring defects, flaky tests (INC-13), untested merges  (INC-14), unclear requirements, deployment failures, cache regressions, and document drift, and converts them  into assigned tasks. v2.0 adds the rule that every feature PR updates its STD case and RTM row..

# **8\. ADDITIONAL PLANS** {#8.-additional-plans}

## ***8.1 Security, Privacy, and Data Governance*** {#8.1-security,-privacy,-and-data-governance}

* Apply least privilege and server-side role/tenant enforcement; never trust client-supplied institution  identity.  
* Protect passwords, JWT secrets, Meta tokens, AI keys, storage credentials, and email credentials; never  place secret values in documents or Git.  
* Maintain auditable state changes and privileged cross-institution activity.  
* Use controlled retention and deletion behavior for user, media, audit, token, and job-run data.  
* Use representative or consented test data and minimize personal information in AI prompts and  evidence.  
* Clear or invalidate user-specific cached data at logout/account/role/institution changes.

## ***8.2 Deployment and Transition*** {#8.2-deployment-and-transition}

Development deployment uses Vercel for the frontend and Railway for the backend, with Supabase PostgreSQL  and Cloudflare R2. Production transition requires an accepted release baseline, environment inventory, migration  verification, secret provisioning, health checks, backup/export confirmation, Meta Page authorization, monitoring  ownership, and a rollback or manual fallback procedure.  The course milestone requires the system to be deployed and ready for testing by 17 October 2026, supported  by test execution and usability test results. Functional, integration, system, and acceptance testing, together with  adviser and user feedback, must be retained as release evidence. Deployment completion is not claimed until the  URL, environment, source baseline, migration state, availability, and supporting results are confirmed.  Meta live operation is an organizational dependency. The DASIG Page administrator or authorized business  representative must grant suitable access and complete any required Meta verification/review. Until that occurs,  testing must use approved development/test identities and a test Page, and public production publishing must not  be represented as complete.

Following the academic deployment milestone, the team plans to conduct an extended pilot deployment  beginning in late October 2026 and continuing through November 2026, with operational activities potentially  extending into December depending on testing requirements, stakeholder feedback, and resource availability.  This period will support functional verification, usability evaluation, defect resolution, performance monitoring, and  preparation for final acceptance and transition. The deployment schedule remains subject to academic  requirements and stakeholder approval.

## ***8.3 Subscription and AI Credit Plan*** {#8.3-subscription-and-ai-credit-plan}

DASIGConnect plans to introduce institution-based subscription tiers to support the system’s continuing  operational, hosting, storage, maintenance, and AI-service expenses. The proposed plans are subject to  adjustment after evaluating actual usage during pilot deployment.

| Feature | Free | Plus | Premium |
| :---- | :---- | :---- | :---- |
| Proposed Monthly Fee | 0 | 499 | 999 |
| Users per institution | 3 | 10 | 30 |
| Monthly Submissions | 50 | 300 | 1000 |
| Media Storage | 1 GB | 10 GB | 50 GB |
| AI caption credits per day | 5 | 25 | 75 |
| AI caption credits per month | 100 | 600 | 2000 |
| AI-processed media per month | 100 | 100 | 5000 |
| Analytics retention | 30 days | 12 months | Full retention |
| Data export | Not included | CSV | CSV and PDF |
| Support | Standard | Priority Email | Priority Support |

One AI caption credit represents one successful caption-generation request. Regenerating a caption consumes  another credit, while failed or timed-out AI requests will not be counted. Daily credits will reset at midnight and will  not carry over to the following day.

Essential functions, including submission creation, review and approval, scheduling, notifications, role-based  access, and account security, will remain available under all plans. Paid subscriptions will primarily provide  greater capacity, AI usage, storage, analytics retention, exports, and support.

The proposed subscription model will initially undergo pilot evaluation. Pricing, limits, and inclusions will be  reviewed based on actual institutional activity, AI consumption, infrastructure expenses, and stakeholder  feedback. Implementation of subscription billing and enforcement is considered a future development and is not  part of the currently completed MVP.

# **9\. PLAN ANNEXES** {#9.-plan-annexes}

## ***9.1 Refactoring Impact Summary*** {#9.1-refactoring-impact-summary}

| Area | 29 May Module 3 baseline | 16 October refactored baseline |
| :---- | :---- | :---- |
| Scale | Commit ce776d2. | Substantial multi-module refactor and hardening through commit 3976afb; use Git/PR evidence rather than effort estimates from commit counts. |
| Roles | contributor / validator / administrator | contributor / moderator / admin with Admin Owner flag; moderator network-wide |
| Account governance | Initial authentication/provisioning scope. | Expanded password/session security, invitation provenance, role changes, admin promotion/transfer, and account controls. |
| Review | Validation queue and terminal decisions. | Editorial edits, self-review prevention, edit severity/history, missed-review handling, and lock cleanup. |
| Scheduling | Fixed guard rails | Runtime-configurable thresholds (V116, V121, V122), GR-S1 soft quota, GR-T1 priority flag, GR-T6 consolidation, DB exclusion constraint |
| Media | Central repository and recommendation basis. | R2, nested albums, Trash with restore/purge, content hashes, display titles, durable Gemini/Voyage processing queue with dead-letter retry |
| Publishing/engagement | Calendar, publishing, fallback, and exception handling. | OAuth/token validation, suspension/escalation, failed-publication recovery, engagement sync, and Page analytics. |
| Operations | Basic deployment and workflow analytics. | Audit query surfaces, append-only controls, system health, scheduled-job records, retention, and performance regression checks. |
| Frontend data behavior | Primarily page/component fetching. | TanStack Query caching, invalidation, route data reuse, and targeted performance tests. |
| Quality evidence | Initial foundation and authentication unit tests. | 952 backend tests in 104 classes (all 29 controllers covered); 33 Playwright E2E tests; frontend lint clean. |

## ***9.2 Requirements-to-Management Traceability*** {#9.2-requirements-to-management-traceability}

| Requirement/objective | Managed through | Evidence |
| :---- | :---- | :---- |
| Multi-institution role access and isolation | Scope, staffing, security, V\&V, and risk plans. | Role/tenant tests, audit records, migration/source review. |
| Complete structured submissions | Content WBS, quality controls, metrics. | Validation behavior and completeness metric. |
| Mandatory review before publishing | Review/publishing WBS, acceptance, risk controls. | State transition and publication audit evidence. |
| Scheduling and publish timeliness | Integration plan, metrics, external-dependency risk. | Calendar/guard-rail tests and publication attempts. |
| Media retrieval and reuse | Media WBS, infrastructure, performance and lifecycle controls. | Search measurements and workflow tests. |
| AI assistance | AI governance, metrics, provider risk, optional-service fallback. | Interaction logs and validation ratings. |
| Usability and stakeholder fit | MVP/UAT plan, participant resources, validation deliverables. | Task results, SUS, comments, interviews, and recommendations. |
| Maintainability and live readiness | Configuration, documentation, QA, monitoring, transition. | Build/test checks, health/audit data, deployment and handover records. |

## ***9.3 Original May 2026 Module 3 Use-Case History*** {#9.3-original-may-2026-module-3-use-case-history}

The following identifiers describe the presentation baseline merged to main on 29 May 2026\. They are retained  as historical evidence and are not relabeled using the later SRS numbering.

| Original use case | Verified period | Principal contributors | Baseline output |
| :---- | :---- | :---- | :---- |
| UC-1.1 Authentication and Provisioning | 19-23 May | Lerah, Mark, Chris, Richemmae | Authentication, provisioning, invitations, tests, route guards |
| UC-1.2 Institution Onboarding | 19-29 May | Richemmae, Mark | Institution foundation, onboarding, scheduling, lifecycle, deletion |
| UC-1.3 Content Submission | 20-29 May | Mark, Chris, Lerah, Richemmae | Composer, submission API, tags, guardrails, media, drafts, scheduling |
| UC-2.1 Content Validation | 24-29 May | Lerah, Mark, Richemmae, Jay Lord | Locks, review actions, queue UI, revision handling |
| UC-2.2 Media Repository | 23-29 May | Richemmae, Jay Lord, Chris | Upload, list/detail/delete, signed URLs, selection, repository UI |
| UC-2.3 Notifications | 23-29 May | Richemmae, Chris, Jay Lord | Notification backend, SSE, filters, real-time UI |
| UC-2.4 Analytics Dashboard | 27-29 May | Lerah, Jay Lord | Metrics APIs and role-scoped dashboard |
| UC-3.1 Calendar and Publishing | 26-29 May | Chris, Richemmae | Calendar, Facebook publishing, scheduling, drag/drop |
| UC-3.2 AI Caption | 26-29 May | Chris, Mark | Claude Vision caption generation and composer integration |
| UC-3.3 AI Media Recommendations | 28-29 May | Chris | Recommendation service and media-picker integration |
| UC-3.4 Manual Publishing Fallback | 27-29 May | Richemmae, Chris | Manual workflow, retry/recovery, resolution integration |
| UC-3.5 Administrator Exception Handling | 27-29 May | Richemmae, Chris | Timeout, override, direct-post, token, exception workflows |

## ***9.4 Current SRS Use-Case Responsibility Matrix*** {#9.4-current-srs-use-case-responsibility-matrix}

Primary implementer means the strongest feature-specific Git evidence. Shared and follow-up work remains  visible; the team is collectively accountable for review, testing, documentation, and acceptance.

| Current SRS use case | Primary/shared implementers | Principal evidence |
| :---- | :---- | :---- |
| UC-1.1 Administrator Account Management | Chris; Richemmae follow-up | PRs 74, 104, 374 |
| UC-1.2 Institution Management | Mark; Richemmae foundation/follow-up | PRs 78, 189, 191 |
| UC-1.3 Contributor Account Management | Mark; Lerah and Chris foundations | PRs 76, 81 |
| UC-1.4 Login and Session Management | Jay Lord; Lerah, Chris, Richemmae follow-up | PR 79 and later security fixes |
| UC-1.5 Post Drafting and Templates | Mark, Chris, Lerah; Richemmae follow-up | PRs 21, 23, 85 |
| UC-1.6 AI and Text Tools Integration | Chris; Mark follow-up | PR 103 |
| UC-1.7 Media Attachment Rules | Jay Lord; Richemmae and Chris follow-up | PR 80; PRs 203, 333-373 |
| UC-1.8 Engagement Helpers | Jay Lord | PR 84 |
| UC-1.9 Draft Submission | Lerah; Richemmae and Chris follow-up | PRs 77, 85 |
| UC-1.10 Moderator Account Management | Chris and Richemmae | PRs 130, 135, 374 |
| UC-2.1 Library Uploads and Albums | Lerah; Jay Lord foundation; Richemmae/Chris follow-up | PR 112 and media lifecycle work |
| UC-2.2 Semantic Search and Filtering | Jay Lord, Lerah, Chris, Richemmae | PRs 112, 310, 365-373 |
| UC-2.3 Media History Tracking | Richemmae | PR 221 |
| UC-2.4 Approval Workflow | Lerah, Mark, Richemmae; Chris performance follow-up | PRs 40, 42, 113 |
| UC-2.5 Automated Watermarking | Mark; Richemmae/Chris follow-up | PRs 98, 267 |
| UC-3.1 Master Calendar Visibility | Mark; Richemmae/Chris follow-up | PR 100 commits and later calendar work |
| UC-3.2 Automated Facebook Publishing | Lerah; Richemmae follow-up | PR 99 |
| UC-3.3 Automated Reminders and Alerts | Jay Lord; Chris/Richemmae foundation | PR 111 |
| UC-3.4 Social Engagement Analytics | Richemmae; Chris follow-up | PR 101 and later analytics work |
| UC-3.5 System Health Analytics | Chris; Richemmae follow-up | PRs 114, 118 |
| UC-3.6 Audit Log Review | Jay Lord; Richemmae/Chris follow-up | PR 127 and later controls |

## ***9.5 Cross-Cutting Development Work*** {#9.5-cross-cutting-development-work}

| Work package | Principal contributor evidence | Management significance |
| :---- | :---- | :---- |
| Repository, deployment, CI, and database foundation | Richemmae; Chris support | Established source, environments, migrations, CI, and testing baseline. |
| R2 media-storage migration | Richemmae, PR 129 | Changed upload, media lifecycle, publishing, and tenant-scoped storage. |
| Contributor/Moderator/Admin role refactor | Chris PR 130; Richemmae follow-up | Changed authorization, terminology, account management, and review responsibility. |
| Frontend caching architecture | Chris, PRs 164-187 | Introduced session-aware caching, invalidation, and route data reuse. |
| React/backend performance program | Chris, PRs 213-258 and 312-327 | Added cancellation, pagination, progressive loading, telemetry, and regression checks. |
| Responsive and interaction quality | Mark, May-September PRs | Improved cross-role mobile/desktop usability without changing core authorization. |
| Media AI and semantic retrieval | Chris, PRs 333-373 | Added durable processing, multi-image activation, Voyage retrieval/ranking, evaluation, and monitoring. |
| Requirements reconciliation | Richemmae, September documentation PRs | Aligned living documents with current roles and implemented behavior. |
| Demo readiness and test coverage | Chris, PRs 401-408 | Fixed the backend test suite, cleared frontend lint errors, removed stale demo code, added controller tests for all remaining controllers, and consolidated documentation. |

## ***9.6 Current Management Assessment*** {#9.6-current-management-assessment}

The DASIGConnect project has completed the five required MVP Validation outputs, along with the updated  Software Requirements Specification (SRS), Software Design Description (SDD), and Software Project  Management Plan (SPMP). These documents provide the foundation for aligning the system's implementation  with approved requirements, stakeholder feedback, and project objectives.

The remaining project activities focus on completing validation-driven refactoring priorities, Software Test  Documents, system testing, deployment preparation, and usability evaluation. The team will continue verifying  implemented features against the approved requirements, documenting test results, addressing identified  defects, and ensuring that all necessary evidence is available for final project acceptance and transition. Any  remaining deployment dependencies, academic requirements, and stakeholder approvals will be monitored and  addressed before project completion.

# **10\. INDEX** {#10.-index}

Key topics are discoverable through the Table of Contents and the Definitions, Risk Management, Refactoring  Impact, and Requirements-to-Management Traceability sections.