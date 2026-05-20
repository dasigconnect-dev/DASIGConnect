# Handoff - 2026-05-20

## What was done this session
- Merged `origin/main` into local `dev` branch — 20 commits, 0 merge conflicts, 22 files changed (1,645 insertions)
- Fixed 3 pre-existing test compatibility issues on `dev` (all exposed by the merge):
  1. **`CreateInstitutionRequest` 3-arg constructor** — `dev` added `emailDomain` as a required field, but 6 test call sites in `InstitutionDtoTest` and `InstitutionServiceTest` still used the old 2-arg form; updated all 6
  2. **`BackendApplication` conditional beans** — custom `flyway` and `dbDiagnostics` `@Bean` methods ignored `spring.flyway.enabled=false`, causing all `@WebMvcTest` and `@SpringBootTest` tests to fail with ApplicationContext errors; fixed with `@ConditionalOnProperty(name="spring.flyway.enabled", havingValue="true", matchIfMissing=true)` on both beans
  3. **`InvitationServiceTest` missing mock** — `emailService.buildInvitationLink()` (added on `dev`) was unmocked in `createInvitation_validRequest_savesTokenAndSendsEmail`, returning `null` and failing the URL assertion; added stub
- Final test result: **124 tests, 0 failures, 0 errors — BUILD SUCCESS**
- Committed all fixes on `dev` (`3966219`)
- Merged `dev` into local `main`
- Updated `TASKS.md`, `HANDOFF.md`, `CLAUDE.md`

## Files changed
- `backend/src/main/java/com/dasigconnect/backend/BackendApplication.java` — `@ConditionalOnProperty` on `flyway` and `dbDiagnostics` beans
- `backend/src/test/java/com/dasigconnect/backend/model/dto/institution/InstitutionDtoTest.java` — 3-arg `CreateInstitutionRequest`
- `backend/src/test/java/com/dasigconnect/backend/service/InstitutionServiceTest.java` — 3-arg `CreateInstitutionRequest` (5 sites)
- `backend/src/test/java/com/dasigconnect/backend/service/InvitationServiceTest.java` — `buildInvitationLink` stub
- `TASKS.md`, `HANDOFF.md`, `CLAUDE.md` — updated

## Current git state
- Branch: `main` (after merging dev in)
- `main` has been merged with `dev` locally — not yet pushed to `origin/main`
- `dev` is 22 commits ahead of `origin/dev` — not yet pushed to `origin/dev`

## What's next
1. Push `main` to `origin/main` and `dev` to `origin/dev` (say "push it" to proceed)
2. Start **UC-1.3 Content Submission** — `SubmissionController`, `SubmissionService`, DTOs, repository methods, tests

## Blockers / notes
- `Institution.emailDomain` and `V2__add_institution_email_domain.sql` are now on both `dev` and `main` after the merge
- `@ConditionalOnProperty` fix is critical: without it, adding new Flyway migrations will keep breaking Spring context tests
- Do not increase HikariCP beyond 5 connections (Supabase Session Pooler hard limit)
- Keep `DATABASE_USER` in `application.properties` and `DATABASE_USERNAME` in `.env` in sync or datasource fails at startup
