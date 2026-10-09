### Use Case ID
UC-1.10

### Use Case Name
Moderator Account Management

### Actor(s)
Administrator, Moderator (limited to managing Contributor invitations they personally sent)

### Precondition(s)
The actor holds a valid, authenticated, ACTIVE session. Administrator privileges are required for all Moderator-account management actions. Moderators hold network-wide scope without an institution assignment. 

### Main Flow
1. The Administrator navigates to User Management and selects Invite User, which opens the invitation modal.
2. The Administrator inputs the invitee's email address and selects the **Moderator** role. Selecting this role hides the destination institution selector, automatically enforcing a network-wide scope.
3. The Administrator submits the invitation request.
4. The system validates the request as a network-scoped Moderator invitation.
5. The system generates a unique, single-use invitation token bound to the invitee's email address, valid for 72 hours, and stores the hashed token.
6. The system creates or updates the invitee's account record in PENDING state, reusing any existing PENDING, PENDING_EMAIL_UNDELIVERED, CANCELLED, or EXPIRED record for that email. If an account with the email exists in an ACTIVE or INACTIVE state, the system rejects the invitation. Older unused invitation tokens for the same email are invalidated.
7. The system dispatches an activation email containing the activation link.
8. The invitee completes activation by setting their password.
9. The account transitions to ACTIVE, receives the Moderator role, remains institution-agnostic, and gains network-wide access to the Approval Workflow and Contributor-management privileges.

### Alternative Flow(s)
**A1 — Invitation Email Undelivered:** If email dispatch fails after all retries, the account remains in PENDING_EMAIL_UNDELIVERED until an Administrator verifies the address and triggers a resend (A8). 

**A2 — Deactivate Moderator Account:** An active Administrator deactivates an ACTIVE Moderator account. The system revokes all active sessions, blocks login, retains historical review activity and audit data, and records the action in the audit log.

**A3 — Reactivate Moderator Account:** An active Administrator reactivates an INACTIVE Moderator account. Existing credentials remain valid, and no new session token is issued. 

**A4 — Remove Moderator Account:** An active Administrator removes a Moderator account. If the account has any historical footprint (submissions, media uploads, review actions, albums, or audit entries), it is automatically deactivated to preserve data integrity. A completely footprint-free account is permanently hard-deleted.

**A5 — Erase Personal Data (Right to Be Forgotten):** The Admin Owner permanently scrubs the user's name, email, avatar, credentials, and unpublished media uploads. Their submissions and review history remain but no longer identify them.

**A6 — Moderator Invites Contributor:** A Moderator invites a Contributor to any institution, assigning only the Contributor role (UC-1.3). 

**A7 — Moderator Manages Own Sent Invitations:** A Moderator may resend, cancel, or delete a Contributor invitation they personally sent. A Moderator cannot deactivate, reactivate, or delete an already-ACTIVE Contributor account. 

**A8 — Cancel Pending Moderator Invitation:** An active Administrator cancels a pending Moderator invitation (PENDING, PENDING_EMAIL_UNDELIVERED, or EXPIRED). The system deletes all outstanding tokens for that email and sets the account to CANCELLED. 

**A9 — Resend Pending Moderator Invitation:** An active Administrator resends an invitation for an account in PENDING_EMAIL_UNDELIVERED, EXPIRED, or CANCELLED state. The system generates a fresh 72-hour token, invalidates prior open tokens, and resets the account to PENDING. 

**A10 — Moderator as Promotion or Transfer Target:** A Moderator may be the target of an Administrator promotion proposal or an Admin Owner Transfer (UC-1.1). 

**A11 — Change Role:** Any active Administrator may move an active account directly between Contributor and Moderator using the Change Role modal.
* **Contributor → Moderator (Promotion):** The Admin proposes promoting an active Contributor to Moderator. This requires the Contributor's confirmation. Upon confirmation, the system changes the role to Moderator, automatically clears the account's institution assignment, invalidates active sessions requiring a re-login, and records USER_ROLE_CHANGED.
* **Moderator → Contributor (Demotion):** The Admin selects Contributor and is required to assign an active target institution. This change is immediate.
* **Either direction:** Any submissions the account currently holds under an active review lock are released back to the queue.

### Postcondition(s)
A new Moderator account exists in PENDING or ACTIVE state, whether created through invitation or through role change from Contributor. An existing Moderator account has been deactivated, reactivated, removed (or deactivated if footprint exists), anonymized, demoted to Contributor, or had its pending invitation cancelled or resent. Any review locks held at the time of a role change or deactivation have been released back to the Approval Queue. All state-changing actions are reflected in the audit log.
