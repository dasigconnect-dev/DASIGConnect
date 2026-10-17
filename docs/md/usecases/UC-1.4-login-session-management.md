# UC-1.4 Login & Session Management

**Use Case ID:** UC-1.4

**Use Case Name:** Login & Session Management

**Actor(s):** Contributor, Administrator, Moderator

**Precondition(s):** The actor holds an account record in the appropriate state (`ACTIVE` for login; `PENDING` for first-time activation, handled under UC-1.1/1.3).

## Main Flow

1. The actor logs in with registered credentials.
2. The system validates the account state and issues a session token (JWT), valid for 8 hours (`app.jwt.access-token-ttl-minutes`, default 480), containing the actor's role, `session_version`, and tenant scope.
3. Contributors receive institution-scoped sessions (the JWT carries `institution_id`). Moderators and Admins receive network-scoped sessions — Moderators are institutionless, same as Admins, so neither carries an `institution_id` claim.
4. The system routes the actor to the dashboard appropriate for their role.
5. Admin Management, User Management, System Health, Audit Log, Page Settings, and Watermark Configuration are shown only to Admin users and are hidden from Moderators and Contributors. Institution Management and the Review Queue are shown to Moderators and Admins. Analytics, Calendar, Media Repository, Notifications, Submissions, and Settings are shown to all three roles — Analytics is role-*scoped* (each role sees its own data breadth), not Admin-exclusive.

## Alternative Flows

- **A1 — Invalid Credentials:** Generic authentication error shown; no session issued. Failed attempts are tracked (`AccountLockoutService`); **5** failed attempts within the window lock the account for 15 minutes.
- **A2 — Account Not Yet Activated (`PENDING`/`PENDING_EMAIL_UNDELIVERED`):** Login rejected; actor directed to check email.
- **A3 — Account `EXPIRED`:** Login rejected; actor instructed to contact an Administrator for reissue. *(The account for whoever sent the original invite may in practice be a Moderator — UC-1.3 A5 lets a Moderator reissue an invitation they personally sent — but the invitation may equally have come from an Admin; the actor should be directed to whoever manages their account, not specifically "an Administrator.")*
- **A4 — Account `INACTIVE`:** Login rejected with a deactivation notice, directing the actor to whoever manages their account.
- **A5 — Password Reset (Unauthenticated):** The actor requests a reset link from the login page. Upon use, the account's `session_version` is incremented, which invalidates every previously issued token — forcing re-login on all devices. The new password must satisfy the same policy as A6 and account activation: at least 12 characters, upper- and lower-case, a digit, a special character, no whitespace, and not built from a common fragment (`password`, `qwerty`, `admin`, `dasig`, `welcome`, `letmein`, `123`, `abc`) or the account's own email/name.
- **A6 — Password Change (Authenticated):** A logged-in actor changes their password from Settings, after providing their current password. `session_version` is not touched, so other active sessions are not invalidated. Same password policy as A5.
- **A7 — Session Expiry:** The frontend decodes the JWT's `exp` claim and shows a countdown banner in the final minutes before expiry, with **Stay Logged In** / **Dismiss** actions. Clicking Stay Logged In (or letting the countdown reach zero regardless of the banner) opens a Session Expired modal requiring the actor to re-enter their password — this is a **full re-login** (`POST /auth/login`, new JWT, dashboards/caches reset), not a silent token refresh. A `POST /auth/refresh` endpoint exists in the backend (re-issues a token for the current user without a password) but the frontend does not currently call it — session extension always means answering the password prompt.
- **A8 — Link Facebook Messenger for Notifications:** From Personal Settings, a **Moderator or Administrator** (not Contributors) may link their personal Facebook account to receive Messenger notifications. This is **not an OAuth consent flow** — the system generates a short, random, 10-minute link code; the actor sends that code as a message to the DASIG Facebook Page in Messenger; a Facebook webhook receives and validates it against the stored code hash, completing the link. The actor becomes eligible for Messenger delivery on applicable triggers (UC-3.3 A4). The actor may unlink at any time, after which Messenger delivery reverts to silent skip (UC-3.3 A5).
- **A9 — Unauthorized Admin Surface Access Attempt:** If a Moderator or Contributor attempts to access Admin Management, User Management, System Health, Audit Log, Page Settings, Watermark Configuration, or Facebook Page integration management (token re-authentication) by direct URL or API call, the system rejects the request with an authorization error and does not render the restricted screen in navigation. (Analytics is *not* in this list — every role can reach it; only the data returned is scoped.)
- **A10 — Logout:** The actor logs out from any single device/session. The system blacklists **only that one token** (`JWTService.invalidateToken`) — this is narrower than A5's password-reset revocation, which invalidates every token the account holds via `session_version`. Logging out on one device does not affect sessions open on other devices.

## Postcondition(s)

The actor holds a valid authenticated session scoped to their role and institution (Contributors) or network-wide (Moderators, Admins), or the requested settings/session action has completed. Admin-only surfaces remain unavailable to Moderators and Contributors; Messenger linking remains unavailable to Contributors.

## Session Revocation — Persistence

Both revocation paths are DB-backed (Postgres), so they survive a backend restart and are visible across instances:

- **Single-token logout (A10):** `JWTService.invalidateToken` writes a row to `revoked_tokens` (SHA-256 hash of the raw JWT, never the token; migration `V88`); `JWTService.isBlacklisted` is an indexed point lookup on every validation. `RevokedTokenCleanupJob` deletes rows past their JWT's expiry hourly.
- **Account-wide revocation (A5, plus deactivation / role change / erasure / owner transfer):** `JWTService.invalidateUserTokens` increments `users.session_version`; `JWTService.extractClaims` rejects any token whose baked-in `session_version` claim no longer matches the DB, or whose account is not `active`. This check now lives in `JWTService` itself (not just the HTTP filter), so it applies to every caller.

*(Historical note: before 2026-09-10 both of these were held in in-process `ConcurrentHashMap`s, so a restart re-admitted every logged-out / deactivated / reset token up to its 8-hour expiry, and a second instance saw none of another's revocations. That is fixed.)*

---

_Verified against the running code as of 2026-09-10. Primary sources: `AuthController`/`AuthService` (`login`, `logout`, `refresh`), `JWTService` (`generateAccessToken`, `invalidateToken`, `invalidateUserTokens`, `extractClaims`), `JwtAuthenticationFilter`, `RevokedToken`/`RevokedTokenRepository`/`RevokedTokenCleanupJob`, migration `V88__revoked_tokens.sql`, `PasswordService` (`resetPassword`, `changePassword`), `PasswordPolicy`, `AccountLockoutService`, `MessengerConnectionController`/`MessengerConnectionService`/`MessengerWebhookController`, `frontend/src/app/App.tsx` (`ProtectedRoute` role gates, session countdown/modal logic), `AccountSettingsScreen.tsx` (`canManagePage`, `canUseMessenger`)._

**Corrections from the prior draft of this UC, found this session:**
- Step 3's "Contributors and Moderators receive institution-scoped sessions" was wrong for Moderators — they've been network-wide (institutionless) since the 2026-08-29 role-model change, same as Admins.
- Analytics was listed as an Admin-only surface in step 5 and A9; the `/analytics` route is open to all three roles (`allowedRoles={["admin","moderator","contributor"]}`) by design — it's role-scoped content, not an Admin-exclusive screen (see UC-2.4).
- A8 described a Facebook OAuth authorization flow; the actual mechanism is a 10-minute link-code sent via a Messenger message to the Page, validated through the Messenger webhook — no OAuth consent screen is involved.
- A8's actor was "an Administrator"; the code (`MessengerConnectionService.createLinkCode`) explicitly allows Moderators too and rejects Contributors with `403`.

**Not mentioned in the prior draft at all, added this session:**
- Concrete numbers: 8-hour JWT TTL, 5-attempt/15-minute lockout, the full password policy.
- A7 didn't mention the proactive countdown banner stage, and implied a token refresh where the actual mechanism is full re-login — plus the unused `/auth/refresh` endpoint.
- A10 (Logout) didn't exist as a flow at all, despite `/auth/logout` being a distinct, narrower revocation mechanism (single token) than A5's account-wide revocation.

**Also fixed in code this session:** session revocation was in-process-only (lost on restart, per-instance) — now persisted to Postgres (`revoked_tokens` table + `users.session_version`), see the Session Revocation section above.
