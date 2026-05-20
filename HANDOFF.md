# Handoff — 2026-05-20

## What was done this session
- Fetched remote, created local `dev` branch from `origin/dev`, and merged `origin/main` into `dev` — 20 commits, 0 conflicts
- Fixed 3 pre-existing test compatibility issues on `dev` exposed by the merge:
  1. `CreateInstitutionRequest` 2-arg constructor calls in `InstitutionDtoTest` and `InstitutionServiceTest` (6 sites) — updated to 3-arg after `emailDomain` was added on dev
  2. `BackendApplication` custom `flyway` and `dbDiagnostics` beans ignoring `spring.flyway.enabled=false` — fixed with `@ConditionalOnProperty`, restoring all `@WebMvcTest` and `@SpringBootTest` tests
  3. `InvitationServiceTest` missing `buildInvitationLink` mock — stub added
- Committed fixes on `dev` (`3966219`) — 124 tests, 0 failures, BUILD SUCCESS
- Pulled `origin/main` to resolve local divergence (2 new CI workflow files)
- Merged `dev` into local `main` — 1 conflict in `HANDOFF.md` (old vs new session notes), resolved by keeping today's version
- Removed `docs/` from `.gitignore` so the folder can be tracked in git
- Updated `TASKS.md`, `CLAUDE.md`, `HANDOFF.md`

## Files changed
- `backend/src/main/java/com/dasigconnect/backend/BackendApplication.java` — `@ConditionalOnProperty` on `flyway` and `dbDiagnostics` beans
- `backend/src/test/.../InstitutionDtoTest.java` — 3-arg `CreateInstitutionRequest`
- `backend/src/test/.../InstitutionServiceTest.java` — 3-arg `CreateInstitutionRequest` (5 sites)
- `backend/src/test/.../InvitationServiceTest.java` — `buildInvitationLink` stub
- `.gitignore` — removed `docs/` exclusion
- `TASKS.md`, `HANDOFF.md`, `CLAUDE.md` — updated

## What's next
1. **Commit the remaining uncommitted changes** on `main`:
   - `.gitignore` (docs/ removal)
   - Deleted `.agent/skills/frontend/dasigconnect-submission-uc13.html` (from merge — review if intentional)
2. **Add docs files** — user has SRS, SDD, proposal PDFs and a `md/` subfolder to place in `docs/`; folder exists but is empty
3. **Read and analyze docs** — once files are in `docs/`, read the SRS/SDD and update `TASKS.md` and `CLAUDE.md` to reflect full spec details
4. **Push** `main` to `origin/main` and `dev` to `origin/dev` (9 commits ahead, not pushed)
5. **Update TASKS.md frontend section** — dev merge brought in frontend Module 1 work (`feat/M3-auth-page`) and UC-1.3 content submission (`feat/content-submission`); these are marked "Not Started" in TASKS.md but are actually in progress/done on dev

## Blockers / notes
- `main` is 9 commits ahead of `origin/main` — **not yet pushed**
- `dev` is 22 commits ahead of `origin/dev` — **not yet pushed**
- `docs/` folder is empty; user needs to copy SRS, SDD, proposal, and `md/` subfolder into it before commit
- `.agent/skills/frontend/dasigconnect-submission-uc13.html` shows as deleted in git status — verify with user whether this deletion is intentional before staging
- `@ConditionalOnProperty` on BackendApplication beans is critical: without it, any new Flyway migration added on dev will break Spring context tests
- Do not increase HikariCP beyond 5 connections (Supabase Session Pooler hard limit)
- Keep `DATABASE_USER` in `application.properties` and `DATABASE_USERNAME` in `.env` in sync
