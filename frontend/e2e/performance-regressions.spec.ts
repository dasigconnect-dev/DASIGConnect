import { expect, test } from "@playwright/test";
import {
  installFragmentableSse, installSession, institution, mockApi, navigateInApp,
  submission, type AppRole,
} from "./fixtures";

test.describe("data loading and cache regressions", () => {
  for (const role of ["admin", "moderator", "contributor"] satisfies AppRole[]) {
    test(`${role} can complete a cold protected-route bootstrap`, async ({ page }) => {
      await installSession(page, role);
      await installFragmentableSse(page);
      await mockApi(page, role);
      await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
      await expect(page).toHaveURL(/\/dashboard$/);
      await expect(page.locator("body")).not.toContainText("Something went wrong");
      await expect(page.locator("#main-content")).toBeVisible();
    });
  }

  test("warm route uses cache and stale route refetches without clearing content", async ({ page }) => {
    await installSession(page, "moderator");
    await installFragmentableSse(page);
    let queueVersion = 0;
    await mockApi(page, "moderator", {
      "/api/v1/validation/queue": async ({ search }) => {
        if (search.get("history") === "true") return [];
        queueVersion += 1;
        if (queueVersion > 1) await new Promise((resolve) => setTimeout(resolve, 500));
        return [{ ...submission, eventTitle: `Cached Queue Item ${queueVersion}` }];
      },
    });
    await page.goto("/validation/queue", { waitUntil: "domcontentloaded" });
    await expect(page.getByText("Cached Queue Item 1")).toBeVisible();
    await navigateInApp(page, "/notifications");
    await expect(page.getByRole("heading", { name: "Validation Inbox" })).toBeVisible();
    await navigateInApp(page, "/validation/queue");
    await expect(page.getByText("Cached Queue Item 1")).toBeVisible();
    expect(queueVersion).toBe(1);
    await page.waitForTimeout(5_100);
    await navigateInApp(page, "/notifications");
    await expect(page.getByRole("heading", { name: "Validation Inbox" })).toBeVisible();
    await navigateInApp(page, "/validation/queue");
    await expect(page.getByText("Cached Queue Item 1")).toBeVisible();
    await expect(page.getByText("Cached Queue Item 2")).toBeVisible();
    expect(queueVersion).toBe(2);
  });

  test("deep-linked media detail loads once and remains warm", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    const assetId = "33333333-3333-4333-8333-000000000001";
    const api = await mockApi(page, "contributor", {
      [`/api/v1/media-assets/${assetId}`]: {
        id: assetId, assetCode: "ASSET-1", title: "Deep Link Asset", storageUrl: "",
        fileName: "deep-link.jpg", fileType: "jpeg", fileSizeBytes: 1024,
        createdAt: "2026-09-15T00:00:00Z", institutionId: institution.id,
        institutionName: institution.name, usedIn: [], tags: [],
      },
    });
    await page.goto(`/media-repository?asset=${assetId}`, { waitUntil: "domcontentloaded" });
    await expect(page.getByText("Deep Link Asset")).toBeVisible();
    await navigateInApp(page, "/dashboard");
    await navigateInApp(page, `/media-repository?asset=${assetId}`);
    await expect(page.getByText("Deep Link Asset")).toBeVisible();
    expect(api.count("GET", `/api/v1/media-assets/${assetId}`)).toBe(1);
  });

  test("institution registry is usable while secondary counts are slow", async ({ page }) => {
    await installSession(page, "admin");
    await installFragmentableSse(page);
    let summaryResolved = false;
    await mockApi(page, "admin", {
      "/api/v1/institutions/summary-counts": async () => {
        await new Promise((resolve) => setTimeout(resolve, 900));
        summaryResolved = true;
        return [{ institutionId: institution.id, contributors: 3, moderators: 1, pendingInvitations: 2 }];
      },
    });
    await page.goto("/admin/institution-management", { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("heading", { name: institution.name })).toBeVisible({ timeout: 700 });
    await expect.poll(() => summaryResolved).toBe(true);
    await expect(page.getByRole("heading", { name: institution.name })).toBeVisible();
  });

  test("media repository loads every backend page without duplicate assets", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    const api = await mockApi(page, "contributor");
    await page.goto("/media-repository?album=66666666-6666-4666-8666-666666666666", { waitUntil: "domcontentloaded" });
    await expect(page.getByText("Regression Asset 1", { exact: true })).toBeVisible();
    await expect(page.locator(".med-card")).toHaveCount(25);
    await page.locator('.med-section div[aria-hidden="true"][style*="height: 1px"]').scrollIntoViewIfNeeded();
    await expect(page.getByText("Regression Asset 26", { exact: true })).toBeVisible();
    await expect(page.locator(".med-card")).toHaveCount(26);
    expect(api.count("GET", "/api/v1/media-assets")).toBe(2);
  });

  test("fragmented SSE event is parsed once and notification mutation rolls back", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    const notification = {
      id: "44444444-4444-4444-8444-444444444444", eventType: "submission_needs_revision",
      message: "Fragmented regression notification", deepLink: "/submissions", readAt: null,
      createdAt: "2026-09-15T02:00:00Z",
    };
    let historyRequests = 0;
    await mockApi(page, "contributor", {
      "/api/v1/notifications/history": ({ search }) => {
        historyRequests += 1;
        const includeNotification = historyRequests > 1;
        return {
          items: includeNotification ? [notification] : [], totalCount: includeNotification ? 1 : 0,
          page: Number(search.get("page") ?? 0), pageSize: 50,
        };
      },
      "PATCH /api/v1/notifications/read-all": async ({ route }) => {
        await route.fulfill({ status: 503, contentType: "application/json", body: "{}" });
      },
    });
    await page.goto("/notifications", { waitUntil: "domcontentloaded" });
    await expect(page.getByText("No notifications yet")).toBeVisible();
    await page.waitForFunction(() => typeof window.__ssePush === "function");
    const payload = `event: notification\ndata: ${JSON.stringify(notification)}\n\n`;
    await page.evaluate(([first, second]) => {
      window.__ssePush?.(first);
      window.__ssePush?.(second);
    }, [payload.slice(0, 31), payload.slice(31)]);
    await expect(page.getByText(notification.message)).toHaveCount(1);
    await page.getByTitle("Mark all notifications as read").click();
    await expect(page.getByTitle("Unread")).toBeVisible();
    await expect(page.getByText("Could not mark all notifications as read.")).toBeVisible();
  });

  test("offline route recovers after connectivity returns", async ({ page, context }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    let historyVersion = 0;
    const api = await mockApi(page, "contributor", {
      "/api/v1/notifications/history": ({ search }) => {
        historyVersion += 1;
        return {
          items: historyVersion > 1 ? [{
            id: "55555555-5555-4555-8555-555555555555", eventType: "submission_pending",
            message: "Recovered after reconnect", deepLink: "/submissions", readAt: null,
            createdAt: "2026-09-15T02:00:00Z",
          }] : [],
          totalCount: historyVersion > 1 ? 1 : 0, page: Number(search.get("page") ?? 0), pageSize: 50,
        };
      },
    });
    await page.goto("/notifications", { waitUntil: "domcontentloaded" });
    await expect(page.getByText("No notifications yet")).toBeVisible();
    await context.setOffline(true);
    await page.getByTitle("Refresh").click();
    await expect(page.getByText("No notifications yet")).toBeVisible();
    await context.setOffline(false);
    await page.evaluate(() => dispatchEvent(new Event("online")));
    await expect(page.getByText("Recovered after reconnect")).toBeVisible();
    expect(api.count("GET", "/api/v1/notifications/history")).toBeGreaterThan(1);
  });

  test("review and settings share the warm watermark configuration", async ({ page }) => {
    await installSession(page, "admin");
    await installFragmentableSse(page);
    const api = await mockApi(page, "admin");
    await page.goto("/validation/queue", { waitUntil: "domcontentloaded" });
    await page.getByText(submission.eventTitle).click();
    await expect.poll(() => api.count("GET", "/api/v1/settings/watermark")).toBe(1);
    await navigateInApp(page, "/settings#page");
    await expect(page.getByText("Page Settings", { exact: true }).first()).toBeVisible();
    expect(api.count("GET", "/api/v1/settings/watermark")).toBe(1);
  });

  test("a superseded account bootstrap cannot populate the next account", async ({ page }) => {
    await installSession(page, "contributor", "account-a");
    await installFragmentableSse(page);
    let releaseFirst: (() => void) | undefined;
    await mockApi(page, "contributor", {
      "/api/v1/me": async ({ count, route }) => {
        if (count === 1) await new Promise<void>((resolve) => { releaseFirst = resolve; });
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({
          id: "test-account-b", email: "account-b@example.invalid", firstName: "Account", lastName: "B",
          displayName: "Account B", role: "CONTRIBUTOR", accountState: "ACTIVE", adminOwner: false,
          institutionId: institution.id, institutionName: institution.name, createdAt: "2026-09-01T00:00:00Z",
          notifyInApp: true, notifyEmail: false, hasAvatar: false, avatarUpdatedAt: null,
        }) });
      },
    });
    const firstNavigation = page.goto("/dashboard", { waitUntil: "domcontentloaded" }).catch(() => null);
    await page.waitForTimeout(100);
    const nextUser = {
      id: "test-account-b", email: "account-b@example.invalid", pw: "", role: "contributor",
      name: "Account B", inst: institution.name, institutionId: institution.id, initials: "AB",
    };
    await page.evaluate((user) => localStorage.setItem("dasigconnect_user", JSON.stringify(user)), nextUser);
    releaseFirst?.();
    await page.reload();
    await firstNavigation;
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("dasigconnect_user") ?? "{}").id)).toBe("test-account-b");
    await expect(page.locator("body")).not.toContainText("account-a@example.invalid");
  });

  test("session timeout is classified by telemetry", async ({ page }) => {
    test.slow();
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    await mockApi(page, "contributor", { "/api/v1/me": async () => new Promise(() => undefined) });
    await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
    await expect.poll(() => page.evaluate(() => window.__performanceEvents.some(
      (event) => event.metric === "api-request" && event.endpoint === "/me" && event.outcome === "timeout",
    )), { timeout: 15_000 }).toBe(true);
    await expect(page).toHaveURL(/\/login$/);
  });
});
