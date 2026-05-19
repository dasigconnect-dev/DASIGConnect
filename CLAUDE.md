# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**DASIGConnect** is a capstone project (Team `2526-sem2-it332-38`, CIT-U) — a web-based Social Media Content Workflow and Scheduling Management System built for the **DOST Acadême–Science and Innovation Group (DASIG)** under DOST Region 7.

It replaces ad hoc coordination between DASIG member HEIs (CIT-U, Silliman University, others) with a structured, role-based workflow: contributors submit event content → administrators validate and approve → system schedules and auto-publishes to the DASIG Facebook Page.

See `README.md` for full project context. See `docs/` for the SDD (94-page design doc) and SRS.

---

## Commands

### Backend (`/backend`)
```bash
# Run locally (loads .env automatically via spring-dotenv)
./mvnw spring-boot:run

# Build JAR (skipping tests)
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=BackendApplicationTests

# Add a dependency (edit pom.xml, then)
./mvnw dependency:resolve
```

### Frontend (`/frontend`)
```bash
npm install        # install dependencies
npm run dev        # start Vite dev server (localhost:5173)
npm run build      # TypeScript check + production build
npm run lint       # ESLint
npm run preview    # preview production build
npx shadcn add <component>   # add a new shadcn/ui component
```

---

## Architecture

DASIGConnect is a **three-tier client-server** system:

```
React SPA (Vercel) ──HTTPS/SSE──► Spring Boot REST API (Render) ──► Supabase PostgreSQL + pgvector
                                                                  └──► Supabase Storage (media files)
                                        │
                            ┌───────────┼───────────┐
                     Facebook Graph API  Anthropic Claude  Voyage AI
                       (auto-publish)    (vision/caption)  (embeddings)
```

### Backend — Spring Boot 4.0.6 / Java 21

- **Entry point:** `src/main/java/com/dasigconnect/backend/BackendApplication.java`
- **Config:** `src/main/resources/application.properties` — references `${DATABASE_URL}`, `${DATABASE_USER}`, `${DATABASE_PASSWORD}`
- **`spring-dotenv`** (`me.paulschwarz:spring-dotenv`) auto-loads `backend/.env` at startup — no manual export needed
- Runs on port `8080`; stateless — all session state in JWT
- **DB migrations:** Flyway (`src/main/resources/db/migration/`)
- **DB access:** Spring Data JPA + Hibernate via HikariCP (max 5 connections — Supabase Session Pooler)
- **Auth:** Spring Security + JWT (`jjwt`) — `JwtAuthenticationFilter` validates every request
- **Multi-tenancy:** `TenantScopeService` enforces institution-level data isolation via `HandlerInterceptor`; backed by PostgreSQL Row-Level Security (RLS)
- **Real-time:** Server-Sent Events (SSE) for in-app notifications
- **Background jobs:** Spring Scheduler for automated Facebook publishing

> **Critical:** `application.properties` references `${DATABASE_USER}` but `.env` uses `DATABASE_USERNAME`. Keep these in sync or the datasource fails to connect.

### Frontend — React 19 / TypeScript / Vite

- **Entry:** `src/main.tsx` → `src/App.tsx`
- **`@` alias** resolves to `src/` (configured in `vite.config.ts`)
- **UI:** shadcn/ui (Radix UI) + Tailwind CSS v4 (via `@tailwindcss/vite` — no `tailwind.config.js` needed)
- **Routing:** React Router v7 with role-based route protection
- **HTTP:** Axios with global JWT interceptors and 401 session expiry handling
- **Calendar:** FullCalendar (daygrid, timegrid, interaction plugins)
- **Real-time:** `EventSource` (SSE) for notification badge
- **State:** React Context API + `useReducer` (JWT, role, institution_id, session expiry)
- **Components:** `src/components/ui/` (shadcn pattern)

### Deployment

| Layer | Platform | Config |
|---|---|---|
| Backend | Render | `backend/render.yaml` — build: `mvn clean package -DskipTests`, start: `java -jar target/backend-0.0.1-SNAPSHOT.jar` |
| Frontend | Vercel | Auto-detect Vite |
| Database | Supabase | PostgreSQL + pgvector extension + Storage |

Render env vars (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`) must be set manually in the Render dashboard (`sync: false`).

---

## Domain Model

### User Roles

| Role | Scope | Key Capabilities |
|---|---|---|
| `CONTRIBUTOR` | Per-institution workspace | Submit content, upload media, use AI caption suggestions, use media recommendations |
| `VALIDATOR` | Per-institution | Review submissions before admin escalation |
| `ADMINISTRATOR` | Network-wide (DASIG) | Approve/reject/revise, schedule posts, manage institutions, view analytics |

### Core Entities (`model/entity/`)

| Entity | Purpose |
|---|---|
| `User` | System user with role + institution binding |
| `Institution` | A DASIG member HEI (e.g., CIT-U) with isolated workspace |
| `InstitutionStatus` | `ACTIVE` / `INACTIVE` |
| `Submission` | A content post submission (event title, date, caption, tags, media) |
| `SubmissionStatus` | `PENDING` → `APPROVED` / `NEEDS_REVISION` / `REJECTED` → `SCHEDULED` → `PUBLISHED` |
| `SlotReservation` | Calendar scheduling slot for a submission |
| `SlotReservationStatus` | `RESERVED` / `PUBLISHED` / `FAILED` |
| `InvitationToken` | One-time token for onboarding new institutions or users |
| `PasswordResetToken` | One-time token for password reset flow |
| `AccountLockout` | Tracks failed login attempts; enforces lockout policy |
| `AuditLog` | Immutable record of all state-changing actions |
| `UserRole` | `CONTRIBUTOR` / `VALIDATOR` / `ADMINISTRATOR` |
| `UserStatus` | `ACTIVE` / `INACTIVE` / `LOCKED` |

---

## External Integrations

| Service | Purpose | Notes |
|---|---|---|
| **Facebook Graph API v25.0** | Automated post publishing to DASIG Facebook Page | Runs in Dev mode during project — posts visible to admins/testers only. Full public visibility requires Meta Business Verification by DASIG. No code changes needed for the Dev→Live transition. |
| **Anthropic Claude Vision API** | AI caption generation (within 10s of image upload) + AI image classification into 8+ categories | Target: ≥60% contributor accept/accept-with-edits rate; ≥80% classification accuracy |
| **Voyage AI API (`voyage-3-lite`)** | Vector embeddings (1024-dim) for semantic media recommendation | Stored in PostgreSQL via pgvector; cosine similarity search returns top 5 related images within 3s |

---

## Development Modules

| Module | Status | Use Cases |
|---|---|---|
| **Module 1** — Foundation, Auth, Submission | In progress | UC-1.1 Auth & User Provisioning · UC-1.2 Institution Onboarding · UC-1.3 Content Submission |
| **Module 2** — Validation, Notifications, Analytics | Planned | UC-2.1 Content Validation · UC-2.2 Media Repository · UC-2.3 Notifications · UC-2.4 Analytics Dashboard |
| **Module 3** — Scheduling, Publishing, AI | Planned | UC-3.1 Calendar & Auto-publish · UC-3.2 AI Caption · UC-3.3 AI Classification & Recommendation · UC-3.4 Manual Fallback · UC-3.5 Admin Exception Handling |

**Methodology:** Agile / Scrum — 2-week sprints. DB migrations use Flyway (`V1__`, `V2__`, ... naming).

---

## Key Design Decisions

- **Stateless JWT auth** — no server-side session; role + institution_id embedded in token claims
- **Tenant isolation** — enforced at two levels: `TenantScopeService` (application) + PostgreSQL RLS (database); neither alone is sufficient
- **SSE over WebSocket** — real-time notifications use `EventSource` (simpler, HTTP/2 compatible, no upgrade needed)
- **pgvector for media recommendation** — `VECTOR(1024)` columns store Voyage AI embeddings; cosine similarity `<=>` operator for nearest-neighbor search
- **HikariCP max 5 connections** — Supabase Session Pooler limit; do not increase without changing Supabase plan
- **Flyway migrations** — `ddl-auto` should be `validate` in prod; migrations live in `src/main/resources/db/migration/`
- **Facebook Dev mode** — API-published posts are invisible to the public until DASIG completes Meta Business Verification; this is expected behavior, not a bug
