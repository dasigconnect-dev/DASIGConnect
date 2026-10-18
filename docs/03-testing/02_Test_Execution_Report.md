# Folder 03 — Test Execution Report

| Field | Value |
| :--- | :--- |
| Project | DASIGConnect (Team `2526-sem2-it332-38`) |
| Prepared by | Member 2 — Bug Fixing & Test Coverage |
| Format reference | *Software Test Document*, Appendix A.1 (Test Logs) and A.2 (Test Results) |
| Scope | Automated backend suite (`backend/`, JUnit 5 + Mockito + Spring MockMvc) |
| Final run | 2026-10-16 13:56–13:57 PHT, `.\mvnw.cmd clean test` |
| Result | **930 tests — 930 passed, 0 failed, 0 errors, 0 skipped — BUILD SUCCESS** |

---

## 1. Test Environment

| Item | Value |
| :--- | :--- |
| OS | Microsoft Windows 11 Home Single Language |
| JDK | Java 21.0.9 LTS (HotSpot 64-Bit) |
| Build | Maven 3.9.15 via Maven Wrapper (`mvnw.cmd`) |
| Framework | Spring Boot 4.0.6 |
| Test tooling | JUnit 5 (Surefire 3.5.5), Mockito (inline mock maker), AssertJ, Spring `@WebMvcTest` / MockMvc |
| Database (tests) | H2 in-memory for Spring context tests; repositories mocked in unit tests |
| External APIs | Facebook Graph, Voyage, Claude/Gemini, storage, SMTP: all mocked (no network calls) |

---

## 2. Summary of Test Execution Metrics (Before vs. After)

| Metric | BEFORE (2026-10-16 00:20) | AFTER (2026-10-16 13:57) | Change |
| :--- | :---: | :---: | :---: |
| Test classes | 92 | **99** | +7 |
| Tests executed | 895 | **930** | +35 |
| Passed | 894 | **930** | +36 |
| Failed | 1 | **0** | −1 |
| Errors / Skipped | 0 / 0 | **0 / 0** | — |
| Pass rate | 99.89% | **100.00%** | |
| Build | BUILD FAILURE | **BUILD SUCCESS** | |
| Duration | 1 min 01 s | 1 min 05 s | |

Where the +35 tests come from: 33 tests in 7 new classes (Section 4), plus 2 tests added to `UserServiceTest` (67 → 69) during the INC-11 fix.

### Results by package (final run — from Surefire XML reports)

| Package | Test classes | Tests | Failed |
| :--- | :---: | :---: | :---: |
| `backend` (application context) | 1 | 1 | 0 |
| `backend.controller` | 18 | 162 | 0 |
| `backend.event` | 1 | 12 | 0 |
| `backend.exception` | 1 | 4 | 0 |
| `backend.external` | 2 | 8 | 0 |
| `backend.model.dto.*` | 3 | 21 | 0 |
| `backend.repository` | 1 | 1 | 0 |
| `backend.schedule` | 11 | 48 | 0 |
| `backend.service` | 60 | 666 | 0 |
| `backend.util` | 1 | 7 | 0 |
| **Total** | **99** | **930** | **0** |

Per-class results for all 99 classes are in [`reports/per-class-test-results.csv`](reports/per-class-test-results.csv).

---

## 3. Test Logs (Appendix A.1 style)

| # | Log file | Command | Date / Time (PHT) | Result |
| :-: | :--- | :--- | :--- | :--- |
| 1 | [`logs/01_BEFORE_full-suite_895-tests_1-FAILED.log`](logs/01_BEFORE_full-suite_895-tests_1-FAILED.log) | `.\mvnw.cmd test` | 2026-10-16 00:20–00:21 | 895 run, **1 failure** (INC-11), BUILD FAILURE |
| 2 | [`logs/02_FIX_UserServiceTest_69-tests_PASSED.log`](logs/02_FIX_UserServiceTest_69-tests_PASSED.log) | `.\mvnw.cmd test -Dtest=UserServiceTest` | 2026-10-16 00:29 | 69 run, 0 failures |
| 3 | [`logs/03_NEW-TESTS_7-classes_33-tests_PASSED.log`](logs/03_NEW-TESTS_7-classes_33-tests_PASSED.log) | `.\mvnw.cmd test "-Dtest=<7 new classes>"` | 2026-10-16 00:33 | 33 run, 0 failures |
| 4 | [`logs/04_AFTER_full-suite_FINAL.log`](logs/04_AFTER_full-suite_FINAL.log) | `.\mvnw.cmd clean test` | 2026-10-16 13:56–13:57 | **930 run, 0 failures, BUILD SUCCESS** |

**How to find the key lines in each log:** open the file and search for `Tests run:` (per class) or `Results:` (the summary at the end). In log 1, search for `FAILURE` to see the failing test.

---

## 4. New Test Functions Written (previously untested services)

Before this cycle, these 7 service classes in `backend/src/main/java/.../service/` had **no test class**. Each test below is mapped to the STD test case it supports.

Commit: `e2ad438e19a8d29b3508d8bbb2cdb72e1ef97ca1` — *test: add unit tests for 7 previously untested services (33 tests)*

| ID | New test class | Service under test | STD Case / UC | Tests |
| :--- | :--- | :--- | :--- | :-: |
| CG-01 | `TokenEncryptionServiceTest` | `TokenEncryptionService` (AES-256-GCM for the stored Facebook Page token) | Case-20 (UC-3.2), Case-24 (UC-3.5) | 7 |
| CG-02 | `PostTemplateServiceTest` | `PostTemplateService` (saved post templates) | Case-6 (UC-1.5) | 8 |
| CG-03 | `ScheduledJobHealthServiceTest` | `ScheduledJobHealthService` (job run history for System Health) | Case-24 (UC-3.5) | 4 |
| CG-04 | `WorkspaceProvisionerServiceTest` | `WorkspaceProvisionerService` (tenant scope on institution onboarding) | Case-3 (UC-1.2), Case-25 | 1 |
| CG-05 | `MediaRetrievalEmbeddingServiceTest` | `MediaRetrievalEmbeddingService` (image + semantic embedding stages) | Case-14 (UC-2.2), Case-9 (UC-1.7) | 4 |
| CG-06 | `MediaSearchCacheServiceTest` | `MediaSearchCacheService` (30 s search cache) | Case-14 (UC-2.2) | 4 |
| CG-07 | `SocialEngagementSyncServiceTest` | `SocialEngagementSyncService` (Facebook engagement sync) | Case-23 (UC-3.4) | 5 |
| | | | **Total** | **33** |

### Test functions and what each one verifies

**CG-01 `TokenEncryptionServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `isConfigured_withSecret_returnsTrue` | The service reports configured when an app secret is set |
| `isConfigured_withNullOrBlank_returnsFalse` | A null or blank secret means not configured |
| `encryptAndDecryptToken_roundTrip_matchesOriginal` | Encrypt → decrypt returns the original token; the stored value is in `iv:ciphertext` form |
| `encryptToken_producesDifferentIVEachTime` | The same token encrypts differently each time (random IV), and both values decrypt |
| `decryptToken_invalidFormat_throwsException` | A malformed stored value fails safely |
| `decryptToken_tamperedCiphertext_throwsException` | The GCM authentication tag rejects tampered data |
| `encryptToken_withBlankSecret_throwsException` | Encryption fails safely when the secret is blank or invalid |

**CG-02 `PostTemplateServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `list_returnsOrderedTemplatesForUser` | Only the user's own templates are listed, newest first |
| `create_withValidData_normalizesAndSavesTemplate` | Text is trimmed, the user's institution is used as fallback, tags are deduplicated, commas are stripped, and at most 8 tags are kept |
| `create_duplicateName_throwsConflict409` | A duplicate template name for the same user returns 409 |
| `create_missingName_throwsIllegalArgumentException` | A name is required |
| `create_missingCaption_throwsIllegalArgumentException` | A caption is required |
| `create_withNullOptionalFields_appliesFallbacks` | Defaults are applied: target "Saved from submission", category "Custom" |
| `delete_existingTemplate_deletesSuccessfully` | The owner can delete their template |
| `delete_nonExistentTemplate_throwsNotFound404` | A missing template, or another user's template, returns 404 |

**CG-03 `ScheduledJobHealthServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `recordSuccess_savesSuccessRunWithoutError` | A SUCCESS run is stored with start, end, and duration |
| `recordFailure_withException_savesFailedRunAndTruncatesLongError` | A FAILED run is stored, and the error text is capped at 1,000 characters |
| `recordFailure_withNullException_savesUnknownFailure` | A null exception is stored as "Unknown failure" |
| `recordFailure_withExceptionWithoutMessage_summarizesClassSimpleName` | When there is no message, the exception class name is stored |

**CG-04 `WorkspaceProvisionerServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `provision_bindsTenantScopeForAdmin` | Onboarding an institution binds the tenant scope for that institution |

**CG-05 `MediaRetrievalEmbeddingServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `generateOrReuse_imageTypeSuccess_runsBothEmbeddingStages` | Images run the image stage, then the semantic stage |
| `generateOrReuse_imageTypeImageStageFails_shortCircuitsAndReturnsFalse` | If the image stage fails, the semantic stage is skipped |
| `generateOrReuse_nonImage_skipsImageStageAndRunsSemanticStage` | Videos skip the image stage |
| `generateOrReuse_nonImageSemanticFails_returnsFalse` | A semantic-stage failure is reported |

**CG-06 `MediaSearchCacheServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `get_nonExistentKey_returnsNull` | A cache miss returns null |
| `putAndGet_validKey_returnsCachedResponse` | A cached search result is returned |
| `invalidateAll_clearsAllEntries` | Invalidation empties the cache |
| `put_exceedsMaxEntries_clearsAndAcceptsNew` | The 500-entry memory guard clears the cache and still stores the new entry |

**CG-07 `SocialEngagementSyncServiceTest`**
| Test function | Verifies |
| :--- | :--- |
| `syncPending_withMissingEngagement_processesBatchAndReturnsCount` | Only posts that have a Facebook post ID are counted as synced |
| `syncOne_successfulFetch_savesUpdatedMetrics` | Reach, reactions, comments, and shares are saved |
| `syncOne_apiThrowsException_recordsLastErrorAndReturnsFalse` | A Graph API error is recorded in `lastError` without crashing the sync |
| `syncOne_missingSubmission_returnsFalse` | A missing submission is skipped safely |
| `constructor_clampsBatchSizeBetween1And100` | The batch size config is clamped to the range 1–100 |

### Tests added during the INC-11 fix (`UserServiceTest`, commit `96148d1c`)
| Test function | Verifies | STD Case |
| :--- | :--- | :--- |
| `changeRole_proposeModeratorPromotion_setsPendingWithoutChangingRoleYet` | The proposal keeps the role, marks it pending, and does not revoke sessions yet | Case-12 / Case-4 |
| `confirmModeratorPromotion_clearsInstitutionAndInvalidatesTokens` | Confirmation applies the Moderator role, clears the institution, and revokes sessions | Case-12 / Case-4 |
| `declineModeratorPromotion_clearsPromotionFieldsWithoutChangingRole` | Declining clears the proposal and leaves the role unchanged | Case-12 / Case-4 |

---

## 5. Defects

See [`01_Defect_Log_Failed_vs_Fixed.md`](01_Defect_Log_Failed_vs_Fixed.md): **INC-11** (failed → fixed) and **INC-12** (open, documentation).

---

## 6. Out of Scope for This Cycle

- Frontend Playwright E2E suite (`npm run test:e2e`) was **not** re-run in this cycle. It needs a test build and a running app.
- Manual / UI procedures for Case-1 to Case-25 were not re-executed. The STD A.1 manual logs still apply.

---

## 7. How to Reproduce

```powershell
cd backend
.\mvnw.cmd clean test
# Expected ending:  Tests run: 930, Failures: 0, Errors: 0, Skipped: 0  /  BUILD SUCCESS
# Per-class XML reports: backend\target\surefire-reports\
```

---

## 8. Hand-off

The backend suite is green (930/930) as of commit `e2ad438e` on branch `test/std`. It is ready to pass to Member 1 for code freeze.
