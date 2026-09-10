# UC-1.6 AI & Text Tools Integration

**Use Case ID:** UC-1.6

**Use Case Name:** AI & Text Tools Integration

**Actor(s):** Contributor, Administrator, Moderator — all three use the same composer. The AI caption endpoint is `hasAnyRole('CONTRIBUTOR','MODERATOR','ADMIN')`; a Moderator without an institution scope may generate captions for any institution's submission.

**Precondition(s):** The actor is in an active composer session (UC-1.5) **on a saved draft** — the "Suggest Caption" control is not rendered until the draft has an id, and the backend rejects a caption request without a real `submissionId`. Fancy Text works on any editable draft. Neither tool is available on a read-only submission (rejected / published / etc.), and **AI caption is also hidden while the composer is in Live Event Fast-Track mode** (a frontend-only gate — the backend does not block it; see note at the end).

## Main Flow — AI Caption Generation

1. The actor clicks **Suggest Caption** in the caption field's action row.
2. A prompt dialog opens. The actor may optionally type instructions (tone, focus, length, details to include) and pick one of three **tones** — `professional` (default), `community`, `energetic`. The dialog can proceed as long as there is an attached image, an existing caption, **or** a typed prompt.
3. The actor confirms. The prompt is limited to **280 characters** — the counter turns over-limit and Generate is disabled past it (backend also enforces `@Size(max = 280)`).
4. The system sends the submission's images (up to 4, base64-encoded; any over 5 MB downscaled in-memory to a JPEG under 5 MB), the media metadata, the event title/date/category/institution, the existing caption, the prompt, and the selected tone to the Anthropic Claude API (`claude-haiku-4-5-20251001`), with a **30-second timeout**.
5. The system returns **exactly one caption** for the selected tone (the request asks Claude for a single variant). The actor inserts it into the caption field, dismisses it, or clicks **Regenerate** (optionally after revising the prompt — back to step 2).
6. The actor edits the inserted caption freely before saving or submitting.
7. Each outcome is recorded via `POST /api/v1/ai/caption/log` (`use` / `use_then_edited` / `dismiss` / `re_generate` + tone) into `ai_interaction_log`, best-effort — a logging failure never blocks the UI.

**Rate limit:** each user may make **30 caption requests per rolling hour**. Over the limit, the endpoint returns `429` with `X-RateLimit-Remaining` / `X-RateLimit-Reset` headers, and the button switches to a "rate-limited" state showing when it will be available again.

**Requested word count:** if the prompt names a word count (e.g. "around 200 words"), the largest number is parsed; if it exceeds **2000**, the request is rejected with `400`. When a word range is requested, the client retries up to 3× to bring Claude's output into range.

## Main Flow — Fancy Text Styling

1. The actor clicks the **Fancy text** button in the caption field's action row (it does **not** pop up automatically on text selection). A panel opens.
2. If text is selected, the panel shows that text transformed into each available Unicode style, previewed live in the caption on hover/focus. If nothing is selected, the panel shows "No caption text selected" (or, once open, targets the whole caption).
3. **Styles:** Bold Serif, Italic Serif, Bold Sans-Serif, Italic Sans-Serif, Script / Cursive, and **Plain** (revert to standard characters).
4. The actor clicks a style; the selected text is replaced with its styled Unicode equivalent. Re-styling already-styled text reverts to plain first, then applies the new style.
5. The actor may style other selections or use **Plain** to revert.

## Alternative Flows

- **A1 — AI Request Timeout:** No Claude response within 30 s → the button shows a timeout notice ("AI request timed out. Retry or continue editing manually."), stays clickable for a retry, and auto-returns to idle after ~5 s. Endpoint returns `504`.
- **A2 — AI Service Unavailable:** A failed call (non-timeout) returns `503`; the button briefly shows "AI unavailable" and is disabled, then **auto-recovers to idle after ~5 seconds**. There is no health check or persistent disable — the control only reacts after a failed request. The core drafting flow (UC-1.5) is unaffected throughout.
- **A3 — No Image Attached:** Generation proceeds with an empty image list, provided there is an existing caption or a typed prompt; Claude works from text context alone.
- **A4 — Empty Prompt:** The request uses the selected tone (default `professional`) and returns one caption in that tone.
- **A5 — Prompt Exceeds Length Limit:** The prompt field caps at **280 characters** — the character counter goes over-limit and **Generate is disabled** until it is shortened (the textarea hard-stops a little above 280); the backend also rejects an over-length prompt with `400`.
- **A6 — Revert Fancy Text:** Selecting styled text and choosing **Plain** maps each styled character back to its standard equivalent.
- **A7 — Unsupported Character Styling:** Only A–Z / a–z (and digits, for the bold styles) have Unicode equivalents; punctuation, emoji, accented letters, and spaces pass through unchanged while the eligible characters are styled.

## Postcondition(s)

The caption field reflects the actor's chosen AI-suggested caption (one caption, in the selected tone, generated per their prompt) and/or fancy-styled Unicode text, fully editable and ready for continued drafting (UC-1.5) or submission (UC-1.9). AI interaction outcomes are recorded in `ai_interaction_log`.

---

_Verified against the running code as of 2026-09-11. Primary sources: `CaptionController` (`/ai/caption`, `/ai/caption/log`, the 30/hour rate limiter), `CaptionGenerationService`, `ClaudeVisionClient` (30 s timeout, ≤4 images, >5 MB downscale, one-variant prompt, requested-word-count cap `MAX_REQUESTED_CAPTION_WORDS = 2000`), `CaptionRequestDto` (`@Size(max = 280)` prompt, `professional|community|energetic` tone), `frontend/src/hooks/useAiCaptionAssist.ts`, `AiCaptionButton.tsx` / `AiCaptionPromptDialog.tsx` (`AI_CAPTION_PROMPT_MAX_LENGTH = 280`), `FancyTextTool.tsx`._

**Corrections from the prior draft of this UC:**
- Actors: added Moderator.
- Precondition: the draft must be **saved** before "Suggest Caption" appears; AI caption is also hidden in Fast-Track mode and on read-only submissions.
- Main Flow: the system returns **one** caption per request (selected tone), not "1–3 variants". Added the concrete tone set, the 30/hour rate limit, the requested-word-count cap, the ≤4-image limit, the interaction-logging endpoint, and the model id — none were in the prior draft.
- A2: the control is not persistently disabled while the service is down — it shows a transient error and auto-recovers after ~5 s; there is no health check.
- A4: an empty prompt yields one caption in the default tone, not plural tone-labeled variants.
- A5: the limit is **280 characters**.
- Fancy Text step 1: the panel is opened by a **button**, not shown automatically on selection. The style set is Bold/Italic Serif, Bold/Italic Sans, Script, and Plain — there is no "small caps".

**Known follow-ups (not fixed):**
- Fancy Text enforces a **3000-character** caption ceiling on styled output, while the composer's own caption trim/readiness logic uses **2000** — the two limits should be unified.
- The Fast-Track gate on AI caption is frontend-only and uncommented; decide whether it's intentional.
