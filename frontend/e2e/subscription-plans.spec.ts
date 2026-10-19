import { expect, test } from "@playwright/test";
import { installSession, mockApi } from "./fixtures";

const freePlan = {
  id: "00000000-0000-0000-0000-000000000101",
  code: "FREE_PILOT",
  name: "Free Pilot",
  version: 1,
  monthlyPriceCentavos: 0,
  institutionLimit: 3,
  activeUserLimit: 15,
  mediaStorageBytes: 10_000_000_000,
  monthlySubmissionLimit: 50,
  monthlyAiCaptionLimit: 100,
  monthlyAiMediaSuggestionLimit: 300,
  perUserDailyCaptionLimit: 5,
  analyticsRetentionDays: 30,
  exportLevel: "NONE",
  supportLevel: "BEST_EFFORT",
  effectiveFrom: "2026-10-01T00:00:00Z",
  effectiveUntil: null,
};

const plusPlan = {
  ...freePlan,
  id: "00000000-0000-0000-0000-000000000102",
  code: "PLUS_NETWORK",
  name: "Plus Network",
  monthlyPriceCentavos: 749_900,
  institutionLimit: 15,
  activeUserLimit: 75,
  mediaStorageBytes: 75_000_000_000,
  monthlySubmissionLimit: 500,
  monthlyAiCaptionLimit: 1_500,
  monthlyAiMediaSuggestionLimit: 5_000,
  perUserDailyCaptionLimit: 20,
  analyticsRetentionDays: 365,
  exportLevel: "CSV",
  supportLevel: "STANDARD_EMAIL",
};

const premiumPlan = {
  ...plusPlan,
  id: "00000000-0000-0000-0000-000000000103",
  code: "PREMIUM_NETWORK",
  name: "Premium Network",
  monthlyPriceCentavos: 1_099_900,
  institutionLimit: 25,
  activeUserLimit: 150,
  mediaStorageBytes: 200_000_000_000,
  monthlySubmissionLimit: 2_000,
  monthlyAiCaptionLimit: 5_000,
  monthlyAiMediaSuggestionLimit: 20_000,
  perUserDailyCaptionLimit: 50,
  analyticsRetentionDays: null,
  exportLevel: "CSV_AND_PDF",
  supportLevel: "PRIORITY_EMAIL",
};

const subscriptionStatus = {
  organizationId: "00000000-0000-0000-0000-000000000001",
  organizationName: "DASIG Central Visayas",
  organizationSlug: "dasig-central-visayas",
  organizationStatus: "ACTIVE",
  subscriptionId: "00000000-0000-0000-0000-000000000201",
  subscriptionStatus: "TRIAL",
  currentPeriodStart: "2026-10-01T00:00:00Z",
  currentPeriodEnd: "2026-12-01T00:00:00Z",
  gracePeriodEndsAt: null,
  plan: freePlan,
  enforcementEnabled: true,
};

async function installSubscriptionSession(page: import("@playwright/test").Page) {
  await installSession(page, "admin");
  await mockApi(page, "admin", {
    "GET /api/v1/subscription": subscriptionStatus,
    "GET /api/v1/subscription/plans": [freePlan, plusPlan, premiumPlan],
    "POST /api/v1/subscription/admin/change-plan": { ...subscriptionStatus, plan: plusPlan },
    "POST /api/v1/subscription/billing/payment-link": {
      invoiceId: "00000000-0000-0000-0000-000000000301",
      invoiceNumber: "INV-2026-0001",
      amountCentavos: plusPlan.monthlyPriceCentavos,
      currency: "PHP",
      checkoutUrl: "https://paymongo.test/checkout/plus",
      providerReferenceNumber: "TEST-0001",
      status: "ACTIVE",
      liveMode: false,
      dueAt: "2026-10-25T00:00:00Z",
    },
  });
}

test("admin profile menu exposes upgrade, settings, and sign out actions", async ({ page }) => {
  await installSubscriptionSession(page);
  await page.goto("/dashboard");

  await page.locator("#dash-avatar").click();

  await expect(page.getByRole("button", { name: "Upgrade Plan" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Account Settings" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Sign Out" })).toBeVisible();
});

test("plan page displays backend prices, inclusions, and current plan", async ({ page }) => {
  await installSubscriptionSession(page);
  await page.goto("/upgrade-plan");

  await expect(page.getByRole("heading", { name: "Choose your organization plan" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Free", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Plus", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Premium", exact: true })).toBeVisible();
  await expect(page.getByText("₱7,499", { exact: true })).toBeVisible();
  await expect(page.getByText("₱10,999", { exact: true })).toBeVisible();
  await expect(page.getByText("5,000 AI media suggestions per month", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Current plan" })).toBeDisabled();

  await page.getByRole("button", { name: "Choose Plus" }).click();
  await expect(page.getByRole("heading", { name: "Upgrade to Plus" })).toBeVisible();
  await expect(page.getByText("Secure QRPh checkout", { exact: true })).toBeVisible();
  await page.getByLabel("Payer email Optional").fill("payer@example.org");
  const paymentRequest = page.waitForRequest((request) =>
    request.method() === "POST" && request.url().endsWith("/api/v1/subscription/billing/payment-link"),
  );
  await page.route("https://paymongo.test/**", (route) => route.fulfill({ status: 200, body: "Payment portal" }));
  await page.getByRole("button", { name: "Continue to QRPh" }).click();
  expect((await paymentRequest).postDataJSON()).toEqual({
    planId: plusPlan.id,
    payerEmail: "payer@example.org",
  });
  await expect(page).toHaveURL("https://paymongo.test/checkout/plus");
});

test("plan cards remain within the mobile viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installSubscriptionSession(page);
  await page.goto("/upgrade-plan");

  await expect(page.getByRole("heading", { name: "Choose your organization plan" })).toBeVisible();
  const dimensions = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    content: document.documentElement.scrollWidth,
  }));
  expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport);
  await expect(page.locator(".subscription-plan-card")).toHaveCount(3);
});
