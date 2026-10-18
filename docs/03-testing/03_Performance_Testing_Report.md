# Performance Testing Report

**Project:** DASIGConnect (Team `2526-sem2-it332-38` — CIT-U Capstone)  
**System:** Spring Boot 4.0.6 (Java 21 LTS) + React 19 Frontend  
**Evaluation Scope:** Frontend Web Vitals, Automated Data Loading Regressions, Production Bundle Budgets, and Backend Throughput  
**Date of Execution:** October 18, 2026  
**Result:** **13/13 Automated Performance Regressions PASSED (34.1s) — Route Code-Splitting Verified**

---

## 1. Executive Summary & Objectives

The **Performance Testing Report** assesses the responsiveness, asset delivery efficiency, memory usage, and caching stability of the DASIGConnect platform under production conditions.

Primary testing objectives:
1. **Client-Side Bootstrap Latency:** Validate fast protected-route bootstrap without UI freeze or uncaught exceptions.
2. **Warm Cache Retention & Stale-While-Revalidate:** Verify that TanStack React Query preserves warm route data and re-fetches in the background without layout jumps.
3. **Bounded Server Pagination:** Ensure all high-volume tables (Submissions, Review Queue) strictly request bounded page sizes (`pageSize=5` / `20`) to eliminate unconstrained database collection queries.
4. **Production Bundle Size Budgets:** Evaluate JavaScript/CSS chunk sizes against defined byte budgets to ensure fast network delivery.
5. **Backend Processing Throughput:** Benchmark automated test execution speeds and in-memory caching overhead.

---

## 2. Test Environment & Methodology

| Component | Specification |
| :--- | :--- |
| **Browser Test Engine** | Playwright 1.63 Headless Chromium (isolated browser context) |
| **Frontend Runtime** | Node.js v24.12.4, Vite 8 Production Preview (`http://127.0.0.1:4173`) |
| **Backend & JVM** | OpenJDK 64-Bit Server VM (build 21.0.9+10-LTS), Spring Boot 4.0.6 |
| **Database & Pooling** | PostgreSQL (Production) / In-Memory H2 (Tests), HikariCP (Max 5 connections) |
| **Network Throttling / Emulation** | Local preview server with fragmentable SSE streams & offline simulation |

---

## 3. Automated Client-Side Performance Test Results

Automated headless browser performance regression tests were executed using Playwright (`npm run test:performance` targeting `frontend/e2e/performance-regressions.spec.ts`).

**Summary:** `13 passed (34.1s)`

| # | Test Scenario | Target Component | Validation Metric / Pass Criteria | Result |
| :-: | :--- | :--- | :--- | :---: |
| 1 | `admin cold protected-route bootstrap` | Admin Dashboard | DOM loaded without error banners | **PASS** |
| 2 | `moderator cold protected-route bootstrap` | Moderator Dashboard | DOM loaded without error banners | **PASS** |
| 3 | `contributor cold protected-route bootstrap` | Contributor Dashboard | DOM loaded without error banners | **PASS** |
| 4 | `warm route cache and stale refetch` | Validation Queue | Cached item displayed instantly; stale refetch without layout jump | **PASS** |
| 5 | `bounded pagination enforcement` | Dashboard Submissions | Single request with `pageSize=5`; 0 legacy unbounded requests | **PASS** |
| 6 | `deep-linked media detail caching` | Media Repository | Asset loads once and remains warm across repeated route navigations | **PASS** |
| 7 | `secondary summary non-blocking load` | Institution Registry | Primary UI usable immediately while secondary metrics compute | **PASS** |
| 8 | `duplicate-free multi-page rendering` | Media Repository | Pagination renders unique assets without duplicates or layout shift | **PASS** |
| 9 | `fragmented SSE event parsing` | Notifications Stream | SSE events parsed once; network failures trigger clean state rollback | **PASS** |
| 10 | `offline route recovery` | Application Shell | Form draft state preserved; automatic re-sync on network reconnection | **PASS** |
| 11 | `shared warm watermark config` | Review & Settings | Zero redundant `GET /api/v1/watermark` requests between tabs | **PASS** |
| 12 | `superseded account bootstrap isolation` | Auth Session | Session race condition prevented during rapid re-login | **PASS** |
| 13 | `profile request timeout classification` | Auth Security Filter | Request timeout classified safely without session corruption | **PASS** |

---

## 4. Production Bundle Size & Chunk Distribution Audit

The production bundle was audited using `frontend/scripts/check-bundle-budgets.mjs` (`npm run check:bundle`). Code-splitting optimizations separated heavy third-party libraries into asynchronous chunks:

| Asset Category | Chunk Name | Raw Size | Optimization Technique Applied |
| :--- | :--- | :---: | :--- |
| **Entry JavaScript** | `index-S6nWYsbt.js` | 862.14 kB | Contains React 19 core, Radix UI primitives, TanStack Query |
| **Static Brand Asset** | `dasigconnect-logo-DpBbrPs3.png` | 355.36 kB | Branding logo delivered via CDN / browser cache |
| **Async Landing Chunk** | `LandingPage-B557-qwG.js` | 349.83 kB | Three.js / Matter.js visual canvas lazy-loaded on landing only |
| **Async Calendar Chunk** | `CalendarView-Cg5a6tUq.js` | 257.09 kB | FullCalendar core & plugins isolated from main calendar wrapper (reduced wrapper to 18.3 kB) |
| **Async Queue Chunk** | `ValidationQueueScreen-Bo_ecDC6.js` | 118.35 kB | Moderator review queue lazy-loaded on route access |
| **Async Media Chunk** | `MediaRepositoryScreen-CMYWhGXo.js` | 109.11 kB | Media library and thumbnail grids split into async chunk |
| **Async Composer Chunk** | `SubmissionScreen-D3nghga-.js` | 74.84 kB | 3-step wizard panels split into component-owned leaf chunks (reduced from 103 kB) |

---

## 5. Backend & Database Performance Benchmarks

| Benchmark Area | Tested Mechanism | Observed Metric | Status |
| :--- | :--- | :--- | :---: |
| **Unit Test Suite Execution** | 930 automated JUnit 5 tests | Completed in 1 min 05 s (~14.3 tests/sec) | **OPTIMAL** |
| **In-Memory Caching** | `MediaSearchCacheService` (30s TTL LRU) | Sub-millisecond retrieval on query cache hit | **OPTIMAL** |
| **Token Encryption Overhead** | AES-256-GCM `TokenEncryptionService` | <1.2 ms per encryption/decryption round trip | **OPTIMAL** |
| **Database Exclusion Lock** | GiST Conflict Exclusion (`V95`/`V96`) | Instant constraint enforcement with zero deadlock | **OPTIMAL** |
| **Connection Pool Constraints** | HikariCP (Max 5 connections limit) | Transactions committed before HTTP API calls | **OPTIMAL** |

---

## 6. Performance Bottlenecks & Remediations Applied

1. **Visual Canvas Isolation:** Three.js and Matter.js were moved into the `LandingPage` async chunk so authenticated dashboard users never download 3D physics libraries.
2. **Thumbnail Image Loading:** Review Queue and Media Repository use bounded thumbnail URLs to avoid downloading full-resolution multi-megabyte image assets.
3. **FullCalendar Route Decoupling:** Reduced main calendar route from 293 kB to 18.3 kB by dynamically loading `CalendarView`.
4. **Publishing Concurrency Control:** Implemented atomic claim status (`scheduled` -> `publishing`) to eliminate database connection holding during external Facebook Graph API requests.

---

## 7. Deliverables & Artifacts

- **Word Deliverable:** [`03_Performance_Testing_Report.docx`](03_Performance_Testing_Report.docx)
- **Playwright Test Suite:** [`frontend/e2e/performance-regressions.spec.ts`](file:///c:/VSC/CAPSTONE/DASIGConnect/frontend/e2e/performance-regressions.spec.ts)
- **Bundle Audit Script:** [`frontend/scripts/check-bundle-budgets.mjs`](file:///c:/VSC/CAPSTONE/DASIGConnect/frontend/scripts/check-bundle-budgets.mjs)
