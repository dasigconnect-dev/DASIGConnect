---
name: remove-hardcoded
description: >
  Audit a codebase or component and remove all hardcoded, static, and mock data — replacing
  everything with live data driven by API connections, environment variables, or dynamic state.
  Use this skill whenever the user says: "remove the hardcoded data", "replace static values
  with real data", "stop using dummy data", "make the data dynamic", "the data should come from
  the API not the code", "remove the mock/fake/placeholder data", "wire up the real data",
  "it's using static arrays instead of the backend", "clean up the hardcoded stuff", or any
  time you find the codebase contains inline arrays, objects, string literals, or mock files
  being used where live backend data should be. Also trigger when the user shows a component
  that renders fake names, placeholder numbers, hardcoded lists, or static config values that
  clearly belong in environment variables or a database. This skill covers full audit,
  systematic replacement, environment variable extraction, API hook substitution, and a
  final verification pass to ensure zero hardcoded data remains in the UI layer.
---

# Remove Hardcoded Data Skill

Your job is to **fully purge all static, hardcoded, and mocked data** from the codebase and
replace every single instance with dynamic data driven by API calls, environment variables, or
runtime state. When you are done, **no data in the UI should be defined in the frontend code
itself** — the backend and its connections are the sole source of truth for all data.

---

## The Core Principle

> If a value could ever change without a code deploy, it must not be hardcoded.

Any data that lives in the UI layer as a literal — an array, an object, a string, a number —
is a liability. It goes stale, it diverges from the backend, and it breaks trust. The frontend
is a **display and interaction layer only**. Data flows in from connections. The frontend owns
zero data.

---

## Phase 0 — Full Audit Before Touching Anything

Before making any change, **read every relevant file** and build a complete inventory of every
hardcoded element. Never guess. Never remove something without knowing what replaces it.

### 0.1 — What to scan

Read through all of the following file types in the project:

- All component files (`.tsx`, `.jsx`, `.vue`, `.svelte`, `.html`)
- All page files
- Any `data/`, `mock/`, `fixtures/`, `constants/`, `seed/`, `__mocks__/` directories
- Any file named `mockData`, `dummyData`, `sampleData`, `fakeData`, `testData`, `placeholder`
- Config files that contain values that should be environment-driven
- Any `const` declarations that hold arrays of objects or large string literals

### 0.2 — Hardcoded Data Taxonomy

Classify every finding into one of these categories:

| Category | Examples | Replacement |
|---|---|---|
| **Static list / array** | `const users = [{id:1, name:"Alice"}, ...]` | API fetch |
| **Hardcoded ID / slug** | `const ORG_ID = "abc123"` | Env variable or auth context |
| **Placeholder text** | `"John Doe"`, `"example@email.com"`, `"Lorem ipsum"` | Dynamic field from API |
| **Hardcoded URL** | `fetch("http://localhost:3000/users")` | `process.env` / `import.meta.env` |
| **Mocked response object** | `const user = { id: 1, role: "admin" }` | Auth/session context or API |
| **Inline config values** | `const MAX_ITEMS = 50`, `const CURRENCY = "USD"` | Env var or API config endpoint |
| **Fake counts / stats** | `<p>1,240 users</p>`, `revenue: "$9,400"` | API aggregation endpoint |
| **Static navigation items** | Hardcoded `navLinks` array with labels/routes | CMS, backend config, or role-based API |
| **Hardcoded enum labels** | `["Active", "Inactive", "Pending"]` as raw strings | API enum or lookup endpoint |
| **Feature flags** | `const SHOW_BETA = true` | Remote config / environment variable |

### 0.3 — Audit Output Format

Before writing any replacement code, produce a full inventory like this:

```
HARDCODED DATA AUDIT
====================

[FILE: src/components/UserList.tsx]
  Line 4-12  : Static users array (8 fake user objects) → REPLACE with useUsers() API hook
  Line 15    : Hardcoded string "Admin Dashboard" in title → REPLACE with org name from /api/org
  Line 34    : fetch("http://localhost:3000/users") → REPLACE with api.get("/users")

[FILE: src/constants/config.ts]
  Line 2     : const API_KEY = "sk-prod-abc123" → MOVE to .env as VITE_API_KEY
  Line 5     : const PLAN_OPTIONS = ["Free","Pro","Enterprise"] → REPLACE with /api/plans
  Line 9     : const DEFAULT_ORG_ID = "org_1234" → REPLACE with auth context orgId

[FILE: src/pages/Dashboard.tsx]
  Line 22-30 : Hardcoded stats object { revenue: 9400, users: 1240 } → REPLACE with /api/stats
  Line 55    : Static chart data array → REPLACE with /api/analytics

TOTAL: 9 instances across 3 files
```

Do not proceed to Phase 1 until this inventory is complete and confirmed.

---

## Phase 1 — Remove Static Arrays and Object Literals

This is the most common category. An inline array of objects being rendered in a component
must be fully deleted and replaced with an API call.

### Before (hardcoded)

```tsx
// ❌ src/components/ProductList.tsx
const products = [
  { id: 1, name: "Wireless Mouse", price: 29.99, stock: 14 },
  { id: 2, name: "Mechanical Keyboard", price: 89.99, stock: 3 },
  { id: 3, name: "USB-C Hub", price: 49.99, stock: 0 },
];

export function ProductList() {
  return (
    <ul>
      {products.map((p) => (
        <li key={p.id}>{p.name} — ${p.price}</li>
      ))}
    </ul>
  );
}
```

### After (dynamic)

```tsx
// ✅ src/components/ProductList.tsx
import { useProducts } from "@/hooks/useProducts";

export function ProductList() {
  const { data: products, isLoading, isError } = useProducts();

  if (isLoading) return <ProductListSkeleton />;
  if (isError) return <ErrorMessage message="Could not load products." />;

  return (
    <ul>
      {products?.map((p) => (
        <li key={p.id}>{p.name} — ${p.price}</li>
      ))}
    </ul>
  );
}
```

```typescript
// ✅ src/hooks/useProducts.ts
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { Product } from "@/types/api";

export function useProducts() {
  return useQuery({
    queryKey: ["products"],
    queryFn: ({ signal }) => api.get<Product[]>("/products", { signal }),
  });
}
```

**Rules:**
- Delete the static array entirely — do not keep it as a fallback
- Delete the import if it was in a separate `data/` file
- Delete the `data/` file itself if it's now empty
- Replace the render logic 1:1 — same structure, same keys, same fields

---

## Phase 2 — Remove Hardcoded URLs and Secrets

Every hardcoded URL, API key, token, secret, or environment-specific value must move to
environment variables. These must never appear in source code.

### Before

```typescript
// ❌ Scattered across components
const res = await fetch("https://api.myapp.com/v1/users", {
  headers: { "X-Api-Key": "sk-live-abc123xyz" }
});
```

### After

```typescript
// ✅ .env.local
VITE_API_URL=https://api.myapp.com/v1
VITE_API_KEY=sk-live-abc123xyz
```

```typescript
// ✅ src/lib/api.ts — one place, used everywhere
const BASE_URL = import.meta.env.VITE_API_URL;
const API_KEY  = import.meta.env.VITE_API_KEY;

// All components use api.get("/users") — never a raw URL
```

### Environment variable naming conventions

| Runtime | Syntax | Example |
|---|---|---|
| Vite (React) | `import.meta.env.VITE_*` | `import.meta.env.VITE_API_URL` |
| Create React App | `process.env.REACT_APP_*` | `process.env.REACT_APP_API_URL` |
| Next.js (client) | `process.env.NEXT_PUBLIC_*` | `process.env.NEXT_PUBLIC_API_URL` |
| Next.js (server) | `process.env.*` | `process.env.DATABASE_URL` |
| Node / Express | `process.env.*` | `process.env.PORT` |

**Rules:**
- Never commit `.env.local` or `.env.production` to version control
- Always commit a `.env.example` with keys but no values
- If a secret is already in git history, treat it as compromised and rotate it

---

## Phase 3 — Remove Hardcoded IDs, Slugs, and References

IDs hardcoded in the frontend are a red flag. They couple the UI to a specific database row.

### Before

```tsx
// ❌ Hardcoded org ID — breaks for every other organization
const ORG_ID = "org_a1b2c3";
const ADMIN_USER_ID = 1;
const DEFAULT_DASHBOARD = "dashboard_main";

useEffect(() => {
  api.get(`/orgs/${ORG_ID}/settings`);
}, []);
```

### After

```tsx
// ✅ IDs come from auth context / route params / API response
import { useAuth } from "@/hooks/useAuth";
import { useParams } from "react-router-dom";

function OrgSettings() {
  const { orgId } = useAuth();           // from session/token
  const { dashboardId } = useParams();   // from URL

  const { data } = useOrgSettings(orgId);
  // ...
}
```

**Where IDs should actually come from:**

| Hardcoded ID type | Dynamic replacement |
|---|---|
| Current user's org | Auth context / JWT claims |
| Current user's ID | Auth context |
| Route-specific entity | URL route params (`useParams`) |
| Default config/plan | API config endpoint or env var |
| Feature/product IDs | API lookup by slug, not ID |

---

## Phase 4 — Remove Hardcoded Display Text Backed by Data

If text in the UI represents real data — a user's name, a count, a label from the database —
it must not be a string literal in JSX.

### Before

```tsx
// ❌ Fake stats, fake name, fake count — all lies
<h1>Welcome back, John!</h1>
<p>You have 3 pending tasks</p>
<p>Total Revenue: $9,400</p>
<span className="badge">Premium Plan</span>
```

### After

```tsx
// ✅ Every value comes from the API
const { data: user } = useCurrentUser();
const { data: stats } = useDashboardStats();

<h1>Welcome back, {user?.name}!</h1>
<p>You have {stats?.pendingTasks ?? 0} pending tasks</p>
<p>Total Revenue: {formatCurrency(stats?.revenue)}</p>
<span className="badge">{user?.plan?.displayName}</span>
```

**Common offenders to scan for:**
- Greeting messages with real names
- Badge/chip labels that reflect user state
- Count displays (`3 items`, `12 members`)
- Status indicators (`Active`, `Online`)
- Price/currency displays
- "Last updated" timestamps shown as static strings

---

## Phase 5 — Remove Hardcoded Select / Dropdown Options

Dropdown options are almost always backed by a backend enum or lookup table.

### Before

```tsx
// ❌ Options defined in frontend — diverge from backend immediately
const STATUS_OPTIONS = ["Active", "Inactive", "Suspended", "Pending Review"];
const ROLE_OPTIONS   = ["admin", "editor", "viewer"];
const COUNTRY_OPTIONS = ["Philippines", "USA", "Singapore", /* 190 more... */];

<select>
  {STATUS_OPTIONS.map((s) => <option key={s}>{s}</option>)}
</select>
```

### After

```tsx
// ✅ Options fetched from API
const { data: statusOptions } = useStatusOptions();   // GET /api/lookups/statuses
const { data: roleOptions }   = useRoleOptions();     // GET /api/lookups/roles
const { data: countries }     = useCountries();       // GET /api/countries

<select>
  {statusOptions?.map((s) => (
    <option key={s.value} value={s.value}>{s.label}</option>
  ))}
</select>
```

**Exception** — a small set of options that are truly universal constants (e.g., `["asc", "desc"]`
for sort direction, or `["light", "dark"]` for theme) may remain as frontend constants. The test:
*would these ever change based on a backend rule or admin setting?* If yes, pull from API.

---

## Phase 6 — Remove Mock Files and Data Directories

After replacing all usages, delete the source files entirely.

### Files/directories to delete

```bash
# Common names for hardcoded data files — delete all of these
src/data/
src/mocks/
src/fixtures/
src/seed/
src/__mocks__/           # only if it held data mocks, not jest mocks
src/constants/mockData.ts
src/lib/dummyData.ts
src/utils/sampleData.ts
public/data/*.json       # static JSON files loaded by the frontend
```

### Deletion checklist

For each file before deleting:

- [ ] Search entire codebase for every `import` of this file
- [ ] Confirm every import site has been updated to use the API hook
- [ ] Delete the file
- [ ] Run TypeScript compiler — confirm no type errors
- [ ] Run the app — confirm nothing breaks

---

## Phase 7 — Remove Hardcoded Feature Flags and Config

### Before

```typescript
// ❌ Feature flags baked into source — require code deploy to change
const FEATURES = {
  newDashboard: true,
  betaCheckout: false,
  maintenanceMode: false,
};

const CONFIG = {
  maxUploadSizeMb: 10,
  defaultCurrency: "USD",
  supportEmail: "help@company.com",
};
```

### After (Environment variables for deploy-time config)

```typescript
// ✅ .env
VITE_MAX_UPLOAD_MB=10
VITE_DEFAULT_CURRENCY=USD
VITE_SUPPORT_EMAIL=help@company.com
```

### After (Remote config for runtime-toggleable flags)

```typescript
// ✅ Feature flags come from API — can be toggled without deploy
// GET /api/config → { features: { newDashboard: true, betaCheckout: false } }

export function useFeatureFlags() {
  return useQuery({
    queryKey: ["config", "features"],
    queryFn: () => api.get<FeatureFlags>("/config/features"),
    staleTime: 1000 * 60 * 5, // cache for 5 minutes
  });
}

// Usage
const { data: flags } = useFeatureFlags();
if (flags?.newDashboard) return <NewDashboard />;
return <OldDashboard />;
```

---

## Phase 8 — Remove Static Navigation and Route Definitions

If navigation items, sidebar links, or menu entries depend on user role or backend config,
they must not be a static array.

### Before

```typescript
// ❌ Static nav — every user sees the same items regardless of role/permissions
const NAV_ITEMS = [
  { label: "Dashboard", href: "/dashboard", icon: "grid" },
  { label: "Users",     href: "/users",     icon: "users" },
  { label: "Billing",   href: "/billing",   icon: "credit-card" },
  { label: "Admin",     href: "/admin",     icon: "shield" },
];
```

### After

```tsx
// ✅ Nav items filtered by user permissions from API
const { data: user } = useCurrentUser();

const NAV_ITEMS = [
  { label: "Dashboard", href: "/dashboard", icon: "grid",        show: true },
  { label: "Users",     href: "/users",     icon: "users",       show: user?.permissions.includes("manage_users") },
  { label: "Billing",   href: "/billing",   icon: "credit-card", show: user?.permissions.includes("manage_billing") },
  { label: "Admin",     href: "/admin",     icon: "shield",      show: user?.role === "admin" },
].filter((item) => item.show);
```

Or even better — fetch the nav structure from the backend entirely if it's fully dynamic.

---

## Phase 9 — Final Verification Pass

After all replacements, run this checklist across the **entire codebase**:

### 9.1 — Grep for common offenders

Run these searches (or their IDE equivalents) and confirm zero results remain:

```bash
# Hardcoded data patterns
grep -r "dummyData\|mockData\|fakeData\|sampleData\|testData\|placeholder" src/
grep -r "localhost:3000\|localhost:8000\|localhost:8080" src/
grep -r "http://\|https://" src/ --include="*.ts" --include="*.tsx"  # should only find api.ts

# Suspicious large literals
grep -rn "= \[{" src/        # arrays of objects — likely static data
grep -rn "const.*= {$" src/  # multiline object literals

# Obvious fake data
grep -ri "john doe\|jane doe\|lorem ipsum\|example\.com\|foo@bar\|test@test" src/
grep -ri "abc123\|dummy\|placeholder\|fake\|hardcoded" src/
```

### 9.2 — Visual scan checklist

Open the running app and check every screen:

- [ ] No names, emails, or avatars visible when the user is not logged in
- [ ] Counts and numbers change when backend data changes
- [ ] Dropdowns and selects are empty when the API hasn't responded yet (not pre-filled with static options)
- [ ] Refreshing the page fetches fresh data — no values survive a refresh from constants
- [ ] Logged-in user's actual name appears, not "John Doe" or "User"
- [ ] Stats/charts update when the backend data changes

### 9.3 — TypeScript and build verification

```bash
npx tsc --noEmit        # Zero type errors
npm run build           # Clean production build
```

### 9.4 — Delete verification

```bash
# Confirm all mock/data directories are gone
ls src/data src/mocks src/fixtures 2>&1   # Should say "No such file or directory"

# Confirm no remaining imports of deleted files
grep -r "from.*\/data\/" src/
grep -r "from.*\/mocks\/" src/
grep -r "from.*mockData\|dummyData\|fixtures" src/
```

---

## Phase 10 — What Is Allowed to Stay

Not everything inline is wrong. These are legitimate frontend-only constants:

| Allowed | Reason |
|---|---|
| UI copy (button labels, headings, error messages) | Not data — part of the UI contract |
| Route path strings (`"/dashboard"`, `"/settings"`) | Frontend routing concern |
| CSS class names and style tokens | Frontend concern |
| Regex patterns for validation | Logic, not data |
| Sort direction `["asc", "desc"]` | Universal constant, never backend-controlled |
| i18n translation keys | Frontend keys (actual translated text lives in locale files, not inline) |
| Unit/format constants (`"USD"` in a formatter if not user-configurable) | Format concern |

The test: **would this value ever need to change based on who the user is, what plan they're on, what org they belong to, or what the backend says?** If yes, it's data. Remove it.

---

## Quick Decision Tree

```
Is it a value that could differ per user, org, role, or environment?
│
├── YES → Must come from API or env variable. Remove from frontend code.
│
└── NO → Is it rendered directly in the UI as content (not structure)?
          │
          ├── YES → Is it realistic-looking fake data (names, numbers, text)?
          │          ├── YES → Remove it. It's masquerading as real data.
          │          └── NO → Likely UI copy. OK to keep.
          │
          └── NO → UI structure/logic constant. OK to keep.
```

---

## Common Mistakes to Avoid

| Mistake | Consequence | Correct approach |
|---|---|---|
| Keeping static data as a "fallback" | Fallback data masks API failures and hides bugs | Show a loading/error state instead |
| Moving hardcoded data to a config file but not the API | Data still can't change without a deploy | Move it to the backend |
| Replacing only the data but leaving the type as `any` | Type safety gone, errors are silent | Define the type from the actual API shape |
| Deleting mock data before confirming the API works | App breaks with no fallback | Verify the API endpoint first, then delete |
| Leaving hardcoded data in tests | Tests don't reflect real shapes | Use API-mirrored fixtures or MSW mocks |
