# DASIGConnect

> A centralized platform for coordinating, validating, and scheduling DASIG social media content across multiple member institutions.

DASIGConnect is a capstone project (Team Code: `2627-sem1-it411-38`) built for the **DOST Acadême–Science and Innovation Group (DASIG)** under DOST Region 7. It replaces informal, ad hoc social media coordination between member Higher Education Institutions (CIT-U, Silliman University, and others) and central DASIG administrators with a structured, role-based digital workflow integrated directly with the official DASIG Facebook Page.

---

## 🌐 Live System Deployment

| Service | Live URL | Platform | Notes |
|---|---|---|---|
| **Web Application (Frontend)** | [https://dasigconnect.com](https://dasigconnect.com) | Vercel | Single-Page Application (React 19 + Vite) |
| **Backend REST API** | [https://api.dasigconnect.com](https://api.dasigconnect.com) | Railway | Spring Boot 4 / Java 21 REST API |
| **System Health Endpoint** | [https://api.dasigconnect.com/health](https://api.dasigconnect.com/health) | Railway | Operational health status probe |

---

## Table of Contents

- [Live System Deployment](#-live-system-deployment)
- [Problem & Solution](#problem--solution)
- [Features by Module](#features-by-module)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started (Local Development)](#getting-started-local-development)
- [Environment Variables](#environment-variables)
- [Deployment Architecture](#deployment-architecture)
- [User Roles & Permissions](#user-roles--permissions)
- [Development Modules & Use Cases](#development-modules--use-cases)
- [Team Information](#team-information)

---

## Problem & Solution

### The Problem
The DASIG Facebook page traditionally suffered from:
- **Delayed event coverage:** Submissions were often provided days after an event occurred.
- **Incomplete content:** Missing photo/video assets, unvetted captions, and missing institutional branding.
- **Irregular activity:** Multi-institution posts collided or left long inactive gaps with no coordination.
- **Lack of auditability:** No formal approval trails, revision history, or automated scheduling.

### The Solution
DASIGConnect provides a unified **Social Media Content Workflow and Scheduling Management System** that:
1. **Empowers Contributors** at member institutions to self-draft and schedule event coverage within system-enforced spacing and daily limit guard rails.
2. **Standardizes Verification** through a network-wide Moderator/Administrator review queue, supporting revision requests, side-by-side diff tracking, and Fast-Track approval for live, time-sensitive events.
3. **Automates Publishing** to the DASIG Facebook Page via the Meta Graph API, backed by an atomic claim state machine, automated brand watermarking, and a guided Manual Fallback for edge cases.
4. **Augments Quality with Advisory AI** — human-triggered Claude Vision captioning, Voyage AI multimodal embeddings for media recommendation, and automatic album auto-matching.

---

## Features by Module

### Module 1 — Accounts, Institutions, Sessions & Drafting
- **Role-Based Access Control (RBAC):** Three network-wide and institution-scoped roles (Contributor, Moderator, Administrator).
- **Institution Management:** Row-level data scoping (RLS) with a protected network-default institution.
- **Account Lifecycle:** Invite, activate, deactivate, reactivate, delete, and erase accounts; capped Admin promotion with propose/confirm workflow, plus Admin Owner transfer.
- **Session Security:** Stateless JWT authentication (8h TTL) paired with database-backed token revocation (single-token logout and account-wide session revocation).
- **3-Step Content Composer:** Structured drafting (Media Attachment → Post Details → Schedule) with a 0–100 completeness readiness score, autosave, and post templates (built-in, personal, and AI-synthesized from top posts).
- **Advisory AI & Typography:** On-demand AI caption generation (Claude Vision) and Fancy Unicode text styling for Facebook captions.
- **Schedule Guard Rails:** Spacing buffers, daily caps, minimum lead times, and configurable posting-hour boundaries.

### Module 2 — Content Workflow, Media Library & Governance
- **Centralized Media Repository:** Shared and institution-scoped media library, nested albums, batch uploads, and manual tag floor enforcement.
- **AI Classification & Multimodal Embeddings:** Background image classification + Voyage AI embeddings (`voyage-4-lite`, 1024-dim, pgvector cosine similarity) powering semantic search and album auto-matching.
- **Media Asset Lifecycle:** Tiered soft-delete with a 30-day purge window, duplicate detection, and "Used In" activity tracking.
- **Review Queue & Approval Workflow:** Moderator review with Approve, Request Revision (with structured remarks), and Reject actions, backed by review locking and live autosave snapshot freezing.
- **Automated Brand Watermarking:** Configurable watermark overlay positioned and stamped onto approved media assets at publish time.
- **Event-Driven Notifications:** Real-time in-app alerts (SSE), transactional email notifications (Resend), and Facebook Messenger delivery.
- **Analytics & Audit Logging:** Role-scoped engagement analytics, content quality telemetry, and an immutable, append-only Audit Log with CSV export.

### Module 3 — Calendar, Publishing & Exception Handling
- **Master Publishing Calendar:** Full visibility of upcoming and past posts across member HEIs with drag-to-reschedule safeguards.
- **Automated Facebook Publishing:** Meta Graph API integration with atomic lock-and-publish semantics to prevent duplicate posting.
- **Fast-Track ("Live Event") Mode:** Asynchronous instant publication upon review approval for live event coverage.
- **Manual Publishing Fallback:** Step-by-step guided recovery workflow with an abandonment timer for posts requiring manual publication.
- **System Health & Governance:** Operational telemetry (success rate, edit-and-approve rate, missed-review rate), external service probes (Facebook, Claude, Voyage, Resend), and OAuth token re-authentication.

---

## System Architecture

DASIGConnect follows a decoupled, three-tier cloud architecture:

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Tier                    │
│            React 19 SPA — Hosted on Vercel             │
│   (React Router v7, Tailwind CSS v4, shadcn/ui, SSE)   │
└───────────────────────────┬────────────────────────────┘
                            │ HTTPS / SSE
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Application Tier                     │
│          Spring Boot 4 REST API — on Railway           │
│   (Spring Security, JWT RBAC, State Machine, Flyway)   │
└─────────────┬────────────────────────────┬─────────────┘
              │ JDBC                       │ S3 API / HTTPS
              ▼                            ▼
┌───────────────────────────┐┌───────────────────────────┐
│         Data Tier         ││       Storage Tier        │
│    Supabase PostgreSQL    ││       Cloudflare R2       │
│  (pgvector 1024-dim, RLS) ││   (Zero-egress storage)   │
└───────────────────────────┘└───────────────────────────┘
```

### External Integrations
- **Meta Graph API (v22.0+):** Post publishing, page engagement insights, and OAuth token management.
- **Anthropic Claude Vision (`claude-haiku-4-5`):** On-demand caption generation and image metadata tagging.
- **Voyage AI (`voyage-4-lite`, 1024-dim):** Vector embeddings stored in PostgreSQL via `pgvector` for semantic media search.
- **Resend API:** Transactional email delivery for invitations, verification, and reset links.
- **Cloudflare R2:** S3-compatible object storage; clients upload directly using presigned PUT URLs, and reads are served via the backend media proxy or custom domain.

---

## Tech Stack

### Frontend (`/frontend`)
| Layer | Technology |
|---|---|
| Framework | **React 19** + **TypeScript** |
| Build Tool | **Vite** + `@tailwindcss/vite` |
| Routing | **React Router v7** |
| Styling & UI | **Tailwind CSS v4** + **shadcn/ui** (Radix UI primitives) |
| State & Query | **TanStack Query** (React Query) + Context API |
| HTTP Client | **Axios** (with token refresh & session expiration interceptors) |
| Calendar | **FullCalendar** (daygrid, timegrid, interaction) |
| Real-time Updates | **EventSource** (Server-Sent Events) |
| Hosting | **Vercel** (`dasigconnect.com`) |

### Backend (`/backend`)
| Layer | Technology |
|---|---|
| Language & Runtime | **Java 21 (LTS)** |
| Framework | **Spring Boot 4.0.6** |
| Security | **Spring Security** + **jjwt** (stateless tokens + DB revocation) |
| Persistence & ORM | **Spring Data JPA** + **Hibernate** |
| Database Migrations | **Flyway** (versioned incremental migrations) |
| Connection Pooling | **HikariCP** (tuned for session pooler limits) |
| Email Service | **Resend HTTP API** |
| Task Scheduling | **Spring Scheduler** (cron & background workers) |
| Hosting & Container | **Railway** (Docker build, `/health` healthcheck, `api.dasigconnect.com`) |

### Database & Cloud Storage
| Service | Implementation |
|---|---|
| Primary Database | **Supabase PostgreSQL** |
| Vector Extension | **pgvector** (`VECTOR(1024)`, cosine similarity index) |
| Media Object Storage | **Cloudflare R2** (S3-compatible, presigned PUT upload) |
| Primary Keys | **UUID v4** throughout all database entities |

---

## Project Structure

```
DASIGConnect/
├── backend/                        # Spring Boot REST API
│   ├── src/main/java/com/dasigconnect/backend/
│   │   ├── config/                 # SecurityConfig, JacksonConfig, CorsConfig
│   │   ├── controller/             # REST API controllers (/api/v1/*)
│   │   ├── event/                  # Event listeners & notification dispatchers
│   │   ├── external/               # Facebook, Claude Vision, Voyage, Resend clients
│   │   ├── model/                  # JPA entities, DTOs, and domain enums
│   │   ├── repository/             # Spring Data JPA repositories
│   │   ├── schedule/               # Background jobs (publishing, expiry, cleanup)
│   │   ├── security/               # JwtFilter, UserPrincipal, TokenHashUtils
│   │   └── service/                # Core business logic services
│   ├── src/main/resources/
│   │   ├── application.properties  # App configuration & fallback defaults
│   │   └── db/migration/           # Flyway SQL migrations (V1 to latest)
│   ├── Dockerfile                  # Container build specification
│   ├── railway.toml                # Railway deployment configuration
│   └── pom.xml                     # Maven dependencies & build configuration
├── frontend/                       # React Single-Page Application
│   ├── src/
│   │   ├── api/                    # Axios API service clients
│   │   ├── components/             # Reusable UI primitives & layout components
│   │   ├── features/               # Feature screens (auth, composer, review, calendar, etc.)
│   │   ├── hooks/                  # Custom React hooks (auth, query, media, tour)
│   │   ├── styles/                 # Tailwind CSS & custom animations
│   │   ├── App.tsx                 # Application routes & layout wrapper
│   │   └── main.tsx                # React DOM root entry point
│   ├── vite.config.ts              # Vite bundler configuration
│   └── package.json                # Frontend dependencies & scripts
├── docs/                           # Canonical documentation (see docs/README.md)
│   ├── pdf/                        # Formal Proposal, SDD, SRS, and local DB guide
│   └── img/                        # Retained visual reference material
├── docker-compose.yml              # Local PostgreSQL + pgvector container configuration
└── README.md                       # Repository guide & setup instructions
```

---

## Getting Started (Local Development)

### Prerequisites
- **Java 21 JDK** or later
- **Node.js 18+** & **npm 9+**
- **Docker Desktop** (optional, recommended for local PostgreSQL + pgvector)
- **Maven** (optional; `./mvnw` wrapper is included in `/backend`)

### 1. Database Setup (Local Docker)
From the repository root, start the local PostgreSQL database with pgvector:
```bash
docker compose up -d
```
*This starts PostgreSQL on port `5432` with user `postgres`, password `postgres`, and database `dasigconnect`.*

### 2. Backend Setup
```bash
cd backend

# Create local environment file from template
cp .env.example .env

# Run the Spring Boot application (auto-loads .env)
./mvnw spring-boot:run

# Or build the executable JAR
./mvnw clean package -DskipTests
```
*The backend API will be available at `http://localhost:8080/api/v1` and healthcheck at `http://localhost:8080/health`.*

### 3. Frontend Setup
```bash
cd frontend

# Install dependencies
npm install

# Start local Vite development server
npm run dev

# Run production build & typecheck
npm run build
```
*The web client will be running at `http://localhost:5173`.*

---

## Environment Variables

### Backend Configuration (`backend/.env`)
Create `backend/.env` with the following variables (see [`backend/.env.example`](backend/.env.example) for detailed comments):

```env
# Database (Local Docker or Supabase Session Pooler)
DASIG_DATABASE_URL=jdbc:postgresql://localhost:5432/dasigconnect
DASIG_DATABASE_USER=postgres
DASIG_DATABASE_PASSWORD=postgres

# Security & Session
JWT_SECRET=your-32-character-or-longer-secret-key-here
JWT_TTL_MINUTES=480

# Application URLs
APP_FRONTEND_BASE_URL=http://localhost:5173
BACKEND_PUBLIC_BASE_URL=http://localhost:8080

# Email Delivery (Resend API)
RESEND_API_KEY=re_your_api_key
MAIL_FROM=no-reply@dasigconnect.com

# Facebook Graph API & OAuth
FACEBOOK_PAGE_ACCESS_TOKEN=your-long-lived-page-access-token
FACEBOOK_PAGE_ID=your-numeric-page-id
FACEBOOK_APP_ID=your-meta-app-id
FACEBOOK_APP_SECRET=your-meta-app-secret
FACEBOOK_OAUTH_REDIRECT_URI=http://localhost:8080/api/v1/facebook/oauth-callback

# AI Services
ANTHROPIC_API_KEY=sk-ant-your-anthropic-key
VOYAGE_API_KEY=pa-your-voyage-key

# Cloudflare R2 Object Storage
R2_ACCOUNT_ID=your-cloudflare-account-id
R2_ENDPOINT=https://your-account-id.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=your-r2-access-key-id
R2_SECRET_ACCESS_KEY=your-r2-secret-access-key
R2_BUCKET=dasigconnect-media
R2_PUBLIC_BASE_URL=https://pub-your-hash.r2.dev
R2_CUSTOM_DOMAIN_URL=https://media.dasigconnect.com
```

### Frontend Configuration (`frontend/.env`)
Create `frontend/.env` (or set in Vercel project settings for production):

```env
# Backend REST API Base URL
VITE_API_URL=http://localhost:8080/api/v1
```
*(In production, this is set to `https://api.dasigconnect.com/api/v1`)*

---

## Deployment Architecture

| Layer | Provider | Domain | Configuration |
|---|---|---|---|
| **Frontend** | Vercel | `dasigconnect.com` | Vite build preset, SPA rewrite rules |
| **Backend** | Railway | `api.dasigconnect.com` | Dockerfile builder, `/health` healthcheck, auto-restart |
| **Database** | Supabase | Managed Cloud | PostgreSQL 15+ with `pgvector` extension |
| **Media Storage** | Cloudflare R2 | `media.dasigconnect.com` | S3-compatible, zero-egress, presigned uploads |

> **Meta Graph API & Platform Access Note:**
> During project development and academic evaluation, the Meta Developer Application operates in **Development Mode** in accordance with Meta's developer policies. Under Development Mode, posts published through the API are visible to authorized application administrators, developers, and testers on the connected Facebook Page feed. Transitioning to full public visibility across all Facebook users requires **Meta Business Verification** by the operating consortium (DOST DASIG Region 7), an external organizational process that requires no code changes to the platform.

---

## User Roles & Permissions

| Role | Operational Scope | Key Capabilities |
|---|---|---|
| **Contributor** | Institutional | Create and edit post drafts, upload/manage media, access AI captioning and formatting tools, pick schedule slots within guard rails. |
| **Moderator** | Network-wide | Validate submissions in the Review Queue (Approve, Request Revision, Reject), invite Contributors, monitor the Master Calendar with capped rescheduling allowance. |
| **Administrator** | Network-wide | Full governance: manage HEI institutions, user role promotions/demotions, System Health, immutable Audit Log, Page Settings, Watermark Configuration, and Facebook token re-authentication. |

---

## Development Modules & Use Cases

The system implementation maps to 22 formal, code-verified use cases:

| Module | Scope | Core Use Cases |
|---|---|---|
| **Module 1** | Accounts, Institutions, Sessions & Drafting | `UC-1.1` Admin Account Management · `UC-1.2` Institution Management · `UC-1.3` Contributor Account Management · `UC-1.4` Login & Session Revocation · `UC-1.5` Post Drafting & Templates · `UC-1.6` AI Caption Tools & Text Formatting · `UC-1.7` Media Attachment Rules · `UC-1.8` Engagement Helpers & Peak Hours · `UC-1.9` Draft Submission & Withdraw · `UC-1.10` Moderator Account Management |
| **Module 2** | Content Workflow, Media Library & Governance | `UC-2.1` Media Library Uploads & Albums · `UC-2.2` Semantic Search & Filtering · `UC-2.3` Media History & Asset Lifecycle · `UC-2.4` Review Queue & Approval Workflow · `UC-2.5` Automated Watermarking |
| **Module 3** | Calendar, Publishing & Exception Handling | `UC-3.1` Master Calendar Visibility · `UC-3.2` Automated Facebook Publishing · `UC-3.3` Automated Reminders & Alerts · `UC-3.4` Engagement Analytics · `UC-3.5` System Health Analytics · `UC-3.6` Audit Log Review |

---

## Team Information

| Name | Role | Contact |
|---|---|---|
| **Richemmae V. Bigno** | Project Lead / Full Stack Developer | `richem.miccah.bigno@gmail.com` |
| **Jay Lord C. Bayonas** | Backend Developer | Team Member |
| **Chris Daniel P. Cabatana** | Full Stack Developer | Team Member |
| **Mark Anton L. Camoro** | Frontend Developer | Team Member |
| **Lerah A. Caones** | QA & Documentation | Team Member |

- **Institution:** Cebu Institute of Technology – University (CIT-U)
- **Department:** College of Computer Studies
- **Course:** IT411 — Capstone Project / Software Engineering
- **Team Code:** `2627-sem1-it411-38`
