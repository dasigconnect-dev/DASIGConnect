import { expect, test } from "@playwright/test";
import { installFragmentableSse, installSession, institution, mockApi } from "./fixtures";

interface TelemetryCall {
  kind: "start" | "complete";
  body: Record<string, unknown>;
}

const FAILED_SUBMISSION_ID = "77777777-7777-4777-8777-777777777777";

function captureTelemetry(calls: TelemetryCall[]) {
  return {
    "POST /api/v1/telemetry/tasks/start": ({ route }: { route: import("@playwright/test").Route }) => {
      calls.push({ kind: "start", body: route.request().postDataJSON() as Record<string, unknown> });
      return { id: "t-1", status: "STARTED" };
    },
    "POST /api/v1/telemetry/tasks/complete": ({ route }: { route: import("@playwright/test").Route }) => {
      calls.push({ kind: "complete", body: route.request().postDataJSON() as Record<string, unknown> });
      return { id: "t-1", status: "COMPLETED" };
    },
  };
}

test.describe("Validation telemetry", () => {
  test("dashboard next-action CTA logs start and complete", async ({ page }) => {
    const calls: TelemetryCall[] = [];
    await installSession(page, "admin");
    await installFragmentableSse(page);
    await mockApi(page, "admin", captureTelemetry(calls));

    await page.goto("/dashboard");
    const cta = page.locator(".first-login-notice-cta");
    await expect(cta).toBeVisible();
    await cta.click();

    await expect.poll(() =>
      calls.some((c) => c.kind === "start" && c.body.taskName === "ADMIN_DASHBOARD_NEXT_ACTION"),
    ).toBe(true);
    await expect.poll(() =>
      calls.some((c) => c.kind === "complete" && c.body.taskName === "ADMIN_DASHBOARD_NEXT_ACTION"),
    ).toBe(true);
  });

  test("view-only screens log start on mount and complete after dwell", async ({ page }) => {
    const calls: TelemetryCall[] = [];
    await installSession(page, "admin");
    await installFragmentableSse(page);
    await mockApi(page, "admin", {
      ...captureTelemetry(calls),
      "/api/v1/analytics/summary": {
        range: "30d",
        periodStart: "2026-10-25",
        periodEnd: "2026-11-24",
        lastUpdated: "2026-11-24T00:00:00Z",
        scopeRole: "admin",
        adminView: true,
        selectedInstitutionIds: [],
        institutionFilterOptions: [],
        averagePostingDelay: { id: "delay", label: "Avg Delay", value: 0, unit: "days", sampleSize: 0, target: null, targetMet: true, deltaPercent: null, sparkline: [], secondaryLabel: null, secondaryValue: null },
        contentCompleteness: { id: "comp", label: "Completeness", value: 100, unit: "%", sampleSize: 0, target: null, targetMet: true, deltaPercent: null, sparkline: [], secondaryLabel: null, secondaryValue: null },
        totalPostsPublished: { id: "pub", label: "Published", value: 0, unit: "posts", sampleSize: 0, target: null, targetMet: true, deltaPercent: null, sparkline: [], secondaryLabel: null, secondaryValue: null },
        postsByInstitution: [],
        contributorBreakdown: [],
        statusBreakdown: [],
        contentIssues: [],
        contributorAnalytics: null,
        validatorAnalytics: null,
        aiPerformance: null,
        adminAnalytics: null,
        operationalHealth: null,
        facebookEngagement: { pageId: null, totalReach: 0, totalEngagements: 0, totalReactions: 0, totalComments: 0, totalShares: 0, totalViews: 0, postsWithMetrics: 0, topPosts: [], lastFetchedAt: null },
        pagePerformance: null,
      },
      "/api/v1/system-health/summary": {
        overallStatus: "HEALTHY",
        generatedAt: "2026-11-24T00:00:00Z",
        checks: [],
        jobs: [],
        tokens: [],
      },
      "/api/v1/system-health/ai-usage": { providers: [], daily: [] },
      "/api/v1/system-health/media-ai-telemetry": { totalQueries: 0, operations: [] },
      "/api/v1/system-health/embedding-coverage": { totalAssets: 0, embeddedAssets: 0, coveragePercent: 100 },
      "/api/v1/audit-logs": { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      "/api/v1/audit-logs/metadata": { categories: [], entityTypes: [] },
    });

    await page.goto("/analytics");
    await expect.poll(() =>
      calls.some((c) => c.kind === "start" && c.body.taskName === "ADMIN_VIEW_ANALYTICS"),
    ).toBe(true);

    // Dwell > MIN_PAGE_VIEW_MS (1000ms) before navigating away
    await page.waitForTimeout(1250);
    await page.getByRole("button", { name: "Audit Log" }).click();

    await expect.poll(() =>
      calls.some((c) => c.kind === "complete" && c.body.taskName === "ADMIN_VIEW_ANALYTICS"),
    ).toBe(true);
    await expect.poll(() =>
      calls.some((c) => c.kind === "start" && c.body.taskName === "ADMIN_VIEW_AUDIT_LOG"),
    ).toBe(true);

    await page.waitForTimeout(1250);
    await page.getByRole("button", { name: "Calendar" }).click();

    await expect.poll(() =>
      calls.some((c) => c.kind === "complete" && c.body.taskName === "ADMIN_VIEW_AUDIT_LOG"),
    ).toBe(true);
    await expect.poll(() =>
      calls.some((c) => c.kind === "start" && c.body.taskName === "ADMIN_VIEW_CALENDAR"),
    ).toBe(true);
  });

  test("failed-publication retry logs start and complete with submissionId", async ({ page }) => {
    const calls: TelemetryCall[] = [];
    await installSession(page, "admin");
    await installFragmentableSse(page);
    await mockApi(page, "admin", {
      ...captureTelemetry(calls),
      "/api/v1/resolution/failures/page": {
        items: [
          {
            submissionId: FAILED_SUBMISSION_ID,
            eventTitle: "Seeded Failed Post",
            institutionId: institution.id,
            institutionName: institution.name,
            contributorEmail: "contributor@example.invalid",
            scheduledAt: "2026-11-23T08:00:00Z",
            status: "publish_failed",
            retryCount: 3,
            lastError: "Graph API error",
            manualPublishInProgress: false,
            fastTrack: true,
          },
        ],
        page: 0,
        pageSize: 20,
        totalCount: 1,
        totalPages: 1,
        hasNext: false,
        failureCount: 1,
      },
      [`/api/v1/submissions/${FAILED_SUBMISSION_ID}`]: {
        id: FAILED_SUBMISSION_ID,
        institutionId: institution.id,
        institutionName: institution.name,
        contributorEmail: "contributor@example.invalid",
        eventTitle: "Seeded Failed Post",
        eventDate: "2026-11-23",
        caption: "Caption",
        description: "Desc",
        status: "publish_failed",
        fastTrack: true,
        submittedAt: "2026-11-23T07:00:00Z",
        createdAt: "2026-11-23T07:00:00Z",
        updatedAt: "2026-11-23T08:00:00Z",
        mediaCount: 0,
        mediaAssets: [],
        tags: [],
      },
      [`POST /api/v1/resolution/${FAILED_SUBMISSION_ID}/retry`]: {},
    });

    await page.goto(`/queue?tab=failed&submissionId=${FAILED_SUBMISSION_ID}`);
    const retryBtn = page.getByRole("button", { name: /^Retry$/i }).first();
    await expect(retryBtn).toBeVisible();
    await retryBtn.click();
    const confirmBtn = page.getByRole("button", { name: /Confirm Retry \(Live\)/i });
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();

    await expect.poll(() =>
      calls.some(
        (c) =>
          c.kind === "start" &&
          c.body.taskName === "ADMIN_RECOVER_FAILED_PUBLICATION" &&
          c.body.targetEntityId === FAILED_SUBMISSION_ID,
      ),
    ).toBe(true);
    await expect.poll(() =>
      calls.some(
        (c) =>
          c.kind === "complete" &&
          c.body.taskName === "ADMIN_RECOVER_FAILED_PUBLICATION" &&
          c.body.targetEntityId === FAILED_SUBMISSION_ID &&
          c.body.firstAttemptSuccess === true,
      ),
    ).toBe(true);
  });
});
