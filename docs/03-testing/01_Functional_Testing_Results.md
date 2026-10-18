# Functional Testing Results

**Project:** DASIGConnect (Team `2526-sem2-it332-38` — CIT-U Capstone)  
**System:** Spring Boot 4.0.6 (Java 21 LTS) + React 19 Frontend  
**Prepared by:** Member 2 — Bug Fixing & Test Coverage  
**Format Reference:** Software Test Document (STD), Section 3 & Appendix A.2  
**Date of Execution:** October 16–18, 2026  
**Result:** **930 tests executed — 930 passed, 0 failed, 0 errors, 0 skipped (100.00% Pass Rate)**

---

## 1. Executive Summary

This **Functional Testing Results** document reports the comprehensive verification of all functional requirements across the DASIGConnect platform. All 25 core Use Cases (`UC-1.1` through `UC-3.6`) specified in the Software Requirements Specification (SRS v6.0) were mapped directly to automated test cases `Case-1` through `Case-25`.

The automated backend test suite was expanded from **895 tests** to **930 tests** across **99 test classes**, achieving a **100% pass rate**. All external third-party integrations (Facebook Graph API, Voyage AI, Claude/Gemini, Cloudflare R2, SMTP) were deterministically tested via isolated unit and slice tests.

---

## 2. Test Execution Environment

| Component | Specification |
| :--- | :--- |
| **Operating System** | Microsoft Windows 11 Home Single Language (64-bit) |
| **Java Development Kit** | OpenJDK 64-Bit Server VM (build 21.0.9+10-LTS) |
| **Build Tool** | Apache Maven 3.9.15 via Maven Wrapper (`mvnw.cmd`) |
| **Framework & Testing Stack** | Spring Boot 4.0.6, JUnit 5 (Surefire 3.5.5), Mockito, AssertJ, Spring MockMvc |
| **Database Layer (Testing)** | In-Memory H2 for Slice Contexts / Mocked JPA Repositories for Services |
| **Execution Command** | `.\mvnw.cmd clean test` |

---

## 3. Summary of Functional Test Execution Metrics

| Metric | Baseline | Final Execution | Variance / Remarks |
| :--- | :---: | :---: | :--- |
| **Total Test Classes** | 92 | **99** | +7 classes added |
| **Total Tests Executed** | 895 | **930** | +35 tests added |
| **Passed Tests** | 894 | **930** | +36 passed |
| **Failed Tests** | 1 (`INC-11`) | **0** | All defects resolved (0 failures) |
| **Errors / Skipped** | 0 / 0 | **0 / 0** | Zero errors, zero skipped |
| **Overall Pass Rate** | 99.89% | **100.00%** | Complete pass achieved |
| **Build Status** | BUILD FAILURE | **BUILD SUCCESS** | Clean green build confirmed |
| **Suite Execution Time** | 1 min 01 s | **1 min 05 s** | Average 14.3 tests/second |

---

## 4. Test Results by Package Breakdown

| Package Namespace | Classes | Tests Run | Failures | Pass Rate |
| :--- | :---: | :---: | :---: | :---: |
| `com.dasigconnect.backend` (Application Context) | 1 | 1 | 0 | 100.0% |
| `com.dasigconnect.backend.controller` (REST Endpoints) | 18 | 162 | 0 | 100.0% |
| `com.dasigconnect.backend.event` (Application Events) | 1 | 12 | 0 | 100.0% |
| `com.dasigconnect.backend.exception` (Global Handlers) | 1 | 4 | 0 | 100.0% |
| `com.dasigconnect.backend.external` (API Adapters) | 2 | 8 | 0 | 100.0% |
| `com.dasigconnect.backend.model.dto` (Data Validation) | 3 | 21 | 0 | 100.0% |
| `com.dasigconnect.backend.repository` (Persistence) | 1 | 1 | 0 | 100.0% |
| `com.dasigconnect.backend.schedule` (Cron & Background) | 11 | 48 | 0 | 100.0% |
| `com.dasigconnect.backend.service` (Business Logic) | 60 | 666 | 0 | 100.0% |
| `com.dasigconnect.backend.util` (Utilities & Diffs) | 1 | 7 | 0 | 100.0% |
| **TOTAL** | **99** | **930** | **0** | **100.00%** |

---

## 5. Newly Authored Test Classes & Functional Coverage

To close test coverage gaps in previously untested services, 33 new test functions across 7 new test classes were authored in commit `e2ad438e`:

| Test Class | Target Service | STD Case | Tests | Functional Verification Scope |
| :--- | :--- | :--- | :---: | :--- |
| **`TokenEncryptionServiceTest`** | `TokenEncryptionService` | Case-20, Case-24 | 7 | AES-256-GCM Facebook token encryption, IV randomization, tampering rejection |
| **`PostTemplateServiceTest`** | `PostTemplateService` | Case-6 (UC-1.5) | 8 | Template normalization, category fallback, 8-tag limit, duplicate 409 |
| **`ScheduledJobHealthServiceTest`** | `ScheduledJobHealthService` | Case-24 (UC-3.5) | 4 | 16-job health tracking, status history logging, execution status queries |
| **`WorkspaceProvisionerServiceTest`** | `WorkspaceProvisionerService` | Case-3 (UC-1.2) | 1 | Institution onboarding tenant workspace provisioning and isolation |
| **`MediaRetrievalEmbeddingServiceTest`** | `MediaRetrievalEmbeddingService` | Case-14 (UC-2.2) | 4 | Multimodal Voyage image & text semantic embedding stages |
| **`MediaSearchCacheServiceTest`** | `MediaSearchCacheService` | Case-14 (UC-2.2) | 4 | 30-second TTL LRU query caching, cache hit/miss behavior |
| **`SocialEngagementSyncServiceTest`** | `SocialEngagementSyncService` | Case-23 (UC-3.4) | 5 | Facebook Graph engagement sync, exponential backoff, rate limiting |

---

## 6. Execution Traceability & Appendix

- **Word Deliverable:** [`01_Functional_Testing_Results.docx`](01_Functional_Testing_Results.docx)
- **Comprehensive Per-Class Matrix:** [`reports/per-class-test-results.csv`](reports/per-class-test-results.csv) (lists all 99 test classes, duration, and pass status)
- **Raw Execution Log:** [`logs/04_AFTER_full-suite_FINAL.log`](logs/04_AFTER_full-suite_FINAL.log)
