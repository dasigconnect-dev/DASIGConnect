# Folder 03 — Defect Log (Failed vs. Fixed)

| Field | Value |
| :--- | :--- |
| Project | DASIGConnect (Team `2526-sem2-it332-38`) |
| Prepared by | Member 2 — Bug Fixing & Test Coverage |
| Format reference | *Software Test Document*, Appendix A.3 — Incident Report |
| Test cycle | Regression cycle, 2026-10-16 |
| Branch | `test/std` |
| Numbering | Continues from the STD (INC-01 to INC-10 already exist) |

---

## 1. Summary

| | Count |
| :--- | :---: |
| Defects found failing this cycle | 1 |
| Defects fixed and re-verified | 1 |
| New open incidents raised | 1 (specification inconsistency, no code failure) |
| Failing tests at start → end | 1 → 0 |

---

## 2. Failed vs. Fixed (Resolved Incidents Verified During Regression Testing)

| Incident ID | Related Test / UC | Severity | Status: FAILED (before) | Status: FIXED (after) |
| :--- | :--- | :---: | :--- | :--- |
| **INC-11** | Case-12 (UC-1.10), Case-4 (UC-1.3 A8) | Medium | **FAILED** — 2026-10-16 00:21 PHT. `UserServiceTest.changeRole_promoteContributorToModerator_clearsInstitutionAndInvalidatesTokens` failed at line 522: `expected: "moderator" but was: "contributor"`. Suite result: 895 run, 1 failure, BUILD FAILURE. | **FIXED** — 2026-10-16 00:29 PHT. Commit `96148d1c`. UserServiceTest 69/69 pass. Final full suite 930/930 pass, BUILD SUCCESS. |

### INC-11 — Detail

| Item | Detail |
| :--- | :--- |
| Title | Moderator-promotion unit test expected an immediate role change |
| Failing test | `UserServiceTest.changeRole_promoteContributorToModerator_clearsInstitutionAndInvalidatesTokens` |
| Error | `org.opentest4j.AssertionFailedError: expected: "moderator" but was: "contributor"` (UserServiceTest.java:522) |
| Introduced by | Commit `3976afb0` — *"Fix: admin/moderator transfer confirmation (#374)"*, 2026-10-04. This PR changed `UserService.changeRole()` so that Contributor → Moderator is a **propose/confirm** flow (new `confirmModeratorPromotion` / `declineModeratorPromotion`, migration `moderator_promotion_pending`). It also updated UC-1.3 A8 to match. It did **not** update `UserServiceTest`. |
| Root cause | **Stale test, not faulty application code.** `changeRole(..., MODERATOR, ...)` now only records a pending proposal (`moderatorPromotionRequestedBy`, `moderatorPromotionExpiresAt`). The role stays `contributor` until the target confirms. The old test still asserted the old instant-change behavior. |
| Spec basis for the fix | UC-1.3 A8: *"The proposal does not immediately change the Contributor's role, institution assignment, or active sessions. The Contributor must confirm the proposal before the change takes effect."* |
| Fix applied | 1. Replaced the stale test with `changeRole_proposeModeratorPromotion_setsPendingWithoutChangingRoleYet`. It checks that the role stays `contributor`, the promotion is pending, sessions are **not** invalidated yet, and `ModeratorPromotionRequestedEvent` is published.<br>2. Added `confirmModeratorPromotion_clearsInstitutionAndInvalidatesTokens`. It checks that the role becomes `moderator`, the institution is cleared, the pending fields are cleared, sessions are invalidated, and `UserRoleChangedEvent` is published.<br>3. Added `declineModeratorPromotion_clearsPromotionFieldsWithoutChangingRole`. |
| Production code changed? | No. Only `backend/src/test/.../UserServiceTest.java` changed (+41 / −1 lines). |
| Evidence | Before: [`logs/01_BEFORE_full-suite_895-tests_1-FAILED.log`](logs/01_BEFORE_full-suite_895-tests_1-FAILED.log) (search `FAILURE`)<br>Fix check: [`logs/02_FIX_UserServiceTest_69-tests_PASSED.log`](logs/02_FIX_UserServiceTest_69-tests_PASSED.log)<br>After: [`logs/04_AFTER_full-suite_FINAL.log`](logs/04_AFTER_full-suite_FINAL.log) (search `Tests run: 930`) |
| Commit | `96148d1cdc90b1079f5865449536bb09ac713b1b` — *test(user): align moderator promotion tests with propose/confirm flow (INC-11)* |
| Verified by / date | Member 2 — 2026-10-16 13:57 PHT (clean `mvnw clean test`) |

---

## 3. Open Incidents & Known Discrepancies (new this cycle)

| Incident ID | Related Test / UC | Severity | Description & Root Cause | Current Status & Resolution Plan |
| :--- | :--- | :---: | :--- | :--- |
| **INC-12** | Case-12 (UC-1.10 A11), Case-4 (UC-1.3 A8) | Low (documentation) | **Specification conflict about Contributor → Moderator promotion.** UC-1.3 A8 (updated in PR #374) and the code require the target to confirm. However, UC-1.10 A11 (`docs/md/UC-1.10-moderator-account-management.md`, line 33) and SRS §UC-1.10 A11 still say the move is *"immediate, no confirmation step."* The STD Case-12 remark (*"Lateral move releases active ReviewLock rows and revokes sessions"*) follows the old wording. | **Open — handed to documentation team.** Update UC-1.10 A11 and SRS UC-1.10 A11: Contributor → Moderator is propose/confirm, with a 72-hour expiry. Moderator → Contributor stays immediate. Also update the STD Case-12 procedure. If the team instead decides promotion should be immediate, the **code** must be reverted, and INC-11 must be reopened. |

---

## 4. Notes on Existing STD Incidents

| Incident | Observation this cycle | Recommendation |
| :--- | :--- | :--- |
| INC-01 (Admin Owner transfer to an existing Admin) | Commit `3976afb0` (PR #374, *"Fix: admin/moderator transfer confirmation"*) is dated after the STD logs. Unit test `UserServiceTest.confirmAdminTransfer_toExistingAdmin_keepsOutgoingAsAdmin` passes in the final run. | Candidate for closure. Re-run the Case-2 API/manual procedure before changing the status to Resolved, because a unit test alone does not cover the endpoint role guard. |
| STD Appendix A.2 metrics row | The STD lists *"17 Test Classes (273+ JUnit 5 / MockMvc tests)"*. The actual count today is **99 classes / 930 tests**. | Update A.2 using [`02_Test_Execution_Report.md`](02_Test_Execution_Report.md). |
