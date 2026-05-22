# Module 1 Todos and Current Status

Module 1 is composed of:

- `UC-1.1`: User Provisioning, Onboarding, and Authentication
- `UC-1.2`: Institution Onboarding and Workspace Provisioning
- `UC-1.3`: Content Submission and Self-Service Scheduling

## Current Status

| Member Role | Component Focus | Status |
| --- | --- | --- |
| M1 | Core Data and Infrastructure Setup | Completed |
| M2 | UC-1.1 Backend Services | Completed |
| M3 | UC-1.1 Frontend and Core Auth | Completed |
| M4 | UC-1.2 and UC-1.3 Complex Backend Logic | Completed |
| M5 | UC-1.3 Content Submission UI | Completed except Supabase upload credential verification |

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

## Verification

- Backend focused auth/onboarding tests passed:
  - `InvitationServiceTest`
  - `InvitationControllerTest`
  - `UserServiceTest`
  - `UserControllerTest`
- Focused result: 50 tests, 0 failures, 0 errors.
- Frontend build passed with `npm.cmd run build`.

## Remaining Module 1 Gaps

1. Supabase browser upload cannot be fully verified until the team lead provides Supabase access/credentials.
   Required frontend variables:
   - `VITE_SUPABASE_URL`
   - `VITE_SUPABASE_STORAGE_BUCKET`
   - `VITE_SUPABASE_ANON_KEY`

2. Deployment runtime still needs team-owned credentials:
   - SMTP
   - Supabase service role key
   - Supabase storage bucket
   - frontend base URL
   - database/JWT environment values

3. Submission lookups do not currently provide categories, tags, or preferred time slots. The team needs to confirm whether those are required for Module 1 or can move to a later module.

4. Final manual Module 1 flow still needs to be run end to end after credentials are available:
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
