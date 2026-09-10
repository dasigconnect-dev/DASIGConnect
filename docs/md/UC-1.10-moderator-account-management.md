# UC-1.10 Moderator Account Management

**Use Case ID:** UC-1.10

**Use Case Name:** Moderator Account Management

**Actor(s):** Administrator (full management of Moderator accounts), Moderator (invitation/management privileges over Contributor accounts they personally invited — see UC-1.3)

**Precondition(s):** The actor holds a valid, authenticated ACTIVE session with Administrator privileges. Moderators are network-wide and hold no institution assignment.

## Main Flow

1. The Admin navigates to Moderator Management and selects Invite Moderator.
2. The system validates the request as a network-scoped Moderator invitation with no institution assignment.
3. The system generates a unique, single-use, time-sensitive invitation token bound to the invitee's email address, valid for 72 hours, and stores only the token hash.
4. The system creates or updates the invitee's account record in `PENDING` state, reusing an existing `PENDING`, `PENDING_EMAIL_UNDELIVERED`, `CANCELLED`, or `EXPIRED` record for that email if one exists (an existing `ACTIVE` account, or a re-invitation targeting an `INACTIVE` account, returns a conflict error — the latter must go through Reactivation, A3), and marks any older unused invitation tokens for the same email as used.
5. The system dispatches an activation email containing the activation link with the raw token.
6. The invitee completes activation by setting their password.
7. The account transitions to `ACTIVE`, receives the Moderator role, remains institutionless, and gains network-wide review privileges (UC-2.4) plus the Contributor-management privileges defined in UC-1.3.

## Alternative Flows

- **A1 — Invitation Email Undelivered:** If dispatch fails after retries, the account remains `PENDING_EMAIL_UNDELIVERED` until an Admin verifies the address and triggers a resend (A8).
- **A2 — Deactivate Moderator Account:** *(Any active Admin — not Owner-restricted, since the target is not an Admin account.)* Deactivates an `ACTIVE` Moderator account (a non-active target returns a validation error). Revokes all active sessions, blocks login, retains historical review activity and audit data, and records the action in the audit log.
- **A3 — Reactivate Moderator Account:** *(Any active Admin.)* Reactivates a previously deactivated (`INACTIVE`) Moderator account. Existing credentials remain valid; no new session token is issued. A non-inactive target returns a validation error. Re-inviting a deactivated Moderator is rejected; reactivation is the only path back to `ACTIVE`.
- **A4 — Delete Moderator Account:** *(Any active Admin.)* Permanently removes a Moderator account only when it is `INACTIVE`, `CANCELLED`, or `EXPIRED` (an `ACTIVE` target is rejected, requiring deactivation first). If the account has any historical footprint — submissions, media uploads, validation logs/review actions, albums created, or any audit entry — it is not hard-deleted; it persists as an anonymized-at-rest inactive row with a `USER_REMOVED` audit entry. Only a completely footprint-free account is hard-deleted, writing `USER_DELETED` instead. (A moderator who was previously a Contributor may still carry submission/upload footprint from that earlier role.)
- **A5 — Erase Moderator Account (Right to Be Forgotten):** *(Admin Owner only, consistent with UC-1.1 A6's treatment of this sensitive action regardless of target role.)* Target must be `INACTIVE` or `CANCELLED` and must not already be erased. Anonymizes the record in place and writes a `USER_ANONYMIZED` audit entry.
- **A6 — Moderator Invites Contributor:** A Moderator invites a Contributor to any institution (Moderators are not institution-scoped) but may only assign the Contributor role — attempting to invite an Admin or Moderator role returns an authorization error. Full behavior specified in UC-1.3.
- **A7 — Moderator Manages Own Sent Invitations:** A Moderator may resend, cancel, or (once cancelled/expired) delete a Contributor invitation they personally sent, per UC-1.3 A5/A6. A Moderator cannot deactivate, reactivate, or delete an already-`ACTIVE` Contributor account — those actions remain Admin-only.
- **A8 — Cancel Pending Moderator Invitation:** *(Any active Admin.)* Cancels a pending Moderator invitation while the target account is `PENDING`, `PENDING_EMAIL_UNDELIVERED`, or `EXPIRED`. Deletes all outstanding tokens for that email and sets the account to `CANCELLED`.
- **A9 — Resend Pending Moderator Invitation:** *(Any active Admin.)* Resends an invitation for a Moderator account in `PENDING_EMAIL_UNDELIVERED`, `EXPIRED`, or `CANCELLED` state. Generates a fresh 72-hour token, invalidates all prior open tokens, and resets the account to `PENDING`.
- **A10 — Moderator as Promotion or Transfer Target:** A Moderator may be the target of an Admin promotion proposal (UC-1.1 A7) or an Admin Owner Transfer (UC-1.1 A8). Both are specified fully in UC-1.1 and are not duplicated here.
- **A11 — Moderator as Lateral Role-Change Target:** Any active Admin may move an account directly between Contributor and Moderator — immediate, no confirmation step, distinct from A10's promotion-to-admin path. Contributor → Moderator clears the institution assignment; Moderator → Contributor requires assigning an active target institution. Either direction invalidates the account's sessions, and leaving the Moderator role releases any review locks the account currently holds. Recorded as `USER_ROLE_CHANGED`.

## Postcondition(s)

A new Moderator account exists in `PENDING` or `ACTIVE` state; an existing Moderator account has been deactivated, reactivated, deleted (if footprint-free), anonymized, or had its role changed directly (A11); or its pending invitation has been cancelled or resent. All state-changing actions are reflected in the audit log.

---

_Verified against the running code as of 2026-09-10. Primary sources: `InvitationService`, `UserService` (`updateStatus`, `removeUser`, `erasePersonalData`, `changeRole`), `InvitationController`, `UserController`. The `(UC-2.4)` cross-reference in Main Flow step 7 is carried over from the source document as given and has not been independently verified against this repo's own UC numbering conventions._
