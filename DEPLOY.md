# Deploying the dev environment

- **Frontend:** Vercel → `https://dev.dasigconnect.com`
- **Backend:** Railway (Docker, `backend/railway.toml`) → `https://api-dev.dasigconnect.com`
- **Database:** Supabase Postgres (Session Pooler)
- **Media:** Cloudflare R2

Deploy the `dev` branch.

---

## 1. Backend — Railway

### Service
- Create the service from this repo, set **Root Directory** to `backend` in
  Settings (Railway has no `rootDir` field inside `railway.toml` — the
  dashboard setting is the source of truth for a monorepo).
- Build/deploy behavior comes from `backend/railway.toml` — Dockerfile
  builder, health check path `/health`, restart-on-failure.
- Custom domain `api-dev.dasigconnect.com` → Railway dashboard → Settings →
  Networking → Custom Domain → add the CNAME Railway gives you at your DNS
  provider.

### Plan reality
- Railway's Hobby plan does **not** spin the service down after idle the way
  Render's free tier does — no cold start, no keepalive pinger needed. (Confirm
  this against whichever plan the project is actually on before relying on it;
  a "Serverless"/sleep toggle can be enabled per-service and should stay off
  here.)
- Railway bills usage-based (compute + egress) rather than a flat
  instance-hours cap — no equivalent to Render's "one free service only" limit,
  but keep an eye on usage in the dashboard.
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` is still worth
  setting if the service plan has a comparably small memory ceiling; adjust to
  the actual allocated RAM.

### Environment variables (Railway dashboard → Variables)
Railway does not support committing a non-secret var list alongside secrets
the way `render.yaml`'s `sync: false` did — set everything below directly in
the dashboard. Start with the minimum and add Facebook / AI only when you
actually demo those features.

**Set now:**

| Key | Value |
|---|---|
| `PORT` | Railway sets this automatically — do not override; `server.port=${PORT:8080}` reads it |
| `DASIG_DATABASE_URL` | `jdbc:postgresql://<supabase-pooler-host>:5432/postgres` |
| `DASIG_DATABASE_USER` | `postgres.<project-ref>` |
| `DASIG_DATABASE_PASSWORD` | Supabase DB password |
| `JWT_SECRET` | fresh 64-hex (`openssl rand -hex 64`) — **not** the local/leaked one |
| `APP_FRONTEND_BASE_URL` | `https://dev.dasigconnect.com` |
| `APP_GUARDRAILS_ENFORCED` | `true` |
| `BACKEND_PUBLIC_BASE_URL` | `https://api-dev.dasigconnect.com` (or the Railway-issued `*.up.railway.app` domain if no custom domain yet) — **drives the media proxy URL and the Facebook OAuth redirect default; do not leave this at the `localhost:8080` fallback in production** |
| `RESEND_API_KEY` | `re_…` |
| `MAIL_FROM` | address on a Resend-verified domain |
| `MAIL_FROM_NAME` | `DASIGConnect` |
| `MAIL_REPLY_TO` | a monitored inbox |
| `R2_ACCOUNT_ID` / `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_PUBLIC_BASE_URL` | from Cloudflare R2 |
| `R2_BUCKET` | `dasigconnect-media` |

**Leave unset until needed:**

| Key | Why wait |
|---|---|
| `FACEBOOK_PAGE_ACCESS_TOKEN` / `FACEBOOK_PAGE_ID` / `FACEBOOK_APP_ID` / `FACEBOOK_APP_SECRET` | Publishing self-disables while blank. When you do set it, use a **test** Facebook page — a dev submission that reaches `published` posts publicly. |
| `FACEBOOK_API_VERSION` (`v25.0`) / `FACEBOOK_OAUTH_REDIRECT_URI` | Only meaningful once the tokens above are set. Defaults off `BACKEND_PUBLIC_BASE_URL` if unset — only set this explicitly if the whitelisted Meta redirect URI differs from that. |
| `ANTHROPIC_API_KEY` / `VOYAGE_API_KEY` | **Billed per call.** AI caption/classification/suggestion degrade gracefully while blank, and `EmbeddingReconciliationJob` skips itself. Set a spend limit in the Anthropic console before adding the key. |
| `DASIG_SUPABASE_SERVICE_ROLE_KEY` | Only if Claude Vision 401s fetching Supabase-hosted images. |

### One-time external config
- **Resend → Domains:** verify your sending domain (SPF + DKIM DNS records) —
  needed for any email to send.
- **Supabase:** before the first deploy, run
  `SELECT max(version) FROM flyway_schema_history;` and confirm it matches the
  highest `backend/src/main/resources/db/migration/V*.sql`. The app runs
  `flyway.repair()` then `migrate()` on boot; a mismatch fails startup.
- **Backup:** `pg_dump` the Supabase DB before the first deploy and before any
  risky migration — free Supabase has no point-in-time recovery.
- **Only when enabling Facebook:** Meta app → Facebook Login → Valid OAuth
  Redirect URIs → add `https://api-dev.dasigconnect.com/api/v1/facebook/oauth-callback`;
  App Domains → add `dasigconnect.com`.
- **Migrating from an existing Render deploy:** any `media_assets.storage_url`
  rows already backfilled to the old Render `BACKEND_PUBLIC_BASE_URL` host need
  a one-time SQL rewrite to the new Railway host, e.g.
  `UPDATE media_assets SET storage_url = REPLACE(storage_url, '<old Render host>/', '<new Railway BACKEND_PUBLIC_BASE_URL>/') WHERE storage_url LIKE '<old Render host>/%'`
  — not run automatically.

---

## 2. Frontend — Vercel

### Project
- Root directory: `frontend/`
- Build: `npm run build` · Output: `dist` · Install: `npm install`
- SPA routing handled by `frontend/vercel.json`.
- Custom domain `dev.dasigconnect.com` → Vercel dashboard → Domains.

### Environment variables (Vercel dashboard → Settings → Environment Variables)
Vite reads these at **build time**, so a change needs a redeploy.

| Key | Value |
|---|---|
| `VITE_API_URL` | `https://api-dev.dasigconnect.com/api/v1` |

The Supabase `VITE_*` vars are not read by the current frontend — skip them.

---

## 3. CORS

No code change needed. The backend auto-adds `APP_FRONTEND_BASE_URL` to the
allowed CORS origins when it isn't a localhost URL. It must match the browser
origin **exactly**: `https://dev.dasigconnect.com` — no trailing slash, no path.

---

## 4. Server-Sent Events (notifications stream)

`GET /api/v1/notifications/stream` is a long-lived SSE connection.

- **Railway:** fine — no proxy buffering by default on a Railway-issued or
  custom domain routed directly through Railway's edge.
- **Cloudflare:** if `api-dev.dasigconnect.com` is proxied (orange cloud),
  Cloudflare buffers the response and drops the connection at ~100 s. Either set
  that subdomain to **DNS-only (grey cloud)**, or add a Configuration Rule that
  disables buffering for `/api/v1/notifications/stream`.

---

## 5. Post-deploy smoke test

1. `GET https://api-dev.dasigconnect.com/health` → `{"status":"ok"}`
2. Railway deploy logs: Flyway `migrate()` finished, no `FlywayValidateException`, Tomcat bound to `$PORT`.
3. Log in at `https://dev.dasigconnect.com` — confirm the request hits `api-dev` with no CORS error.
4. Notifications bell connects (SSE `connected` event in the network tab).
5. Trigger an invite → Resend dashboard shows the send; link points at `dev.dasigconnect.com`.
6. Open a media library screen — images load through the backend media proxy at `BACKEND_PUBLIC_BASE_URL`.
7. Semantic media search returns results (exercises pgvector + Voyage).

---

## 6. Recommended before this deploy carries real traffic

- Merge `perf/reduce-db-egress` (local dev DB + lighter polling) into `dev`.
- Push media list filtering/pagination into SQL (`MediaAssetService.list()` /
  `semanticSearch()` still fetch the whole scoped table and paginate in memory).
