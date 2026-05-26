import { api } from "./authApi";

export type CaptionTone = "professional" | "community" | "energetic";

export interface CaptionVariant {
  tone: CaptionTone;
  caption: string;
}

export interface CaptionResponse {
  submissionId: string;
  variants: CaptionVariant[];
  /** X-RateLimit-Remaining header value parsed from response */
  rateLimitRemaining?: number;
  /** X-RateLimit-Reset header value (epoch seconds) */
  rateLimitReset?: number;
}

export type CaptionAction =
  | "use"
  | "use_then_edited"
  | "edit"
  | "re_generate"
  | "dismiss";

export async function suggestCaption(
  submissionId: string,
  existingCaption?: string
): Promise<CaptionResponse> {
  const res = await api.post<{ submissionId: string; variants: CaptionVariant[] }>(
    "/ai/caption",
    {
      submissionId,
      // Only send if non-empty — backend treats null/absent as "generate from scratch"
      ...(existingCaption?.trim() ? { existingCaption: existingCaption.trim() } : {}),
    },
    { validateStatus: () => true }
  );

  const remaining = res.headers?.["x-ratelimit-remaining"];
  const reset = res.headers?.["x-ratelimit-reset"];

  if (res.status === 429) {
    const error: RateLimitError = new Error("Rate limit reached") as RateLimitError;
    error.isRateLimit = true;
    error.rateLimitReset = reset ? Number(reset) : undefined;
    throw error;
  }
  if (res.status === 504) {
    throw new Error("timeout");
  }
  if (res.status !== 200) {
    throw new Error("unavailable");
  }

  return {
    ...res.data,
    rateLimitRemaining: remaining != null ? Number(remaining) : undefined,
    rateLimitReset: reset != null ? Number(reset) : undefined,
  };
}

export interface RateLimitError extends Error {
  isRateLimit: true;
  rateLimitReset?: number;
}

export function isRateLimitError(e: unknown): e is RateLimitError {
  return (e as RateLimitError)?.isRateLimit === true;
}

/** Fire-and-forget — never throws. */
export function logCaptionInteraction(
  submissionId: string,
  actionTaken: CaptionAction,
  toneSelected?: CaptionTone
): void {
  api
    .post("/ai/caption/log", { submissionId, actionTaken, toneSelected })
    .catch(() => {});
}
