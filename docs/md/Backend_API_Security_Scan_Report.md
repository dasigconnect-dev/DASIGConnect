# Backend API Security Scan Report

**Base URL:** `http://localhost:8080`  
**Environment:** Local backend server  
**Scan Date:** 2026-08-31 to 2026-09-01  
**Scope:** Authentication, authorization, security error handling, SQL injection probes, XSS reflection checks, and role-based API access.

## Executive Summary

Backend priority hardening is complete for the tested local API scope. The original scan produced several misleading failures because placeholder values such as `<userId>` and guessed routes were sent directly to the backend. After hardening, malformed request bodies, invalid UUIDs, missing authentication, wrong HTTP methods, and unsupported media types now return controlled responses instead of generic `500 Internal Server Error` responses.

Authenticated scans were performed with Contributor, Moderator, and Admin tokens. No SQL error disclosure, stack trace disclosure, token leakage, or reflected XSS payload was observed in the tested endpoints.

## Backend Hardening Changes

- Standardized Spring Security unauthorized and access-denied responses into the project `ApiResponse` envelope.
- Returned `401 UNAUTHORIZED` for missing, expired, invalid, or revoked sessions.
- Returned `403 ACCESS_DENIED` for authenticated users without the required role.
- Added global handling for malformed JSON/request bodies.
- Added global handling for invalid UUID path and query parameters.
- Added global handling for unsupported media types.
- Added global handling for missing request parts, missing path variables, invalid request binding, unacceptable media types, and multipart errors.
- Updated backend controller tests to assert the hardened status codes and response shapes.

## Verification Summary

### Automated Tests

```text
Command: .\mvnw.cmd test
Result: 536 tests, 0 failures, 0 errors, 0 skipped
```

### Security Headers Observed On Backend Responses

```text
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
Cache-Control: no-store, must-revalidate, no-cache, max-age=0
Pragma: no-cache
Expires: 0
Vary: Origin
Vary: Access-Control-Request-Method
Vary: Access-Control-Request-Headers
```

## Unauthenticated And Error-Handling Results

| Request | Result | Status |
| :--- | :--- | :--- |
| `GET /health` | `200 OK` | Pass |
| `GET /api/v1/me` | `401 UNAUTHORIZED` | Pass |
| `POST /api/v1/auth/refresh` without token | `401 UNAUTHORIZED` | Pass |
| `POST /api/v1/auth/login` with malformed body | `400 VALIDATION_ERROR` | Pass |
| `POST /api/v1/auth/forgot-password` with malformed body | `400 VALIDATION_ERROR` | Pass |
| `POST /api/v1/auth/reset-password` with malformed body | `400 VALIDATION_ERROR` | Pass |
| `POST /api/v1/auth/change-password` with malformed body | `400 VALIDATION_ERROR` | Pass |
| `POST /api/v1/auth/change-password` with valid body but no token | `401 UNAUTHORIZED` | Pass |
| `POST /api/v1/auth/login` with invalid credentials | `401 UNAUTHORIZED` | Pass |
| `POST /api/v1/auth/login` with unsupported content type | `415 UNSUPPORTED_MEDIA_TYPE` | Pass |
| `POST /api/v1/me` | `405 METHOD_NOT_ALLOWED` | Pass |
| `PATCH /health` | `405 METHOD_NOT_ALLOWED` | Pass |

## Invalid Input And Injection-Style Probe Results

| Request | Result | Status |
| :--- | :--- | :--- |
| `GET /api/v1/users?institutionId=not-a-uuid` | `400 VALIDATION_ERROR` | Pass |
| `GET /api/v1/users/not-a-uuid` | `400 VALIDATION_ERROR` | Pass |
| `PATCH /api/v1/users/not-a-uuid/status` | `400 VALIDATION_ERROR` | Pass |
| `GET /api/v1/users/1'` | `400 VALIDATION_ERROR` | Pass |
| `GET /api/v1/submissions/1'` | `400 VALIDATION_ERROR` | Pass |
| `GET /api/v1/audit-log?id=1'` | `200 OK`; no DB error disclosure | Pass |
| `GET /api/v1/audit-log?q=<script>alert(1)</script>` | `200 OK`; no unsafe reflection observed | Pass |

## Contributor Authenticated Scan

Authenticated role: `contributor`  
Institution scope: `7c4ae008-7c9e-4b71-95dc-16bb92444c90`

| Request | Result | Status |
| :--- | :--- | :--- |
| `GET /api/v1/me` | `200 OK` | Pass |
| `GET /api/v1/submissions` | `200 OK` | Pass |
| `GET /api/v1/submissions/lookups` | `200 OK` | Pass |
| `GET /api/v1/calendar` | `200 OK`; public/cross-institution calendar behavior observed | Pass with observation |
| `GET /api/v1/notifications` | `200 OK` | Pass |
| `GET /api/v1/notifications/unread-count` | `200 OK` | Pass |
| `GET /api/v1/users?institutionId=<own-institution-id>` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/users/counts?institutionId=<own-institution-id>` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/settings/page` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/settings/watermark` | `200 OK` | Pass |
| `GET /api/v1/validation/queue` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/system-health/summary` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/audit-log` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/analytics/summary` | `200 OK`; contributor-scoped response | Pass |

Contributor analytics response showed `scopeRole=contributor`, `adminView=false`, empty institution filter options, and no admin/operational-health analytics.

## Moderator Authenticated Scan

Authenticated role: `moderator`  
Institution scope: `null`

| Request | Result | Status |
| :--- | :--- | :--- |
| `GET /api/v1/me` | `200 OK` | Pass |
| `GET /api/v1/users/network` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/users/admins` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/users/moderators` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/institutions` | `200 OK` | Pass |
| `GET /api/v1/submissions` | `200 OK` | Pass |
| `GET /api/v1/validation/queue` | `200 OK` | Pass |
| `GET /api/v1/calendar` | `200 OK`; network-wide moderator scope observed | Pass with observation |
| `GET /api/v1/system-health/summary` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/audit-log` | `403 ACCESS_DENIED` | Pass |
| `GET /api/v1/settings/page` | `403 ACCESS_DENIED` | Pass |

Moderator calendar access returned network-wide details. This appears consistent with the current model because moderator accounts are network-scoped with `institutionId=null`. Confirm against RBAC requirements if moderators are later changed to institution-scoped users.

## Admin Authenticated Scan

Authenticated role: `admin`  
Institution scope: `null`

| Request | Result | Status |
| :--- | :--- | :--- |
| `GET /api/v1/me` | `200 OK` | Pass |
| `GET /api/v1/users/network` | `200 OK` | Pass |
| `GET /api/v1/users/admins` | `200 OK` | Pass |
| `GET /api/v1/users/moderators` | `200 OK` | Pass |
| `GET /api/v1/institutions` | `200 OK` | Pass |
| `GET /api/v1/users?institutionId=<institution-id>` | `200 OK` | Pass |
| `GET /api/v1/users/counts?institutionId=<institution-id>` | `200 OK` | Pass |
| `GET /api/v1/submissions` | `200 OK` | Pass |
| `GET /api/v1/validation/queue` | `200 OK` | Pass |
| `GET /api/v1/analytics/summary` | `200 OK`; admin/network view | Pass |
| `GET /api/v1/system-health/summary` | `200 OK` | Pass |
| `GET /api/v1/audit-log` | `200 OK` | Pass |
| `GET /api/v1/settings/page` | `200 OK` | Pass |
| `GET /api/v1/settings/watermark` | `200 OK` | Pass |
| `GET /api/v1/calendar` | `200 OK` | Pass |

Admin access to user lists, audit logs, system health, network analytics, and page settings is expected for the current RBAC model.

## Observations

- Localhost uses plain HTTP, so HTTPS and HSTS checks are expected to fail locally. Re-test HTTPS and `Strict-Transport-Security` only after deployment behind HTTPS.
- `GET /api/v1/settings/watermark` exposes `updatedBy` as an admin email. This is low sensitivity and appears acceptable for watermark display, but can be reduced to a display name or user ID if stricter privacy is required.
- Admin audit logs expose IP address and user-agent fields. This is expected for admin-only audit visibility and was blocked for Contributor and Moderator roles.
- Calendar responses expose different detail levels by role. Contributor access showed public/scoped behavior; Moderator and Admin showed broader network visibility.

## Conclusion

The backend passed the priority API hardening checks for local security testing. The previous `500` responses caused by malformed input and placeholder path variables were addressed. Authenticated role checks confirmed expected access boundaries for Contributor, Moderator, and Admin users across the tested core endpoints.

Before final deployment, run a production HTTPS scan and verify HSTS, deployment proxy headers, and production CORS origin settings.
