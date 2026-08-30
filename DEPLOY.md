# Deploying the dev environment

- **Frontend:** Vercel → `https://dev.dasigconnect.com`
- **Backend:** Render (Docker, `backend/render.yaml`) → `https://api-dev.dasigconnect.com`
- **Database:** Supabase Postgres (Session Pooler)
- **Media:** Cloudflare R2

Deploy the `dev` branch.

---

## 1. Backend — Render

### Service
- Blueprint: `backend/render.yaml`, root directory `backend/`, Docker runtime.
- **Plan: Starter or higher.** The free plan sleeps when idle, which stops the
  every-minute publishing scheduler and the token/timeout jobs.
- Health check path: `/health` (already set in the blueprint; open, no auth).
- Custom domain `api-dev.dasigconnect.com` → add in Render dashboard → Settings → Custom Domains.

### Environment variables (Render dashboard → Environment)
Everything marked `sync: false` in `render.yaml` must be set here. Non-secret
values (`APP_FRONTEND_BASE_URL`, `FACEBOOK_OAUTH_REDIRECT_URI`, `FACEBOOK_API_VERSION`,
`MAIL_FROM_NAME`, `R2_BUCKET`, `APP_GUARDRAILS_ENFORCED`) are already in the file.

| Key | Value |
|---|---|
| `DASIG_DATABASE_URL` | `jdbc:postgresql://<supabase-pooler-host>:5432/postgres` |
| `DASIG_DATABASE_USER` | `postgres.<project-ref>` |
| `DASIG_DATABASE_PASSWORD` | Supabase DB password |
| `JWT_SECRET` | 64-hex random (`openssl rand -hex 64`) — **not** the local one |
| `RESEND_API_KEY` | `re_…` |
| `MAIL_FROM` | `no-reply@dasigconnect.com` (domain verified in Resend) |
| `MAIL_REPLY_TO` | a monitored inbox |
| `FACEBOOK_PAGE_ACCESS_TOKEN` | long-lived page token |
| `FACEBOOK_PAGE_ID` / `FACEBOOK_APP_ID` / `FACEBOOK_APP_SECRET` | from the Meta app |
| `ANTHROPIC_API_KEY` / `VOYAGE_API_KEY` | provider keys |
| `R2_ACCOUNT_ID` / `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_PUBLIC_BASE_URL` | from Cloudflare R2 |
| `DASIG_SUPABASE_SERVICE_ROLE_KEY` | optional; leave unset unless Claude Vision 401s on images |

### One-time external config
- **Meta app → Facebook Login → Settings → Valid OAuth Redirect URIs:** add
  `https://api-dev.dasigconnect.com/api/v1/facebook/oauth-callback`.
- **Meta app → App Domains / Site URL:** add `dasigconnect.com`.
- **Resend → Domains:** `dasigconnect.com` verified (SPF + DKIM DNS records).
- **Supabase:** confirm `flyway_schema_history` max version matches the highest
  `backend/src/main/resources/db/migration/V*.sql` before first deploy. The app
  runs `flyway.repair()` then `migrate()` on boot; a checksum mismatch fails startup.

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
