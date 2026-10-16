# Folder 03 — Testing Evidence (Member 2: Bug Fixing & Test Coverage)

**Assigned tasks**
1. Fix failing test cases and write new test functions for code that had none.
2. Save test execution logs and fill out the Defect Log (failed vs. fixed).

**Result:** 1 failing test fixed. 7 new test classes (33 tests) and 2 extra tests written. Backend suite went from **895 tests / 1 failing** to **930 tests / 0 failing**.

---

## What is in this folder (read in this order)

| # | File | What it is | Matches STD section |
| :-: | :--- | :--- | :--- |
| 1 | [`01_Defect_Log_Failed_vs_Fixed.md`](01_Defect_Log_Failed_vs_Fixed.md) | Defect Log: what failed, why, how it was fixed, and proof | Appendix A.3 Incident Report |
| 1b | [`01_Defect_Log_Failed_vs_Fixed.csv`](01_Defect_Log_Failed_vs_Fixed.csv) | Same Defect Log in spreadsheet form (open in Excel / Google Sheets) | Appendix A.3 |
| 2 | [`02_Test_Execution_Report.md`](02_Test_Execution_Report.md) | Before/after metrics, environment, log index, list of every new test function | Appendix A.1 + A.2 |
| 3 | [`logs/`](logs/) | Raw Maven console output — the actual evidence | Appendix A.1 Test Logs |
| 4 | [`reports/per-class-test-results.csv`](reports/per-class-test-results.csv) | Pass/fail and test count for all 99 test classes (from the final run) | Appendix A.2 |

### Logs (numbered in the order they happened)

| File | Shows |
| :--- | :--- |
| `logs/01_BEFORE_full-suite_895-tests_1-FAILED.log` | Starting state: 895 tests, 1 failure (INC-11) |
| `logs/02_FIX_UserServiceTest_69-tests_PASSED.log` | The fixed test class passing on its own |
| `logs/03_NEW-TESTS_7-classes_33-tests_PASSED.log` | The 7 new test classes passing |
| `logs/04_AFTER_full-suite_FINAL.log` | Final clean run: 930 tests, 0 failures, BUILD SUCCESS |

---

## Git evidence

| Commit | Description |
| :--- | :--- |
| `3976afb0` | (Teammate, PR #374, 2026-10-04) Change that caused the test to go stale (root cause of INC-11) |
| `96148d1c` | **Member 2** — Fix INC-11 (`UserServiceTest`) |
| `e2ad438e` | **Member 2** — Add 7 new test classes (33 tests) |

Branch: `test/std` · Repo: `github.com/dasigconnect-dev/DASIGConnect`

---

## Changed source files

| File | Change |
| :--- | :--- |
| `backend/src/test/java/com/dasigconnect/backend/service/UserServiceTest.java` | Fixed 1 test, added 2 |
| `backend/src/test/java/com/dasigconnect/backend/service/TokenEncryptionServiceTest.java` | New (7 tests) |
| `backend/src/test/java/com/dasigconnect/backend/service/PostTemplateServiceTest.java` | New (8 tests) |
| `backend/src/test/java/com/dasigconnect/backend/service/ScheduledJobHealthServiceTest.java` | New (4 tests) |
| `backend/src/test/java/com/dasigconnect/backend/service/WorkspaceProvisionerServiceTest.java` | New (1 test) |
| `backend/src/test/java/com/dasigconnect/backend/service/MediaRetrievalEmbeddingServiceTest.java` | New (4 tests) |
| `backend/src/test/java/com/dasigconnect/backend/service/MediaSearchCacheServiceTest.java` | New (4 tests) |
| `backend/src/test/java/com/dasigconnect/backend/service/SocialEngagementSyncServiceTest.java` | New (5 tests) |

No production (`src/main`) code was changed by Member 2.

---

## Items for other members

- **Documentation team:** INC-12. UC-1.10 A11 and SRS UC-1.10 A11 say Contributor → Moderator needs no confirmation, but UC-1.3 A8 and the code require confirmation. STD Case-12 and the STD A.2 metrics row (*"17 classes / 273+ tests"*) also need updating.
- **Member 1:** The green suite is at commit `e2ad438e`. INC-01 may now be closable after a Case-2 API retest; see the Defect Log, section 4.

## Re-running the tests

```powershell
cd backend
.\mvnw.cmd clean test
```
