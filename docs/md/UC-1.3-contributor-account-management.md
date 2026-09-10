# UC-1.3 Contributor Account Management

**Use Case ID:** UC-1.3

**Use Case Name:** Contributor Account Management

**Actor(s):** Administrator (any active Admin — Admin Owner status not required except for admin-on-admin actions, which are out of scope here), Moderator (invitation actions only, scoped to invitations they personally sent for resend/cancel/delete)

**Precondition(s):** The actor holds a valid, authenticated ACTIVE session with Administrator privileges (any active Admin), or Moderator privileges for invitation actions specifically.

## Main Flow

1. The actor navigates to the institution's Contributor management panel and selects "Invite Contributor."
2. The target institution must not be `INACTIVE`; a `PENDING` institution is permitted, not only `ACTIVE`.
3. If the actor is a Moderator, the system permits inviting to any institution (Moderators are network-wide, not institution-scoped) but restricts the assigned role to Contributor only — attempting to invite an Administrator or Moderator role returns an authorization error.
4. The system generates a unique, time-sensitive Invitation Token, valid for 72 hours, and creates the account record in `PENDING` state. If a `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `CANCELLED`, or `EXPIRED` account row already exists for that email, it is reused rather than recreated; an existing `ACTIVE` account for that email returns a conflict error, and — regardless of role — a re-invitation targeting an `INACTIVE` account also returns a conflict error and must go through Reactivation (A4) instead.
5. The system dispatches an activation email containing a single-use activation link scoped to that institution.
6. The invitee completes activation (password setup) and the account transitions to `ACTIVE`, with Row-Level Security binding established to the assigned institution.

## Alternative Flows

- **A1 — Invitation Email Undelivered:** Account remains `PENDING_EMAIL_UNDELIVERED` until resend is triggered.
- **A2 — Token Used on Wrong Institution:** Generic invalid-token error, no institution information disclosed.
- **A3 — Deactivate Contributor:** *(Administrator only — Moderators cannot deactivate or reactivate Contributors.)* The Admin deactivates an `ACTIVE` Contributor account; attempting to deactivate a non-active account returns a validation error. The system revokes all active sessions (token invalidation) and retains submission history unaffected.
- **A4 — Reactivate Contributor:** *(Administrator only.)* The Admin reactivates an `INACTIVE` Contributor account; attempting to reactivate a non-inactive account returns a validation error.
- **A5 — Reissue Invitation:** For a Contributor account in `EXPIRED`, `CANCELLED`, `PENDING`, or `PENDING_EMAIL_UNDELIVERED` state, the actor issues a new Invitation Token, resetting it to `PENDING`. The previous token is invalidated (all open invitation tokens for that email are marked used, so any prior activation link stops working). Actor: Administrator, or a Moderator reissuing an invitation they personally sent.
- **A6 — Delete Contributor Account:** Eligible target states are `INACTIVE`, `CANCELLED`, or `EXPIRED`. If the account has any footprint — a submission, a media upload, a validation/review log, an album it created, or any audit log entry — it is not hard-deleted; instead, it remains as an anonymized-at-rest inactive row, and a `USER_REMOVED` audit entry is written. Only a completely footprint-free account is hard-deleted. Actor: Administrator (any account), or a Moderator (only a Contributor they personally invited, whose invitation is `CANCELLED` or `EXPIRED`).
- **A7 — Erase Contributor Account (Right to Be Forgotten):** A stronger, separate action distinct from A6. Available to the Admin Owner only. Target account must be `INACTIVE` or `CANCELLED`. The system anonymizes the record in place: name nulled, email replaced with a placeholder (`deleted+<id>@deleted.invalid`), unattached uploads soft-deleted, and related notifications/lockout records purged. A `USER_ANONYMIZED` audit entry is written.
- **A8 — Contributor as Role-Change Target:** A Contributor may be promoted directly to Moderator — immediate, any active Admin, no confirmation step, fires an in-app role-change notification — or proposed for Admin, which any active Admin may initiate but which the Contributor must confirm before it takes effect (see UC-1.1 A7). Either promotion clears the Contributor's institution assignment. A former Contributor demoted back from Moderator requires assigning an active target institution. Full mechanics are specified in UC-1.1 and UC-1.10 and are not duplicated here.

## Postcondition(s)

A Contributor account exists in `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `ACTIVE`, `CANCELLED`, or `EXPIRED` state, bound to a specific institution, or an existing account has been deactivated, reactivated, hard-deleted (if footprint-free), anonymized (A7), or had its role changed (A8). A "deleted" account with any historical footprint persists as an anonymized inactive row rather than disappearing entirely.

---

_Verified against the running code as of 2026-09-10. Primary sources: `InvitationService` (`createInvitation`, `prepareExistingPendingUser`, `resend`, `cancel`), `UserService` (`updateStatus`, `removeUser`, `erasePersonalData`, `changeRole`), `InvitationController`, `UserController`. The Main Flow step 4 inactive-reinvite conflict is enforced uniformly across all three roles as of this revision (previously only checked for Moderator/Admin existing rows — fixed)._
