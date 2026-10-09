---
name: Backend Bug Checker
description: Systematically inspects the Java Spring Boot backend for common bugs, security flaws, and data integrity issues.
---

# Backend Bug Checker Skill

This skill provides a structured methodology for identifying bugs, vulnerabilities, and logical flaws in a Java Spring Boot backend. 

## Scope
When asked to perform a backend bug check, rigorously examine the codebase (specifically focusing on Controllers, Services, Entities, and Database Migrations).

## Bug Checking Checklist

### 1. Security & Authorization
- **Endpoint Protection:** Verify that `@PreAuthorize` or equivalent security annotations exist on all protected endpoints.
- **Data Scope / Tenant Isolation:** Ensure services filter database queries by the current user's `institutionId` or `userId` where applicable, preventing cross-tenant data leaks.
- **Role Validations:** Verify that administrative actions explicitly check for `ADMIN` or `MODERATOR` roles.

### 2. Transaction Boundaries & Data Integrity
- **@Transactional Annotations:** Ensure database writes that modify multiple tables are wrapped in `@Transactional` to prevent partial commits.
- **Race Conditions:** Look for check-then-act vulnerabilities in reservations, creation, or state-change logic. Ensure pessimistic locks or optimistic locking (`@Version`) is used where appropriate.
- **Circular Dependencies:** Check if database models or logic paths allow for infinite loops (e.g., nested albums referencing themselves).

### 3. Payload Validation & Exception Handling
- **DTO Validation:** Ensure `@Valid` is used on `@RequestBody` items. Verify that constraints (`@NotNull`, `@Size`, `@Min`, `@Max`) are present.
- **Graceful Failure:** Ensure exceptions are handled cleanly and don't leak stack traces to the frontend.
- **Null Reference Checks:** Identify potential `NullPointerExceptions` in deeply nested DTO property access.

### 4. Logic & State Dissonance (Frontend-Backend Mismatch)
- Look for logic where the backend expects a certain boolean, enum, or string value but processes it using naive comparisons (like ignoring casing or `.findFirst()` vs `.findAll()`).
- Verify that default variables and overrides map cleanly to the database schema.

## Execution
1. Read the relevant service/controller files.
2. Execute searches for missing `@PreAuthorize`, missing `@Transactional`, and unsafe state changes.
3. Produce a structured bug report mapping findings to **Critical**, **Major**, and **Minor** severity levels.
4. Recommend actionable code changes for every identified bug.

