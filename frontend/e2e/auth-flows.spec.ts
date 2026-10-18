import { expect, test, type Page } from "@playwright/test";
import { installFragmentableSse, installSession, mockApi, userFor, type ApiRequest } from "./fixtures";

/**
 * The auth flows in features/auth/hooks (useAuthSession and friends), end to
 * end against the mocked API: sign-in, lockout, forgot/reset password, invite
 * activation, the session-expired modal, and sign-out.
 */

const STRONG_PASSWORD = "Harbor#Lantern92";

function jwt(secondsFromNow = 86_400) {
  const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + secondsFromNow })).toString(
    "base64url",
  );
  return `e30.${payload}.test`;
}

async function fulfillJson({ route }: ApiRequest, status: number, body: unknown) {
  await route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
}

async function requestBody(page: Page, method: string, pathSuffix: string) {
  const request = await page.waitForRequest(
    (req) => req.method() === method && new URL(req.url()).pathname.endsWith(pathSuffix),
  );
  return request.postDataJSON() as Record<string, unknown>;
}

test.describe("auth flows", () => {
  test("signing in stores the session and opens the dashboard", async ({ page }) => {
    await installFragmentableSse(page);
    const api = await mockApi(page, "contributor", { "POST /api/v1/auth/login": { accessToken: jwt() } });
    await page.goto("/login");

    await page.locator("#l-email").fill("  Contributor@Example.Invalid ");
    await page.locator("#l-pw").fill(STRONG_PASSWORD);
    const loginBody = requestBody(page, "POST", "/auth/login");
    await page.getByRole("button", { name: "Sign In" }).click();

    expect(await loginBody).toEqual({ email: "contributor@example.invalid", password: STRONG_PASSWORD });
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.locator("#main-content")).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem("dasigconnect_token"))).not.toBeNull();
    expect(api.count("GET", "/api/v1/me")).toBeGreaterThan(0);
  });

  test("Enter submits the login form with what was typed", async ({ page }) => {
    await installFragmentableSse(page);
    await mockApi(page, "contributor", { "POST /api/v1/auth/login": { accessToken: jwt() } });
    await page.goto("/login");

    await page.locator("#l-email").fill("contributor@example.invalid");
    await page.locator("#l-pw").fill(STRONG_PASSWORD);
    const loginBody = requestBody(page, "POST", "/auth/login");
    await page.locator("#l-pw").press("Enter");

    expect((await loginBody).password).toBe(STRONG_PASSWORD);
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test("five failed sign-ins lock the form", async ({ page }) => {
    await mockApi(page, "contributor", {
      "POST /api/v1/auth/login": (request: ApiRequest) => fulfillJson(request, 401, { error: "Invalid credentials" }),
    });
    await page.goto("/login");
    await page.locator("#l-email").fill("contributor@example.invalid");
    await page.locator("#l-pw").fill("wrong-password");

    for (let attempt = 1; attempt <= 5; attempt += 1) {
      const response = page.waitForResponse((res) => res.url().endsWith("/auth/login"));
      await page.getByRole("button", { name: "Sign In" }).click();
      await response;
      if (attempt < 5) await expect(page.locator("#login-err-msg")).toContainText("Invalid credentials");
    }

    await expect(page.locator("#lockout-box")).not.toHaveClass(/hidden/);
    await expect(page.locator("#lockout-timer")).toContainText(/1[45]:\d\d/);
  });

  test("forgot password confirms without revealing whether the account exists", async ({ page }) => {
    await mockApi(page, "contributor", {
      "POST /api/v1/auth/forgot-password": (request: ApiRequest) => fulfillJson(request, 404, { error: "Not found" }),
    });
    await page.goto("/forgot-password");

    await page.locator("#forgot-email").fill("someone@example.invalid");
    const body = requestBody(page, "POST", "/auth/forgot-password");
    await page.getByRole("button", { name: "Send Reset Link" }).click();

    expect(await body).toEqual({ email: "someone@example.invalid" });
    await expect(page).toHaveURL(/\/forgot-password-sent$/);
    await expect(page.locator("body")).toContainText("someone@example.invalid");
  });

  test("reset password sends the link's token and the new password", async ({ page }) => {
    await mockApi(page, "contributor", { "POST /api/v1/auth/reset-password": {} });
    await page.goto("/reset-password?token=reset-token-123");

    await page.locator("#reset-new-password").fill(STRONG_PASSWORD);
    await page.locator("#reset-confirm-password").fill(STRONG_PASSWORD);
    const body = requestBody(page, "POST", "/auth/reset-password");
    await page.getByRole("button", { name: "Update Password" }).click();

    expect(await body).toEqual({ token: "reset-token-123", newPassword: STRONG_PASSWORD });
    await expect(page.getByRole("button", { name: /Continue to Sign In/ })).toBeVisible();
  });

  test("accepting an invite activates the account and signs in", async ({ page }) => {
    await installFragmentableSse(page);
    await mockApi(page, "contributor", {
      "GET /api/v1/invitations/validate": {
        recipientEmail: "contributor@example.invalid",
        assignedRole: "CONTRIBUTOR",
        institutionName: "Cebu Institute of Technology - University",
        expiresAt: new Date(Date.now() + 48 * 3_600_000).toISOString(),
      },
      "POST /api/v1/invitations/accept": { accessToken: jwt() },
    });
    await page.goto("/invite?token=invite-token-456");

    await expect(page.locator("#inv-email-display")).toContainText("contributor@example.invalid");
    await page.locator("#inv-first-name").fill("Test");
    await page.locator("#inv-last-name").fill("Contributor");
    await page.locator("#inv-pw").fill(STRONG_PASSWORD);
    await page.locator("#inv-pw2").fill(STRONG_PASSWORD);
    const body = requestBody(page, "POST", "/invitations/accept");
    await page.locator("#inv-btn").click();

    expect(await body).toEqual({
      token: "invite-token-456",
      firstName: "Test",
      lastName: "Contributor",
      password: STRONG_PASSWORD,
    });
    await expect(page).toHaveURL(/\/dashboard$/);
    expect(await page.evaluate(() => localStorage.getItem("dasigconnect_token"))).not.toBeNull();
  });

  test("re-login from the session-expired modal keeps the user on their page", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    await mockApi(page, "contributor", { "POST /api/v1/auth/login": { accessToken: jwt() } });
    await page.goto("/dashboard");
    await expect(page.locator("#main-content")).toBeVisible();

    await page.evaluate(() => window.dispatchEvent(new Event("dasigconnect:session-expired")));
    await expect(page.locator("#session-modal")).not.toHaveClass(/hidden/);
    await expect(page.locator("#modal-email")).toHaveValue(userFor("contributor").email);

    await page.locator("#modal-pw").fill(STRONG_PASSWORD);
    const body = requestBody(page, "POST", "/auth/login");
    await page.getByRole("button", { name: "Sign In & Resume" }).click();

    expect(await body).toEqual({ email: userFor("contributor").email, password: STRONG_PASSWORD });
    await expect(page.locator("#session-modal")).toHaveClass(/hidden/);
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test("Enter submits the session-expired modal on any page", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    await mockApi(page, "contributor", { "POST /api/v1/auth/login": { accessToken: jwt() } });
    await page.goto("/notifications");
    await expect(page.locator("#main-content")).toBeVisible();

    await page.evaluate(() => window.dispatchEvent(new Event("dasigconnect:session-expired")));
    await page.locator("#modal-pw").fill(STRONG_PASSWORD);
    const body = requestBody(page, "POST", "/auth/login");
    await page.locator("#modal-pw").press("Enter");

    expect((await body).password).toBe(STRONG_PASSWORD);
    await expect(page.locator("#session-modal")).toHaveClass(/hidden/);
    await expect(page).toHaveURL(/\/notifications$/);
  });

  test("signing out clears the session and returns to the landing page", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    const api = await mockApi(page, "contributor", { "POST /api/v1/auth/logout": {} });
    await page.goto("/dashboard");
    await expect(page.locator("#main-content")).toBeVisible();

    await page.locator("#dash-avatar").click();
    await page.getByRole("button", { name: "Sign Out", exact: true }).click();

    await expect(page).toHaveURL(/\/$/);
    await expect.poll(() => api.count("POST", "/api/v1/auth/logout")).toBe(1);
    expect(await page.evaluate(() => localStorage.getItem("dasigconnect_token"))).toBeNull();
  });

  test("a signed-out visit to a signed-in page goes to the login page", async ({ page }) => {
    await mockApi(page, "contributor");
    await page.goto("/calendar");
    await expect(page).toHaveURL(/\/login$/);
  });

  test("signing out from the session modal on the composer lands on the landing page", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    await mockApi(page, "contributor", { "POST /api/v1/auth/logout": {} });
    await page.goto("/submissions/new");
    await expect(page.locator("#composer-step-nav")).toBeVisible();

    await page.evaluate(() => window.dispatchEvent(new Event("dasigconnect:session-expired")));
    await page.locator(".session-signout-btn").click();

    await expect(page).toHaveURL(/\/$/);
  });

  test("Case-30d: Bulk invitations preview, validation pill badges, and batch send (RP-05 / REQ-127)", async ({ page }) => {
    await installSession(page, "admin");
    await installFragmentableSse(page);

    const mockPreviewResponse = {
      totalRows: 3,
      validCount: 2,
      invalidCount: 1,
      rows: [
        {
          index: 1,
          email: "contributor1@cit.edu",
          role: "contributor",
          institutionId: "11111111-1111-4111-8111-111111111111",
          institutionName: "CITU",
          status: "valid",
          reason: null,
        },
        {
          index: 2,
          email: "moderator1@cit.edu",
          role: "moderator",
          institutionId: "11111111-1111-4111-8111-111111111111",
          institutionName: "CITU",
          status: "valid",
          reason: null,
        },
        {
          index: 3,
          email: "duplicate@cit.edu",
          role: "contributor",
          institutionId: "11111111-1111-4111-8111-111111111111",
          institutionName: "CITU",
          status: "duplicate_in_batch",
          reason: "Duplicate email in batch",
        },
      ],
    };

    const mockSendResponse = {
      batchId: "55555555-5555-4555-8555-555555555555",
      rowCount: 3,
      sentCount: 2,
      rejectedCount: 1,
      results: mockPreviewResponse.rows,
    };

    await mockApi(page, "admin", {
      "GET /api/v1/users": [],
      "GET /api/v1/invitations/pending/network": [],
      "GET /api/v1/invitations/pending/admins": [],
      "POST /api/v1/invitations/batch/preview": mockPreviewResponse,
      "POST /api/v1/invitations/batch": mockSendResponse,
    });

    await page.goto("/admin/user-management");
    await expect(page.locator(".um-screen")).toBeVisible();

    // Open Invite User modal
    const inviteBtn = page.getByRole("button", { name: /Invite User/i });
    await expect(inviteBtn).toBeVisible();
    await inviteBtn.click();

    // Switch to Bulk CSV tab
    const bulkTab = page.getByRole("tab", { name: /Bulk CSV/i });
    await expect(bulkTab).toBeVisible();
    await bulkTab.click();

    // Fill CSV input
    const textarea = page.locator("#bulk-invitation-input");
    await expect(textarea).toBeVisible();
    await textarea.fill(
      "contributor1@cit.edu, contributor, CITU\nmoderator1@cit.edu, moderator, CITU\nduplicate@cit.edu, contributor, CITU",
    );

    // Preview Batch
    const previewBtn = page.getByRole("button", { name: /Preview Batch & Validate/i });
    await previewBtn.click();

    // Verify preview table and status badges
    const previewTable = page.locator("table[aria-label='Batch Preview Table']");
    await expect(previewTable).toBeVisible();
    await expect(page.locator(".um-batch-pill.is-valid")).toContainText("2 Valid");
    await expect(page.locator(".um-batch-pill.is-error")).toContainText("1 Issues");
    await expect(page.locator(".status-valid").first()).toHaveText("Valid");
    await expect(page.locator(".status-duplicate_in_batch")).toHaveText("Duplicate in Batch");
    await expect(page.locator(".um-batch-reason").last()).toContainText("Duplicate email in batch");

    // Send Batch
    const sendBatchBtn = page.getByRole("button", { name: /Confirm & Send 2 Valid Invitation/i });
    await expect(sendBatchBtn).toBeVisible();
    await sendBatchBtn.click();

    // Verify batch summary success banner
    const successBanner = page.locator(".um-batch-success");
    await expect(successBanner).toBeVisible();
    await expect(page.locator(".um-batch-success-title")).toContainText("Batch Invitations Processed");
    await expect(page.locator(".um-batch-success-desc")).toContainText("Sent 2 invitations. 1 row(s) were rejected/skipped.");
    await expect(page.locator(".um-batch-success-meta")).toContainText("55555555-5555-4555-8555-555555555555");
  });
});

