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
1. Lets contributors from each member institution submit event content (photos, captions, tags)
2. Routes submissions through an administrator-reviewed approval pipeline
3. Schedules approved content for automated publication to the DASIG Facebook Page
4. Provides AI-assisted caption generation and intelligent media recommendation

---

## Features

### Module 1 — Foundation, Access Control & Content Submission
- Multi-institution contributor accounts with **Role-Based Access Control** (Contributor / Validator / Administrator)
- Isolated institution workspaces with row-level data scoping
- Content submission form with server-enforced validation (event title, date, description, caption, tags, media upload)
- Invitation-token onboarding flow for new institutions and users

### Module 2 — Validation Workflow, Notifications & Analytics
- Admin validation interface: Approve / Request Revision (with remarks) / Reject
- Centralized **media repository** — search by filename, tag, uploader; one-click "Use in new post" reuse
- **Email notifications** within 5 minutes of any submission status change
- **Real-time in-app notifications** via Server-Sent Events (SSE) within 30 seconds of state change
- Analytics dashboard: posting frequency, submission-to-publish duration, content completeness rate

### Module 3 — Scheduling, Publishing & AI Features
- Visual **calendar scheduling** with conflict detection (FullCalendar.js)
- **Automated Facebook publishing** via Facebook Graph API v25.0 (≥95% publish success rate within ±5 min)
- Manual publishing fallback for API constraint scenarios
- **AI caption generation** — Claude Vision analyzes uploaded images and suggests captions within 10 seconds
- **AI image classification** — auto-tags uploaded images into 8+ categories (≥80% accuracy target)
- **Intelligent media recommendation** — returns top 5 semantically related images from the repository within 3 seconds using Voyage AI embeddings + pgvector cosine similarity search

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
│  Supabase Storage (media files) │
└─────────────────────────────────┘
```

**External integrations:**
- **Facebook Graph API v25.0** — automated post publishing
- **Anthropic Claude Vision API** — AI caption generation + image classification
- **Voyage AI API** (`voyage-3-lite`) — vector embeddings for semantic media recommendation

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
| Database | Supabase PostgreSQL |
| Vector Search | pgvector extension (`VECTOR(1024)`, cosine similarity) |
| File Storage | Supabase Storage |
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
│   ├── render.yaml             # Render deployment config
│   └── pom.xml
├── frontend/                   # React SPA
│   ├── src/
│   │   ├── components/ui/      # shadcn/ui components
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── vite.config.ts
│   └── package.json
├── docs/                       # Project documentation
│   ├── DASIGConnect - Project Proposal.pdf
│   ├── DASIGConnect_SDD.pdf    # Software Design Document (94 pages)
│   └── DASIGConnect_SRS.pdf    # Software Requirements Specification
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

| Role | Scope | Capabilities |
|---|---|---|
| **Contributor** | Per-institution | Submit content, upload media, view AI caption suggestions, use media recommendations |
| **Validator** | Per-institution | Review submissions before escalating to admin |
| **Administrator** | Network-wide (DASIG) | Approve/reject/request revision, schedule posts, manage institutions, view analytics |

---

## Development Modules

| Module | Status | Key Use Cases |
|---|---|---|
| **Module 1** — Foundation | In progress | UC-1.1 Auth & Provisioning · UC-1.2 Institution Onboarding · UC-1.3 Content Submission |
| **Module 2** — Validation & Analytics | Planned | UC-2.1 Validation · UC-2.2 Media Repo · UC-2.3 Notifications · UC-2.4 Analytics |
| **Module 3** — Scheduling & AI | Planned | UC-3.1 Calendar/Publishing · UC-3.2 AI Caption · UC-3.3 AI Classification · UC-3.4 Manual Fallback · UC-3.5 Exception Handling |

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
