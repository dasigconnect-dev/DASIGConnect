# Deploying the dev environment

- **Frontend:** Vercel → `https://dev.dasigconnect.com`
- **Backend:** Render (Docker, `backend/render.yaml`) → `https://api-dev.dasigconnect.com`
- **Database:** Supabase Postgres (Session Pooler)
- **Media:** Cloudflare R2

Deploy the `dev` branch.

---

## 1. Backend — Render

### Service
- Blueprint: `backend/render.yaml` — `rootDir: backend`, Docker runtime, `plan: free`.
- Health check path: `/health` (open, no auth).
- Custom domain `api-dev.dasigconnect.com` → Render dashboard → Settings → Custom Domains.

### Free-tier reality
- The service **spins down after ~15 min idle**. While asleep, no `@Scheduled`
  job runs — a Facebook post scheduled for 2:00 PM does not publish until the
  service is awake; `StaleSubmissionDetectorJob` then publishes the missed
  window on its next run. Net effect: posts are **late (~10–15 min)**, not lost.
- **Keep it warm:** point an uptime monitor (UptimeRobot / cron-job.org, free)
  at `https://api-dev.dasigconnect.com/health` every ~10 min. `.github/workflows/keepalive.yml`
  is a backup pinger — enable Actions on the repo for it to run.
- 512 MB RAM → `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70 -XX:+UseSerialGC` (in the blueprint).
- 750 instance-hours/month. A warm service uses ~730 h, so this must be your
  **only** Render service — a second free service tips you over and both suspend.
- Cold start after idle is 30–60 s. Ping it before any scheduled demo.

### Environment variables (Render dashboard → Environment)
Set everything marked `sync: false` in `render.yaml`. Start with the minimum and
add Facebook / AI only when you actually demo those features.

**Set now:**

| Key | Value |
|---|---|
| `DASIG_DATABASE_URL` | `jdbc:postgresql://<supabase-pooler-host>:5432/postgres` |
| `DASIG_DATABASE_USER` | `postgres.<project-ref>` |
| `DASIG_DATABASE_PASSWORD` | Supabase DB password |
| `JWT_SECRET` | fresh 64-hex (`openssl rand -hex 64`) — **not** the local/leaked one |
| `RESEND_API_KEY` | `re_…` |
| `MAIL_FROM` | address on a Resend-verified domain |
| `MAIL_REPLY_TO` | a monitored inbox |
| `R2_ACCOUNT_ID` / `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_PUBLIC_BASE_URL` | from Cloudflare R2 |

**Leave unset until needed:**

| Key | Why wait |
|---|---|
| `FACEBOOK_PAGE_ACCESS_TOKEN` / `FACEBOOK_PAGE_ID` / `FACEBOOK_APP_ID` / `FACEBOOK_APP_SECRET` | Publishing self-disables while blank. When you do set it, use a **test** Facebook page — a dev submission that reaches `published` posts publicly. |
| `FACEBOOK_API_VERSION` (`v25.0`) / `FACEBOOK_OAUTH_REDIRECT_URI` | Only meaningful once the tokens above are set. |
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

- **Render:** fine — the stream sends its first event immediately.
- **Cloudflare:** if `api-dev.dasigconnect.com` is proxied (orange cloud),
  Cloudflare buffers the response and drops the connection at ~100 s. Either set
  that subdomain to **DNS-only (grey cloud)**, or add a Configuration Rule that
  disables buffering for `/api/v1/notifications/stream`.

---

## 5. Post-deploy smoke test

1. `GET https://api-dev.dasigconnect.com/health` → `{"status":"ok"}`
2. Render logs: Flyway `migrate()` finished, no `FlywayValidateException`, Tomcat on the `$PORT`.
3. Log in at `https://dev.dasigconnect.com` — confirm the request hits `api-dev` with no CORS error.
4. Notifications bell connects (SSE `connected` event in the network tab).
5. Trigger an invite → Resend dashboard shows the send; link points at `dev.dasigconnect.com`.
6. Open a media library screen — images load from the R2 public URL.
7. Semantic media search returns results (exercises pgvector + Voyage).

---

## 6. Recommended before this deploy carries real traffic

- Merge `perf/reduce-db-egress` (local dev DB + lighter polling) into `dev`.
- Push media list filtering/pagination into SQL (`MediaAssetService.list()` /
  `semanticSearch()` still fetch the whole scoped table and paginate in memory).
