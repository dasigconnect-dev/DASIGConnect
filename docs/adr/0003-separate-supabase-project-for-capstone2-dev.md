# ADR-0003: Separate Supabase project for Capstone 2 development
- Status: Accepted
- Date: 2026-05-30
- Deciders: DASIGConnect team

## Context
Capstone 1 is presented and its system must remain demoable (the panel can ask to see it
again). Capstone 2 adds many structural, migration-heavy changes (V28+): new columns with
backfills, new RLS policies, and the `visibility` backfill that — done wrong — freezes the
live submission/publishing flow. Flyway is forward-only, and a shared-DB history mismatch
already caused a `FlywayValidateException` once (the V6–V9/V10 checksum incident). The
200-asset load test also generates junk data and stresses the 5-connection pool.

Running experimental migrations and load tests against the production Supabase project
that backs the Capstone 1 demo is an unacceptable risk.

## Decision
Provision a **dedicated Supabase project** (`dasigconnect-dev`) for Capstone 2 work, with
its own Storage bucket (`dasigconnect-media-dev`). The Capstone 1 production project is
**frozen** — no Capstone 2 migrations run against it. The backend selects the dev project
via a `dev` Spring profile / separate `backend/.env`; `application.properties` prod values
are untouched. Fresh project → **baseline Flyway at version 0** so V1→V27→V28+ apply
cleanly. HikariCP stays at 5 (same pooler constraint).

## Consequences
- Free tier allows 2 projects → no cost; dev project pauses when idle (resume on demand).
- Capstone 1 demo stays stable and re-runnable.
- Eval datasets (D1/D2/D5) are seeded into the dev project; no contamination of prod data.
- A later, deliberate migration/cutover step is needed to bring Capstone 2 to production —
  track it as an explicit task, not an accident.
- Two environments to keep in sync (env vars, secrets) — document both in the handoff.
