# DASIGConnect

> A centralized platform for coordinating, validating, and scheduling DASIG social media content across multiple member institutions.

DASIGConnect is a capstone project (Team Code: `2526-sem2-it332-38`) built for the **DOST Acadême–Science and Innovation Group (DASIG)** under DOST Region 7. It replaces the informal, ad hoc coordination between member HEIs (CIT-U, Silliman University, and others) and a central DASIG administrator with a structured, role-based digital workflow integrated with the DASIG Facebook Page.

---

## Table of Contents

- [Problem](#problem)
- [Solution](#solution)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Deployment](#deployment)
- [User Roles](#user-roles)
- [Development Modules](#development-modules)
- [Team](#team)

---

## Problem

The DASIG Facebook page suffers from:
- Delayed event coverage (events posted days after occurrence)
- Incomplete posts due to missing media assets
- Irregular publishing activity from uncoordinated multi-institution contributions
- No structured submission → validation → scheduling workflow

## Solution

A web-based **Social Media Content Workflow and Scheduling Management System** that:
1. Lets Contributors at each member institution self-schedule event content (photos/video, captions, tags) within system-enforced scheduling rules
2. Routes standard submissions through a network-wide Moderator/Administrator review queue, with a separate Fast-Track path for time-sensitive live events
3. Schedules approved content for automated publication to the DASIG Facebook Page via the Graph API, with a guided manual fallback and an audited Administrator exception-handling path for anything the pipeline can't post automatically
4. Provides on-demand AI-assisted caption generation, AI-assisted media/album matching, and semantic media search — advisory tools a human always chooses to use, never autonomous publishing

---

## Features

### Module 1 — Accounts, Institutions, Sessions & Drafting
- Three-role **Role-Based Access Control**: Contributor (per-institution), Moderator (network-wide review + Contributor management), Administrator (network-wide, full governance) — see [User Roles](#user-roles)
- Institution management with row-level data scoping (RLS) and a protected network-default institution
- Invite/deactivate/reactivate/delete/erase account lifecycle for all three roles, including a capped, propose/confirm Admin promotion flow and an Admin Owner transfer
- Stateless JWT sessions (8h TTL) with DB-backed revocation (single-token logout and account-wide invalidation)
- 3-step content composer (Media → Details → Schedule) with a 0–100 readiness checklist, autosave, withdraw, and built-in + personal post templates
- On-demand **AI caption generation** (Claude Vision) and a Fancy Unicode text-styling tool for captions
- Mandatory media attachment (device upload or media library pick) before a draft can be submitted, plus AI-suggested media and AI-assisted album matching
- Peak-hour posting recommendations from real historical Facebook engagement data
- Network-wide runtime guard rails (spacing, daily cap, lead time, posting-hours window) that a Standard submission must satisfy to reserve a schedule slot

### Module 2 — Content Workflow, Media Library & Governance
- Centralized **media repository** with nested per-institution albums, a shared network library, tagging, semantic + keyword search, and AI-assisted Auto-Match on upload
- Background **AI image classification + Voyage AI embeddings** (`voyage-4-lite`, 1024-dim, pgvector) powering semantic media recommendation and album matching — advisory only, no contributor-facing suggestion popups
- Media history / asset lifecycle: tiered soft-delete with a 30-day purge window, duplicate detection, and a per-asset "Used In" activity history
- Moderator/Administrator **approval workflow**: Approve / Request Revision (with remarks) / Reject, with edit-during-review diff tracking and reviewer locking
- **Automated brand watermarking** applied to approved media at publish time, network-wide configurable
- Event-driven notification system (29 trigger events) with in-app (SSE), email, and Facebook Messenger delivery
- Role-scoped **Analytics dashboard**: posting metrics, Facebook engagement by institution, content completeness, and (Administrator-only) operational health / AI performance reporting, with CSV export
- Admin-only, immutable **Audit Log**: filterable, paginated, structured before/after diffs, CSV export, DB-enforced append-only guarantee (no update/delete path exists at any layer)

### Module 3 — Calendar, Publishing & Exception Handling
- Role-differentiated **master calendar** with drag-to-reschedule (capped and guard-rail-checked for Moderators; unrestricted for Administrators)
- **Automated Facebook publishing** via Graph API — the verified two-step method for image-only posts (`/{PAGE_ID}/photos` → `/{PAGE_ID}/feed`) and the single-call method for video-only posts (`/{PAGE_ID}/videos`), claimed atomically before any API call to prevent double-publishing
- Fast-Track ("Live Event") posts publish asynchronously on approval, bypassing the scheduled queue entirely
- **Manual Publishing Fallback** — a guided 3-step recovery workflow (with an abandonment window) for submissions the automated pipeline couldn't post, including mixed image+video posts (out of automated scope during the pilot)
- **Administrator exception handling**: validation timeouts, override requests, emergency admin direct posts (justification required, audit-logged), and Facebook token re-authentication/health monitoring
- **System Health dashboard** (Administrator-only): database/object-storage capacity, external API status (Facebook, Claude, Voyage, email), background job health, and operational metrics (publish success rate, Edit & Approve rate, missed-review rate, and more)

---

## Architecture

DASIGConnect follows a **three-tier client-server architecture**:

```
┌─────────────────────────────────┐
│  Presentation Tier              │
│  React SPA — served via Vercel  │
│  (React Router, Tailwind, ShadCN│
│   Axios, FullCalendar, SSE)     │
└────────────────┬────────────────┘
                 │ HTTPS / SSE
┌────────────────▼────────────────┐
│  Application Tier               │
│  Spring Boot REST API — Render  │
│  (JWT auth, RBAC, tenant scope, │
│   state machine, bg scheduler)  │
└────────────────┬────────────────┘
                 │
┌────────────────▼────────────────┐
│  Data Tier                      │
│  Supabase PostgreSQL + pgvector │
│  (database only — no storage)   │
│  Cloudflare R2 (media files,    │
│  S3-compatible, presigned PUT)  │
└─────────────────────────────────┘
```

**External integrations:**
- **Facebook Graph API** — automated post publishing, page engagement analytics, OAuth token management
- **Anthropic Claude Vision API** (`claude-haiku-4-5`) — on-demand AI caption generation + background image classification
- **Voyage AI API** (`voyage-4-lite`, 1024-dim) — vector embeddings for semantic media recommendation and album matching

Media uploads never pass through the backend: the browser requests a presigned URL, `PUT`s bytes directly to Cloudflare R2, then sends metadata to the backend. Reads go through the backend's own proxy (`MediaProxyController`), not R2 directly, so storage-host changes never break stored URLs.

**Security:** Stateless JWT authentication · Spring Security RBAC · PostgreSQL Row-Level Security (RLS) for tenant isolation

---

## Tech Stack

### Backend (`/backend`) — Spring Boot 4.0 / Java 21
| Component | Technology |
|---|---|
| Framework | Spring Boot 4.0.6 |
| ORM | Spring Data JPA + Hibernate |
| Security | Spring Security + JWT (jjwt) |
| DB Connection | HikariCP (max 5 connections — Supabase Session Pooler) |
| DB Migrations | Flyway |
| Email | Spring Mail |
| Background Jobs | Spring Scheduler |
| Config | `spring-dotenv` (auto-loads `.env`) |
| Deployment | Render |

### Frontend (`/frontend`) — React 19 / TypeScript / Vite
| Component | Technology |
|---|---|
| Framework | React 19 + TypeScript |
| Build | Vite + `@tailwindcss/vite` |
| Routing | React Router v7 |
| UI Components | shadcn/ui (Radix UI) + Tailwind CSS v4 |
| HTTP Client | Axios |
| Calendar | FullCalendar (daygrid, timegrid, interaction) |
| Real-time | EventSource (SSE) |
| State | React Context API + useReducer |

### Database & Storage
| Component | Technology |
|---|---|
| Database | Supabase PostgreSQL (database only, no storage) |
| Vector Search | pgvector extension (`VECTOR(1024)`, cosine similarity) |
| File Storage | Cloudflare R2 (S3-compatible, presigned upload, backend-proxied reads) |
| Primary Keys | UUID throughout |

---

## Project Structure

```
DASIGConnect/
├── backend/                    # Spring Boot REST API
│   ├── src/main/java/com/dasigconnect/backend/
│   │   ├── config/             # SecurityConfig, JacksonConfig
│   │   ├── model/entity/       # JPA entities (User, Institution, Submission, etc.)
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── security/           # JwtAuthenticationFilter
│   │   └── service/            # Business logic (JWTService, EmailService, etc.)
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/       # Flyway SQL migrations
│   ├── railway.toml             # Railway deployment config
│   └── pom.xml
├── frontend/                   # React SPA
│   ├── src/
│   │   ├── components/ui/      # shadcn/ui components
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts
│   └── package.json
├── docs/                       # Project documentation
│   ├── pdf/                    # Proposal, SDD, SRS
│   └── md/                     # Living, code-verified UC-*.md docs + other notes
└── CLAUDE.md                   # AI agent workspace guide
```

---

## Getting Started

### Prerequisites
- Java 21+
- Node.js 18+
- Maven (or use `./mvnw`)
- A Supabase project (PostgreSQL + Storage)

### Backend

```bash
cd backend

# Copy and fill in your environment variables
cp .env.example .env

# Run locally (spring-dotenv auto-loads .env)
./mvnw spring-boot:run

# Build JAR
./mvnw clean package -DskipTests

# Run tests
./mvnw test
```

### Frontend

```bash
cd frontend

npm install       # install dependencies
npm run dev       # start dev server at localhost:5173
npm run build     # TypeScript check + production build
npm run lint      # ESLint
npm run preview   # preview production build
```

---

## Environment Variables

Create `backend/.env` with the following (loaded automatically by `spring-dotenv`):

```env
DATABASE_URL=jdbc:postgresql://<host>:<port>/<db>?sslmode=require
DATABASE_USERNAME=<supabase-username>
DATABASE_PASSWORD=<supabase-password>

JWT_SECRET=<your-jwt-secret>
JWT_EXPIRATION_MS=86400000

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<your-email>
MAIL_PASSWORD=<app-password>

ANTHROPIC_API_KEY=<your-anthropic-key>
VOYAGE_API_KEY=<your-voyage-key>
FACEBOOK_PAGE_ACCESS_TOKEN=<your-facebook-token>
FACEBOOK_PAGE_ID=<your-page-id>
```

> **Note:** `application.properties` references `${DATABASE_USER}` but the `.env` key is `DATABASE_USERNAME` — keep these in sync.

---

## Deployment

| Layer | Platform | Config |
|---|---|---|
| Backend | Render | `backend/render.yaml` |
| Frontend | Vercel | Auto-detect Vite |
| Database | Supabase | PostgreSQL + pgvector + Storage |

**Render build command:** `mvn clean package -DskipTests`  
**Render start command:** `java -jar target/backend-0.0.1-SNAPSHOT.jar`

> **Facebook API note:** During development, the Meta app runs in **Development mode** — published posts are only visible to users with developer/admin roles on the registered app. Transitioning to full public visibility requires Meta Business Verification by the DASIG organization.

---

## User Roles

`Validator` was renamed to `Moderator` and made network-wide (no institution binding) — do not confuse this with the old per-institution Validator role from earlier project drafts.

| Role | Scope | Capabilities |
|---|---|---|
| **Contributor** | Per-institution | Draft and submit content, upload/select media, use AI caption + media/album assist, self-schedule within guard rails |
| **Moderator** | Network-wide | Review the approval queue (Approve/Request Revision/Reject), invite and manage Contributor accounts they personally invited, full calendar access with a capped reschedule allowance |
| **Administrator** | Network-wide | Everything a Moderator can do, plus institution/account management, unrestricted scheduling override, exception handling (direct posts, manual fallback, token re-auth), System Health, Audit Log, and full Analytics |

Analytics is available to all three roles (role-scoped, not Administrator-exclusive); Admin Management, System Health, Audit Log, Page Settings, and Watermark Configuration are Administrator-only.

---

## Development Modules

Status below reflects the current state of the `dev` branch and living, code-verified use-case docs under [`docs/md/`](docs/md/) — see [`CLAUDE.md`](CLAUDE.md)'s "Use Case Status" section for the authoritative, per-use-case breakdown (including two overlapping UC-numbering series inherited from different project drafts).

| Module | Status | Key Use Cases |
|---|---|---|
| **Module 1** — Accounts, Institutions, Sessions & Drafting | Implemented | UC-1.1–1.4 Account/Session Management · UC-1.5 Post Drafting · UC-1.6 AI & Text Tools · UC-1.7 Media Attachment · UC-1.8 Engagement Helpers · UC-1.9 Draft Submission · UC-1.10 Moderator Management |
| **Module 2** — Content Workflow, Media Library & Governance | Implemented | UC-2.1 Library/Albums · UC-2.2 Semantic Search · UC-2.3 Notifications / Media History · UC-2.4 Analytics / Approval Workflow · UC-2.5 Watermarking |
| **Module 3** — Calendar, Publishing & Exception Handling | Implemented | UC-3.1 Master Calendar · UC-3.2 Automated Publishing / AI Captions · UC-3.3 Reminders & Alerts / Media Suggestions · UC-3.4 Analytics / Manual Fallback · UC-3.5 System Health / Exception Handling · UC-3.6 Audit Log Review |

**Methodology:** Agile / Scrum — 2-week sprints with DASIG stakeholder sprint reviews.

---

## Team

| Name | Role |
|---|---|
| Richemmae V. Bigno | Team Member |
| Jay Lord C. Bayonas | Team Member |
| Chris Daniel P. Cabatana | Team Member |
| Mark Anton L. Camoro | Team Member |
| Lerah A. Caones | Team Member |

**Institution:** Cebu Institute of Technology – University (CIT-U), College of Computer Studies  
**Course:** IT332 — Software Engineering / Capstone  
**Team Code:** `2526-sem2-it332-38`
