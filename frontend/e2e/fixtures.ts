import type { Page, Route } from "@playwright/test";

export type AppRole = "admin" | "moderator" | "contributor";
export interface ApiRequest { method: string; path: string; search: URLSearchParams; count: number; route: Route }
export type ApiOverride = (request: ApiRequest) => Promise<unknown | void> | unknown | void;

export const institution = {
  id: "11111111-1111-4111-8111-111111111111", institutionCode: "CITU",
  name: "Cebu Institute of Technology - University", emailDomain: "cit.edu",
  status: "active", createdAt: "2026-09-01T00:00:00Z", hasLogo: false, isProtected: false,
};

export const submission = {
  id: "22222222-2222-4222-8222-222222222222", institutionId: institution.id,
  institutionName: institution.name, contributorEmail: "contributor@example.invalid",
  eventTitle: "Performance Regression Event", eventDate: "2026-09-20",
  caption: "Regression fixture caption", description: "Regression fixture description",
  status: "pending", submittedAt: "2026-09-15T01:00:00Z", createdAt: "2026-09-15T00:00:00Z",
  updatedAt: "2026-09-15T01:00:00Z", mediaCount: 0, mediaAssets: [], tags: [],
};

export function userFor(role: AppRole, suffix = role) {
  const contributor = role === "contributor";
  return {
    id: `test-${suffix}`, email: `${suffix}@example.invalid`, pw: "", role,
    name: `Test ${role}`, inst: contributor ? institution.name : "Network",
    institutionId: contributor ? institution.id : null, initials: "TU",
  };
}

function profileFor(role: AppRole, suffix = role) {
  const user = userFor(role, suffix);
  return {
    id: user.id, email: user.email, firstName: "Test", lastName: role, displayName: user.name,
    role: role.toUpperCase(), accountState: "ACTIVE", adminOwner: role === "admin",
    institutionId: user.institutionId, institutionName: user.institutionId ? institution.name : null,
    createdAt: "2026-09-01T00:00:00Z", notifyInApp: true, notifyEmail: false,
    hasAvatar: false, avatarUpdatedAt: null,
  };
}

export async function installSession(page: Page, role: AppRole, suffix = role) {
  const user = userFor(role, suffix);
  const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 86_400 })).toString("base64url");
  await page.addInitScript(({ token, currentUser }) => {
    if (!localStorage.getItem("dasigconnect_token")) localStorage.setItem("dasigconnect_token", token);
    if (!localStorage.getItem("dasigconnect_user")) localStorage.setItem("dasigconnect_user", JSON.stringify(currentUser));
    const events: unknown[] = [];
    Object.defineProperty(window, "__performanceEvents", { value: events, configurable: true });
    window.addEventListener("dasigconnect:performance", (event) => events.push((event as CustomEvent).detail));
  }, { token: `e30.${payload}.test`, currentUser: user });
}

export async function installFragmentableSse(page: Page) {
  await page.addInitScript(() => {
    const originalFetch = window.fetch.bind(window);
    window.fetch = (input, init) => {
      if (!String(input).includes("/notifications/stream")) return originalFetch(input, init);
      return Promise.resolve(new Response(new ReadableStream({
        start(controller) {
          Object.defineProperty(window, "__ssePush", {
            configurable: true,
            value: (chunk: string) => controller.enqueue(new TextEncoder().encode(chunk)),
          });
          init?.signal?.addEventListener("abort", () => {
            try { controller.error(new DOMException("Aborted", "AbortError")); } catch { /* stream closed */ }
          });
        },
      }), { status: 200, headers: { "Content-Type": "text/event-stream" } }));
    };
  });
}

function mediaPage(page: number) {
  const start = (page - 1) * 25;
  const count = page === 1 ? 25 : page === 2 ? 1 : 0;
  return {
    items: Array.from({ length: count }, (_, index) => {
      const number = start + index + 1;
      return {
        id: `33333333-3333-4333-8333-${String(number).padStart(12, "0")}`,
        assetCode: `ASSET-${number}`, title: `Regression Asset ${number}`, storageUrl: "",
        fileName: `asset-${number}.jpg`, fileType: "jpeg", fileSizeBytes: 1024,
        createdAt: "2026-09-15T00:00:00Z", institutionId: institution.id, institutionName: institution.name,
      };
    }),
    totalCount: 26, page, pageSize: 25,
  };
}

function defaultBody(path: string, search: URLSearchParams, role: AppRole) {
  if (path.endsWith("/me")) return profileFor(role);
  if (path.endsWith("/notifications/unread-count")) return { unreadCount: 1 };
  if (path.endsWith("/notifications/history")) return { items: [], totalCount: 0, page: Number(search.get("page") ?? 0), pageSize: 50 };
  if (path.endsWith("/institutions/summary-counts")) return [{ institutionId: institution.id, contributors: 3, moderators: 1, pendingInvitations: 2 }];
  if (path.endsWith("/institutions")) return [institution];
  if (path.endsWith("/analytics/summary")) return {
    totalPostsPublished: { value: 0 }, statusBreakdown: [], operationalHealth: null,
  };
  if (path.endsWith("/validation/dashboard-summary")) return {
    awaitingReview: 1, approvedThisMonth: 0, rejectedThisMonth: 0, contributorCount: 1,
  };
  if (path.endsWith("/validation/queue/page")) return {
    items: [submission], page: Number(search.get("page") ?? 0),
    pageSize: Number(search.get("pageSize") ?? 20), totalCount: 1, totalPages: 1,
    hasNext: false,
    counts: { all: 1, pending: 1, in_review: 0, needs_revision: 0, scheduled: 0, published: 0, rejected: 0 },
  };
  if (path.endsWith("/resolution/failures/page")) return {
    items: [], page: Number(search.get("page") ?? 0),
    pageSize: Number(search.get("pageSize") ?? 20), totalCount: 0,
    totalPages: 0, hasNext: false, failureCount: 0,
  };
  if (path.endsWith(`/submissions/${submission.id}`)) return submission;
  if (path.endsWith("/submissions/lookups")) return {
    allowedFileTypes: ["jpeg"], allowedImageTypes: ["jpeg"], allowedVideoTypes: [], maxFileSizeMb: 10,
    maxMediaAssetsPerSubmission: 10, maxTitleLength: 255, minScheduleLeadTimeHours: 1,
    maxScheduleDaysAhead: 30, categories: [], availableTags: [], guardrailsEnforced: true,
  };
  if (path.endsWith("/submissions/page")) return {
    items: [submission], page: Number(search.get("page") ?? 0),
    pageSize: Number(search.get("pageSize") ?? 20), totalCount: 1, totalPages: 1,
    hasNext: false,
    counts: {
      all: 1, drafts: 0, "action-needed": 0, rejected: 0, submitted: 1,
      "under-review": 1, scheduled: 0, published: 0, failed: 0,
    },
  };
  if (path.endsWith("/media-assets/albums")) return [{
    id: "66666666-6666-4666-8666-666666666666", institutionId: institution.id,
    institutionCode: institution.institutionCode, institutionName: institution.name,
    parentAlbumId: null, name: "Regression Album", childAlbumCount: 0, assetCount: 26,
    canDelete: true, shared: false, createdAt: "2026-09-15T00:00:00Z", updatedAt: "2026-09-15T00:00:00Z",
  }];
  if (path.endsWith("/media-assets")) return mediaPage(Number(search.get("page") ?? 1));
  if (path.endsWith("/settings/watermark")) return { enabled: true, elements: [] };
  if (path.endsWith("/settings/page")) return { facebookPageId: null, guardrailsEnforced: true, updatedAt: null };
  if (path.endsWith("/messenger/connection")) return { connected: false };
  if (path.endsWith("/users/counts")) return { contributors: 0, moderators: 0 };
  if (path.endsWith("/pending/count")) return { pendingInvitations: 0 };
  if (path.includes("/validation/") && path.endsWith("/lock")) return null;
  if (path.includes("/validation/") && path.endsWith("/log")) return [];
  return [];
}

export async function mockApi(page: Page, role: AppRole, overrides: Record<string, ApiOverride | unknown> = {}) {
  const counts = new Map<string, number>();
  await page.route("https://cdn.jsdelivr.net/**", (route) =>
    route.fulfill({ status: 200, contentType: "text/css", body: "" }),
  );
  await page.route("**/api/v1/**", async (route) => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    const key = `${method} ${url.pathname}`;
    const count = (counts.get(key) ?? 0) + 1;
    counts.set(key, count);
    const override = overrides[key] ?? overrides[url.pathname];
    const value = typeof override === "function"
      ? await (override as ApiOverride)({ method, path: url.pathname, search: url.searchParams, count, route })
      : override;
    if (typeof override === "function" && value === undefined) return;
    await route.fulfill({
      status: 200, contentType: "application/json",
      body: JSON.stringify(override === undefined ? defaultBody(url.pathname, url.searchParams, role) : value),
    });
  });
  return { count: (method: string, path: string) => counts.get(`${method} ${path}`) ?? 0 };
}

export async function navigateInApp(page: Page, path: string) {
  await page.evaluate((target) => {
    history.pushState({}, "", target);
    dispatchEvent(new PopStateEvent("popstate"));
  }, path);
}

declare global {
  interface Window {
    __performanceEvents: Array<Record<string, unknown>>;
    __ssePush?: (chunk: string) => void;
  }
}
