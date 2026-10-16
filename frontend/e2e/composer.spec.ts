import { expect, test, type Page } from "@playwright/test";
import { installFragmentableSse, installSession, institution, mockApi, type ApiRequest } from "./fixtures";

/**
 * The submission composer (features/submission/SubmissionScreen) end to end
 * against a small stateful fake backend: one draft kept in memory that the
 * create / update / upload / submit endpoints read and change, the way the
 * real API would. Safety net for refactoring the composer.
 */

const DRAFT_ID = "44444444-4444-4444-8444-444444444444";
const STORAGE_HOST = "https://storage.e2e.invalid";
// 1×1 transparent PNG
const PNG_BYTES = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "base64",
);

type Draft = Record<string, unknown> & { mediaAssets: Array<Record<string, unknown>> };

function savedAsset(n: number) {
  return {
    id: `55555555-5555-4555-8555-${String(n).padStart(12, "0")}`,
    storageUrl: `${STORAGE_HOST}/asset-${n}.png`,
    fileName: `asset-${n}.png`,
    fileType: "png",
    fileSizeBytes: PNG_BYTES.length,
    displayOrder: n - 1,
    caption: "",
    skipWatermark: false,
  };
}

function baseDraft(overrides: Partial<Draft> = {}): Draft {
  return {
    id: DRAFT_ID,
    status: "draft",
    institutionId: institution.id,
    institutionName: institution.name,
    contributorEmail: "contributor@example.invalid",
    eventTitle: "",
    eventDate: "",
    caption: "",
    albumName: "",
    tags: [],
    mediaTags: [],
    fastTrack: false,
    scheduledAt: null,
    createdAt: "2026-09-20T00:00:00Z",
    updatedAt: "2026-09-20T00:00:00Z",
    mediaCount: 0,
    mediaAssets: [],
    ...overrides,
  };
}

/** Wires the composer endpoints to one in-memory draft; returns it and a request log. */
async function mockComposerBackend(page: Page, initial: Draft | null) {
  const state: { draft: Draft | null; log: Array<{ key: string; body: unknown }> } = { draft: initial, log: [] };
  const record = (request: ApiRequest) => {
    const body = request.route.request().postDataJSON() as unknown;
    state.log.push({ key: `${request.method} ${request.path}`, body });
    return body as Record<string, unknown>;
  };
  const json = async (request: ApiRequest, body: unknown, status = 200) => {
    await request.route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
  };

  await page.route(`${STORAGE_HOST}/**`, async (route) => {
    state.log.push({ key: `${route.request().method()} storage`, body: null });
    await route.fulfill({ status: 200, body: "" });
  });

  const api = await mockApi(page, "contributor", {
    // The real lookups allow these (JPEG/PNG/WebP/GIF, MP4/MOV/WebM); the shared fixture only lists jpeg.
    "GET /api/v1/submissions/lookups": {
      allowedFileTypes: ["jpeg", "jpg", "png", "webp", "gif", "mp4", "mov", "webm"],
      allowedImageTypes: ["jpeg", "jpg", "png", "webp", "gif"],
      allowedVideoTypes: ["mp4", "mov", "webm"],
      maxFileSizeMb: 50, maxMediaAssetsPerSubmission: 10, maxTitleLength: 255,
      minScheduleLeadTimeHours: 2, maxScheduleDaysAhead: 30, categories: [], availableTags: [],
      guardrailsEnforced: true,
    },
    "GET /api/v1/media-assets": {
      items: [{
        ...savedAsset(2),
        assetCode: "MED-E2E-2",
        createdAt: "2026-09-20T00:00:00Z",
        institutionId: institution.id,
        institutionName: institution.name,
      }],
      totalCount: 1,
      page: 1,
      pageSize: 24,
    },
    "POST /api/v1/submissions": async (request: ApiRequest) => {
      state.draft = baseDraft({ ...record(request), id: DRAFT_ID, status: "draft" });
      await json(request, state.draft);
    },
    [`PATCH /api/v1/submissions/${DRAFT_ID}`]: async (request: ApiRequest) => {
      state.draft = { ...(state.draft ?? baseDraft()), ...record(request) } as Draft;
      await json(request, state.draft);
    },
    [`GET /api/v1/submissions/${DRAFT_ID}`]: async (request: ApiRequest) => {
      await json(request, state.draft);
    },
    [`POST /api/v1/submissions/${DRAFT_ID}/media/upload-url`]: async (request: ApiRequest) => {
      record(request);
      const n = (state.draft?.mediaAssets.length ?? 0) + 1;
      await json(request, {
        signedUrl: `${STORAGE_HOST}/upload/asset-${n}.png`,
        publicUrl: `${STORAGE_HOST}/asset-${n}.png`,
        path: `asset-${n}.png`,
      });
    },
    [`POST /api/v1/submissions/${DRAFT_ID}/media`]: async (request: ApiRequest) => {
      const body = record(request);
      const draft = state.draft ?? baseDraft();
      const asset = {
        ...savedAsset(draft.mediaAssets.length + 1),
        storageUrl: String(body.storageUrl),
        fileName: String(body.fileName),
        fileType: String(body.fileType),
        fileSizeBytes: Number(body.fileSizeBytes),
      };
      draft.mediaAssets = [...draft.mediaAssets, asset];
      draft.mediaCount = draft.mediaAssets.length;
      state.draft = draft;
      await json(request, draft);
    },
    [`GET /api/v1/media-assets/${savedAsset(2).id}`]: savedAsset(2),
    [`POST /api/v1/submissions/${DRAFT_ID}/assets`]: async (request: ApiRequest) => {
      const body = record(request);
      const draft = state.draft ?? baseDraft();
      const assetId = String(body.mediaAssetId ?? "");
      if (draft.mediaAssets.some((asset) => asset.id === assetId)) {
        await json(request, { message: "Asset is already attached to this submission." }, 409);
        return;
      }
      const asset = assetId === savedAsset(2).id ? savedAsset(2) : { ...savedAsset(2), id: assetId };
      draft.mediaAssets = [...draft.mediaAssets, asset];
      draft.mediaCount = draft.mediaAssets.length;
      state.draft = draft;
      await json(request, draft, 201);
    },
    [`DELETE /api/v1/submissions/${DRAFT_ID}/assets/${savedAsset(1).id}`]: async (request: ApiRequest) => {
      record(request);
      const draft = state.draft ?? baseDraft();
      draft.mediaAssets = draft.mediaAssets.filter((asset) => asset.id !== savedAsset(1).id);
      draft.mediaCount = draft.mediaAssets.length;
      state.draft = draft;
      await request.route.fulfill({ status: 204, body: "" });
    },
    [`PATCH /api/v1/submissions/${DRAFT_ID}/media/order`]: async (request: ApiRequest) => {
      record(request);
      await json(request, state.draft);
    },
    [`POST /api/v1/submissions/${DRAFT_ID}/submit`]: async (request: ApiRequest) => {
      record(request);
      state.draft = { ...(state.draft ?? baseDraft()), status: "pending", submittedAt: new Date().toISOString() };
      await json(request, state.draft);
    },
  });
  return { state, api };
}

/** Opens a composer step and waits until its content is showing (retries the click while it settles). */
async function goToStep(page: Page, name: RegExp, readyLocator: ReturnType<Page["locator"]>) {
  await expect(async () => {
    await page.getByRole("button", { name }).click();
    await expect(readyLocator).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 10_000 });
}

async function openComposer(page: Page, path: string, draft: Draft | null) {
  await installSession(page, "contributor");
  await installFragmentableSse(page);
  const backend = await mockComposerBackend(page, draft);
  await page.goto(path);
  await expect(page.locator("#composer-step-nav")).toBeVisible();
  return backend;
}

test.describe("submission composer", () => {
  test("a new Live Event post can be drafted and submitted", async ({ page }) => {
    const { state } = await openComposer(page, "/submissions/new", null);

    // Step 1 — media
    await page.locator("input.umt-input").setInputFiles({ name: "event.png", mimeType: "image/png", buffer: PNG_BYTES });

    await expect(page.getByRole("listitem", { name: /event\.png/ })).toBeVisible();

    // Step 2 — details
    const title = page.getByPlaceholder("e.g. CIT-U Innovation Summit 2026");
    await goToStep(page, /Step 2 Post Details/, title);
    await title.fill("E2E Innovation Summit");
    await page.getByRole("button", { name: "Select event date" }).click();
    await page.getByRole("button", { name: "Today" }).click();
    await page.getByPlaceholder("Write a compelling caption for the DASIG Facebook page...").fill(
      "Join us for the E2E Innovation Summit. #DASIG",
    );

    // Step 3 — album + Live Event
    const album = page.getByPlaceholder("Search, select, or create a new album");
    await goToStep(page, /Step 3 Organize & Schedule/, album);
    await album.fill("E2E Album");
    await page.getByText(/Create new album/).click();
    await page.getByRole("button", { name: "Live Event" }).click();

    await page.locator(".sub-guard-submit-btn").click();
    await page.getByRole("button", { name: /^(Submit for Approval|Submit Anyway)$/ }).last().click();

    await expect(page.getByText("Submission sent!")).toBeVisible();
    const keys = state.log.map((entry) => entry.key);
    const created = state.log.find((entry) => entry.key === "POST /api/v1/submissions")?.body as Record<string, unknown>;
    expect(created).toMatchObject({
      eventTitle: "E2E Innovation Summit",
      caption: "Join us for the E2E Innovation Summit. #DASIG",
      albumName: "E2E Album",
      fastTrack: true,
    });
    expect(keys).toContain(`POST /api/v1/submissions/${DRAFT_ID}/media/upload-url`);
    expect(keys).toContain("PUT storage");
    expect(keys).toContain(`POST /api/v1/submissions/${DRAFT_ID}/media`);
    expect(keys.at(-1)).toBe(`POST /api/v1/submissions/${DRAFT_ID}/submit`);
    expect(state.draft?.status).toBe("pending");
  });

  test("autosave persists an edit to a saved draft", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
        mediaAssets: [savedAsset(1)],
        mediaCount: 1,
      }),
    );

    const caption = page.getByPlaceholder("Write a compelling caption for the DASIG Facebook page...");
    await goToStep(page, /Step 2 Post Details/, caption);
    await expect(caption).toHaveValue("Original caption");
    await caption.fill("Edited caption, saved automatically");

    await expect
      .poll(
        () =>
          state.log.some(
            (entry) =>
              entry.key === `PATCH /api/v1/submissions/${DRAFT_ID}` &&
              (entry.body as Record<string, unknown>)?.caption === "Edited caption, saved automatically",
          ),
        { timeout: 6_000 },
      )
      .toBe(true);
  });

  test("a saved draft uploads newly selected media without a full draft save", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
        mediaAssets: [savedAsset(1)],
        mediaCount: 1,
      }),
    );

    await page.locator("input.umt-input").setInputFiles({
      name: "autosaved.png",
      mimeType: "image/png",
      buffer: PNG_BYTES,
    });

    await expect
      .poll(
        () => state.log.some((entry) => entry.key === `POST /api/v1/submissions/${DRAFT_ID}/media`),
        { timeout: 6_000 },
      )
      .toBe(true);
    expect(state.draft?.mediaAssets).toHaveLength(2);
  });

  test("removing an upload in flight leaves no attachment behind", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
      }),
    );
    await page.route(`**/api/v1/submissions/${DRAFT_ID}/media`, async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 250));
      await route.fallback();
    });

    await page.locator("input.umt-input").setInputFiles({
      name: "cancelled.png",
      mimeType: "image/png",
      buffer: PNG_BYTES,
    });
    await expect
      .poll(
        () => state.log.some((entry) => entry.key === `POST /api/v1/submissions/${DRAFT_ID}/media/upload-url`),
        { timeout: 6_000 },
      )
      .toBe(true);
    await page.getByRole("button", { name: "Remove cancelled.png" }).click();

    await page.waitForTimeout(1_000);
    await expect.poll(() => state.draft?.mediaAssets.length ?? -1, { timeout: 6_000 }).toBe(0);
    const registered = state.log.some(
      (entry) => entry.key === `POST /api/v1/submissions/${DRAFT_ID}/media`,
    );
    if (registered) {
      expect(state.log.some(
        (entry) => entry.key === `DELETE /api/v1/submissions/${DRAFT_ID}/assets/${savedAsset(1).id}`,
      )).toBe(true);
    }
  });

  test("a transient automatic upload failure falls back to the established draft retry", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
      }),
    );
    let failedOnce = false;
    await page.route(`**/api/v1/submissions/${DRAFT_ID}/media`, async (route) => {
      if (!failedOnce) {
        failedOnce = true;
        await route.fulfill({ status: 503, contentType: "application/json", body: JSON.stringify({ message: "Temporary failure" }) });
        return;
      }
      await route.fallback();
    });

    await page.locator("input.umt-input").setInputFiles({
      name: "retry.png",
      mimeType: "image/png",
      buffer: PNG_BYTES,
    });

    await expect.poll(() => state.draft?.mediaAssets.length ?? 0, { timeout: 8_000 }).toBe(1);
    expect(failedOnce).toBe(true);
  });

  test("a saved draft detaches removed media automatically", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
        mediaAssets: [savedAsset(1)],
        mediaCount: 1,
      }),
    );

    await page.getByRole("button", { name: "Remove asset-1.png" }).click();

    await expect
      .poll(
        () => state.log.some(
          (entry) => entry.key === `DELETE /api/v1/submissions/${DRAFT_ID}/assets/${savedAsset(1).id}`,
        ),
        { timeout: 6_000 },
      )
      .toBe(true);
    expect(state.draft?.mediaAssets).toHaveLength(0);
  });

  test("a saved draft attaches a library selection automatically", async ({ page }) => {
    const { state } = await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        eventTitle: "Saved Draft Event",
        eventDate: "2026-09-25",
        caption: "Original caption",
        albumName: "Saved Album",
      }),
    );

    await page.getByRole("tab", { name: "My Library" }).click();
    await page.getByRole("button", { name: /asset-2\.png/ }).click();
    await page.getByRole("button", { name: "Add Selected (1)" }).click();

    await expect
      .poll(
        () => state.log.some((entry) => entry.key === `POST /api/v1/submissions/${DRAFT_ID}/assets`),
        { timeout: 6_000 },
      )
      .toBe(true);
    expect(state.draft?.mediaAssets).toHaveLength(1);
    expect(state.draft?.mediaAssets[0]?.id).toBe(savedAsset(2).id);
  });

  test("My Submissions lists posts and opens one", async ({ page }) => {
    await installSession(page, "contributor");
    await installFragmentableSse(page);
    await mockApi(page, "contributor");
    await page.goto("/submissions");

    const card = page.getByText("Performance Regression Event").first();
    await expect(card).toBeVisible();
    await card.click();
    await expect(page).toHaveURL(/\/submissions\/22222222-2222-4222-8222-222222222222$/);
  });

  test("a draft sent back for revision shows the reviewer's feedback", async ({ page }) => {
    await openComposer(
      page,
      `/submissions/${DRAFT_ID}`,
      baseDraft({
        status: "needs_revision",
        eventTitle: "Needs Work Event",
        eventDate: "2026-09-25",
        caption: "First try",
        albumName: "Saved Album",
        mediaAssets: [savedAsset(1)],
        mediaCount: 1,
        validatorRemarks: "Please use a clearer photo.",
      }),
    );

    const banner = page.locator(".sub-revision-banner");
    await expect(banner).toContainText("Revision Requested");
    await expect(banner).toContainText("Please use a clearer photo.");
    await expect(page.locator(".sub-guard-submit-btn")).toContainText("Submit for Revision");
  });
});
