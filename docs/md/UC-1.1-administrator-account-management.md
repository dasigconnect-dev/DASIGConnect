# UC-1.1 Administrator Account Management

## 1.1.1 Use Case Diagram

To be generated once the team confirms the final flow.

## 1.1.2 Use Case Description

**Use Case ID:** UC-1.1

**Use Case Name:** Administrator Account Management

**Actor(s):** Administrator, Super Administrator

**Precondition:** The actor holds a valid, authenticated ACTIVE session with Administrator or Super Administrator privileges. The initial Super Administrator account is provisioned directly by the development team prior to pilot deployment and is outside the scope of this use case.

## Main Flow

1. The actor opens the account management panel and selects `Invite New Administrator`.
2. The system validates the request as a network-scoped Administrator invitation with no institution assignment.
3. The system generates a unique, single-use, time-sensitive invitation token bound to the invitee email address, valid for 72 hours.
4. The system stores only the token hash, creates or updates the invitee account record in `pending` state, and marks older unused invitations for the same email as used.
5. The system dispatches an activation email containing the activation link with the raw token.
6. The invitee completes activation by setting their password.
7. The account transitions to `active`, receives the `administrator` role, remains institutionless, and can invite Administrators, Validators, and Contributors.

## Alternative Flows

- **A1 - Invitation Email Undelivered:** If dispatch fails after retries, the account remains in `pending_email_undelivered` until an Administrator verifies the address and triggers a resend.
- **A2 - Super Administrator Removes Administrator:** Only the Super Administrator may deactivate or remove another Administrator account. The system revokes active sessions for the removed or deactivated account. If a non-Super Administrator attempts this action, the system rejects it with an authorization error.
- **A3 - Reactivate Administrator:** The Super Administrator may reactivate a previously deactivated Administrator account without requiring re-invitation.
- **A4 - Super Administrator Transfer:** The current Super Administrator requests transfer to an existing ACTIVE Administrator. The target Administrator must confirm before the transfer takes effect. On confirmation, the outgoing account is demoted to standard Administrator, the incoming account becomes Super Administrator, outgoing sessions are revoked, and the transfer is recorded in the audit log.
- **A5 - Super Administrator Unreachable:** If the Super Administrator cannot be reached to initiate a transfer, resolution is an out-of-scope post-pilot governance concern requiring direct development team or DOST Region 7 oversight.

## Postcondition

A new Administrator account exists in `pending`, `pending_email_undelivered`, or `active` state, or an existing Administrator account has been deactivated, reactivated, or involved in a confirmed Super Administrator transfer. State-changing account management actions are reflected in the audit log.

## Business Rules

- Administrator accounts are network-scoped and are not assigned to an institution.
- Administrator invitation tokens remain valid for 72 hours and are single-use.
- Standard Administrators may invite Administrators and institution users but may not deactivate, remove, or reactivate Administrator accounts.
- A deactivated Administrator account cannot be re-invited to bypass Super Administrator reactivation.
- Only one active Administrator account may hold Super Administrator designation.
- Super Administrator transfer requires confirmation from the incoming Administrator.
- User sessions are revoked when an account is deactivated, removed, or loses Super Administrator privileges.

## Backend Coverage

- Migration: `V28__administrator_account_management.sql`
- Entity updates: `User`, `InvitationToken`
- DTO updates: `CreateInvitationRequestDto`, `InvitationResponseDto`, `InvitationValidateResponseDto`, `PendingInvitationDto`, `UserDto`, `SuperAdministratorTransferResponseDto`
- Services: `InvitationService`, `UserService`, `JWTService`, `AuditLogService`
- Controllers: `InvitationController`, `UserController`
- Endpoints:
  - `POST /api/v1/invitations`
  - `GET /api/v1/invitations/validate`
  - `POST /api/v1/invitations/accept`
  - `POST /api/v1/invitations/{id}/resend`
  - `PATCH /api/v1/users/{id}/status`
  - `DELETE /api/v1/users/{id}`
  - `POST /api/v1/users/{id}/super-administrator-transfer`
  - `POST /api/v1/users/super-administrator-transfer/confirm`

## Verification

- Focused backend tests passed:
  - `InvitationControllerTest`
  - `UserControllerTest`
  - `InvitationServiceTest`
  - `UserServiceTest`
  - `JWTServiceTest`
- Focused result: 78 tests, 0 failures, 0 errors.
- Full backend result: 294 tests, 0 failures, 0 errors.

## PR Notes

- Scope: backend UC-1.1 Administrator Account Management plus matching documentation.
- Changed areas: database migration, user/invitation entities and DTOs, invitation/user/JWT services, user controller, focused tests, UC documentation.
- Breakage risk: low to medium. Existing contributor and validator invitation behavior is preserved, but the invitation schema now permits null `institution_id` for Administrator invitations.
- Target branch: `dev`.
- Source branch: `feature/uc11-admin-account-management`.
- Reviewer: tag team lead as reviewee before merging.
