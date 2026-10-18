# DASIGConnect — Refactoring Evidence Document
**Deliverable:** Folder 01 — Code, Fixes & Refactoring Evidence  
**Project:** DASIGConnect (Spring Boot 4.0.6 + React 19)  
**Team:** `2526-sem2-it332-38` — CIT-U Capstone  
**Authors:** Member 1 & Member 2 (Code, Fixes & Refactoring)  
**Date:** October 16, 2026  
**Status:** Code Freeze / Green Suite Verified (930/930 Tests Passing)

---

## 1. Overview & MVP Validation Findings

During MVP validation testing and automated test execution across the DASIGConnect platform, two major categories of code debt and behavioral discrepancies were identified:

1. **State Machine Decoupling vs. Stale Test Assertions:**
   - **Finding:** MVP user testing revealed that unilateral, instantaneous role changes (such as immediately converting a Contributor into a network-wide Moderator) introduced operational risk and violated the principle of least privilege. In response, PR #374 implemented a **two-step propose/confirm state machine**.
   - **Regression:** Existing unit tests in `UserServiceTest` were not updated during this refactoring, causing a critical assertion failure (`expected: "moderator" but was: "contributor"`) that broke the continuous integration build.
2. **Critical Core Service Test Coverage Gaps ("Sa Wa Pa Nahimoan"):**
   - **Finding:** Key architectural components—specifically AES-256-GCM Facebook token encryption, post template sanitization, scheduled job execution monitoring, and background engagement metrics synchronization—were functional but lacked dedicated unit and regression tests, posing a significant regression risk for future deployments.

---

## 2. GitHub PR Links & Commit Hashes

| Commit / PR Hash | Author / Role | Date | Description & Purpose |
| :--- | :--- | :--- | :--- |
| **PR #374** (`3976afb0`) | Teammate | 2026-10-04 | *Fix: admin/moderator transfer confirmation* — Introduced 2-step propose/confirm state machine for moderator promotion. |
| **`96148d1c`** | Member 2 | 2026-10-16 | *test(user): align moderator promotion tests with propose/confirm flow (INC-11)* — Refactored stale test assertions and added confirm/decline coverage. |
| **`e2ad438e`** | Member 2 | 2026-10-16 | *test: add unit tests for 7 previously untested services (33 tests)* — Authored comprehensive JUnit 5 + Mockito test suites for untested backend services. |
| **`07910352`** | Member 2 | 2026-10-16 | *docs(testing): add Folder 03 defect log, test execution report, and logs* — Formal QA deliverables and execution logs for Folder 03. |

*Repository URL:* `https://github.com/dasigconnect-dev/DASIGConnect`  
*Target Branch:* `test/std`

---

## 3. Code Clean-up & Anti-Pattern Remediation

In accordance with the Member 1 & 2 guidelines, the following code clean-up activities were completed:
- **Removal of Stale Test Expectations:** Stripped outdated assertions that bypassed the promotion confirmation lifecycle.
- **De-duplication & Input Normalization:** Ensured template tag parsing defensively strips commas, removes duplicate tags, and caps the tag ceiling to 8 items to protect downstream database indexes.
- **Dead Session Mitigation:** Implemented explicit session token revocation tests (`jwtService.invalidateUserTokens()`) ensuring that promoting or demoting an account invalidates existing JWT claims across all active browser sessions.

---

## 4. Before & After Code Snippets

### Snippet 1: Moderator Promotion Lifecycle Decoupling (`UserServiceTest.java`)
*Problem:* The legacy test assumed calling `changeRole()` immediately mutated the account role to `"moderator"`, which failed under the two-step security model.

#### Before (Failing Stale Test)
```java
@Test
void changeRole_promoteContributorToModerator_clearsInstitutionAndInvalidatesTokens() {
    User target = user(UUID.randomUUID(), "c@cit.edu.ph", UserRole.contributor, institution);
    User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
    when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));
    when(userRepository.save(target)).thenReturn(target);

    UserDto result = userService.changeRole(target.getId(), UserRole.moderator, null,
            principal(peerAdmin.getId(), "admin", null));

    // FAILS: Role remains "contributor" pending confirmation
    assertThat(result.getRole()).isEqualTo("moderator");
    assertThat(target.getInstitution()).isNull();
    verify(jwtService).invalidateUserTokens(target.getId());
    verify(eventPublisher).publishEvent(any(UserRoleChangedEvent.class));
}
```

#### After (Decoupled Proposal, Confirmation & Decline Suites)
```java
@Test
void changeRole_proposeModeratorPromotion_setsPendingWithoutChangingRoleYet() {
    User target = user(UUID.randomUUID(), "c@cit.edu.ph", UserRole.contributor, institution);
    User peerAdmin = user(UUID.randomUUID(), "peer@dasigconnect.com", UserRole.admin, null);
    when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(userRepository.findById(peerAdmin.getId())).thenReturn(Optional.of(peerAdmin));
    when(userRepository.save(target)).thenReturn(target);

    UserDto result = userService.changeRole(target.getId(), UserRole.moderator, null,
            principal(peerAdmin.getId(), "admin", null));

    // VERIFIES: Role remains contributor; promotion is marked pending
    assertThat(result.getRole()).isEqualTo("contributor");
    assertThat(result.isModeratorPromotionPending()).isTrue();
    assertThat(target.getModeratorPromotionRequestedBy()).isEqualTo(peerAdmin.getId());
    assertThat(target.getModeratorPromotionExpiresAt()).isAfter(Instant.now());
    verify(jwtService, never()).invalidateUserTokens(any());
    verify(eventPublisher).publishEvent(any(ModeratorPromotionRequestedEvent.class));
}

@Test
void confirmModeratorPromotion_clearsInstitutionAndInvalidatesTokens() {
    User target = user(UUID.randomUUID(), "c@cit.edu.ph", UserRole.contributor, institution);
    UUID requesterId = UUID.randomUUID();
    target.setModeratorPromotionRequestedBy(requesterId);
    target.setModeratorPromotionExpiresAt(Instant.now().plus(Duration.ofHours(24)));
    when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
    when(userRepository.save(target)).thenReturn(target);

    UserDto result = userService.confirmModeratorPromotion(principal(target.getId(), "contributor", institutionId));

    // VERIFIES: On confirmation, role changes, institution clears, and sessions revoked
    assertThat(result.getRole()).isEqualTo("moderator");
    assertThat(target.getInstitution()).isNull();
    verify(jwtService).invalidateUserTokens(target.getId());
    verify(eventPublisher).publishEvent(any(UserRoleChangedEvent.class));
}
```

---

### Snippet 2: AES-256-GCM Token Encryption Security (`TokenEncryptionServiceTest.java`)
*Problem:* Facebook Page Access Tokens stored at rest in the database were encrypted using AES-256-GCM, but had zero automated test coverage verifying IV randomness, tamper detection, or decryption integrity.

#### After (Added Unit Test Suite — Commit `e2ad438e`)
```java
@Test
void encryptAndDecryptToken_roundTrip_matchesOriginal() {
    String token = "EAABwzLIX564BAO7testToken1234567890abcdefghijklmnopqrstuvwxyz";
    String encrypted = service.encryptToken(token);

    assertThat(encrypted).isNotNull();
    assertThat(encrypted).contains(":"); // Storage format iv_b64:ciphertext_b64
    assertThat(encrypted).isNotEqualTo(token);

    String decrypted = service.decryptToken(encrypted);
    assertThat(decrypted).isEqualTo(token);
}

@Test
void decryptToken_tamperedCiphertext_throwsException() {
    String token = "valid-token";
    String encrypted = service.encryptToken(token);
    String[] parts = encrypted.split(":");
    // Tamper with ciphertext bits: GCM authentication tag must reject
    String tampered = parts[0] + ":" + parts[1].substring(0, parts[1].length() - 4) + "AAAA";

    assertThatThrownBy(() -> service.decryptToken(tampered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Token decryption failed");
}
```

---

### Snippet 3: Input Sanitization & Normalization (`PostTemplateServiceTest.java`)
*Problem:* Post templates permitted raw tag lists and potential duplicate naming collisions without regression test verification.

#### After (Added Unit Test Suite — Commit `e2ad438e`)
```java
@Test
void create_withValidData_normalizesAndSavesTemplate() {
    PostTemplateRequestDto req = new PostTemplateRequestDto();
    req.setName("  Welcome Post  ");
    req.setCaption("  Join us for our annual kickoff event!  ");
    req.setTags(List.of("tag1", "  tag2  ", "tag1", "tag3,extra", "", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9"));

    when(repository.existsByOwnerUserIdAndNameIgnoreCase(userId, "Welcome Post")).thenReturn(false);
    when(repository.save(any(PostTemplate.class))).thenAnswer(inv -> inv.getArgument(0));

    service.create(req, user);

    ArgumentCaptor<PostTemplate> captor = ArgumentCaptor.forClass(PostTemplate.class);
    verify(repository).save(captor.capture());
    PostTemplate captured = captor.getValue();

    // Verifies: Leading whitespace trimmed, duplicate tags removed, commas stripped, capped at 8 tags
    assertThat(captured.getName()).isEqualTo("Welcome Post");
    assertThat(captured.getCaption()).isEqualTo("Join us for our annual kickoff event!");
    assertThat(captured.getTags().split(",")).hasSize(8);
}
```

---

## 5. Verification Results & Green Suite Impact

| Metric | Before Refactoring Pass | After Refactoring Pass | Improvement |
| :--- | :---: | :---: | :---: |
| **Total Test Classes** | 92 classes | **99 classes** | **+7 classes** |
| **Total Test Methods** | 895 tests | **930 tests** | **+35 tests** |
| **Passing Tests** | 894 tests | **930 tests** | **+36 passing** |
| **Failing Tests** | 1 test (INC-11) | **0 tests** | **0 failures** |
| **Build Status** | BUILD FAILURE | **BUILD SUCCESS** | **100% Green Suite** |

---

## 6. Conclusion & Member 1 Handoff

The refactoring and bug-fixing cycle for DASIGConnect is complete. The application and test suites are aligned with the SRS state machine, previously untested services now have robust unit test coverage, and the suite executes 100% clean across 930 tests. The branch is frozen and ready for final code integration by Member 1.
