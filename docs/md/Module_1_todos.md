# Module 1 Todos and Current Status

Module 1 is composed of:

- `UC-1.1`: User Provisioning, Onboarding, and Authentication
- `UC-1.2`: Institution Onboarding and Workspace Provisioning
- `UC-1.3`: Content Submission and Self-Service Scheduling

## Current Status

| Member Role | Component Focus                         | Status                                                                 |
| ----------- | --------------------------------------- | ---------------------------------------------------------------------- |
| M1          | Core Data and Infrastructure Setup      | Completed                                                              |
| M2          | UC-1.1 Backend Services                 | Completed                                                              |
| M3          | UC-1.1 Frontend and Core Auth           | Completed                                                              |
| M4          | UC-1.2 and UC-1.3 Complex Backend Logic | Completed                                                              |
| M5          | UC-1.3 Content Submission UI            | In progress: save draft / submit review browser issue still unresolved |

## Completed

1. Core schema and infrastructure are in place: users, institutions, invitation tokens, account lockouts, submissions, slot reservations, audit logging, JWT, email service, tenant scope, and Flyway migrations.

2. Auth and onboarding are implemented:
   - login/logout
   - account lockout
   - forgot/reset password
   - invitation create/validate/accept/resend
   - `GET /api/v1/me`
   - scoped user listing
   - scoped user counts

3. Invitation lifecycle is hardened:
   - active accounts cannot be re-invited
   - only one user account can exist per email
   - creating or resending an invite supersedes older unused, unexpired invite links for the same email
   - validators can only invite contributors into their own institution
   - validators must use the institution email domain rule
   - administrators can invite external emails such as Gmail for convenience

4. Institution onboarding is implemented:
   - canonical `GET /api/v1/institutions`
   - canonical `POST /api/v1/institutions`
   - legacy `/api/v1/admin/institutions` remains supported
   - institution email domains are unique

5. Submission backend and frontend wiring are implemented:
   - `GET /api/v1/submissions`
   - `GET /api/v1/submissions/{id}`
   - `POST /api/v1/submissions`
   - `PATCH /api/v1/submissions/{id}`
   - `DELETE /api/v1/submissions/{id}`
   - `POST /api/v1/submissions/{id}/submit`
   - `POST /api/v1/submissions/{id}/media`
   - `GET /api/v1/submissions/lookups`
   - `POST /api/v1/submissions/{id}/evaluate-slot`

6. Dashboard wiring is implemented:
   - role-specific admin/validator/contributor dashboard views
   - live institution count
   - live user counts
   - live pending invite count
   - pending invite list
   - resend invite action
   - current users list for selected institution

7. Frontend auth state now hydrates from `GET /api/v1/me` after login, invite accept, session modal relogin, and saved-token restore. Institution display now comes from the backend profile; email-domain guessing is only a fallback.

8. Local SMTP has been configured through ignored environment files. Production/staging still need team-owned SMTP values.

9. Local Supabase configuration is in place:
   - backend database connection through `DASIG_DATABASE_*`
   - backend Supabase service config through `DASIG_SUPABASE_*`
   - frontend direct upload config through `VITE_SUPABASE_*`
   - shared storage bucket: `dasigconnect-media`

10. Fresh Supabase database startup is fixed. Flyway baselines at version `0`, then applies V1 through V4 so Module 1 tables are created correctly.

11. Testing-mode submission changes are in place:

- backend guard rail enforcement is configurable with `APP_GUARDRAILS_ENFORCED`
- local default is currently `false`
- guard rail violations return structured `422` responses
- frontend submit buttons are not disabled by readiness score while testing
- frontend draft payloads provide safe defaults for blank backend-required fields

12. Submission queue design has been improved locally with a clearer queue panel, count badge, stronger item containers, and active/hover states.

## Verification

- Backend focused auth/onboarding tests passed:
  - `InvitationServiceTest`
  - `InvitationControllerTest`
  - `UserServiceTest`
  - `UserControllerTest`
- Focused result: 50 tests, 0 failures, 0 errors.
- Frontend build passed with `npm.cmd run build`.
- Fresh Supabase DB startup passed: backend applied V1 through V4, then a second startup validated migrations with 0 pending migrations.
- Focused submission backend tests passed:
  - `SlotReservationServiceTest`
  - `SubmissionServiceTest`
  - `SubmissionControllerTest`
- Focused submission result: 42 tests, 0 failures, 0 errors.

## Remaining Module 1 Gaps

1. Save draft and submit-for-review are still the active Module 1 blocker. Guard rail blocking has been bypassed for testing and frontend defaults were added, but the full browser flow still needs manual debugging/verification against the active backend and Supabase setup.

2. Supabase browser upload credentials are configured locally, but the actual upload path still needs manual verification through the submission form.

3. Submission queue design has been improved locally, but still needs user/team review with real queue data and mobile/responsive widths.

4. Deployment runtime still needs team-owned credentials:
   - SMTP
   - Supabase service role key
   - Supabase storage bucket
   - frontend base URL
   - database/JWT environment values

5. Submission lookups do not currently provide categories, tags, or preferred time slots. The team needs to confirm whether those are required for Module 1 or can move to a later module.

6. Final manual Module 1 flow still needs to be run end to end after credentials are available:
   - admin login
   - create institution
   - invite validator
   - validator accepts invite
   - validator invites contributor
   - contributor accepts invite
   - contributor creates draft/submits content
   - dashboard pending invite/user/submission values update correctly

## Out Of Module 1

- UC-2.1 validator review queue actions
- UC-2.2 media library / asset picker backend
- UC-2.3 SSE notifications
- UC-2.4 analytics dashboard
- UC-3.x calendar, auto-publish, manual publishing fallback, AI captions, and recommendations

## Branch Note

Current work is on `feature/uc13-submission-backend`. Merge to main only after local verification, review, and team approval.
