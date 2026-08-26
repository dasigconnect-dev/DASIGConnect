import { api } from "./authApi";

// ─── UC-3.3 Classification, Recommendation & Media Suggestion ────────────────

export interface ClassificationSuggestions {
  suggestedCategory: string | null;
  suggestedTags: string[];
  confidence: number | null;
  assetCount: number;
}

export interface SimilarMediaAsset {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  aiCategory: string | null;
  createdAt: string;
}

export type AiInteractionType = "tag_classification" | "media_recommendation";
export type AiInteractionAction = "accepted" | "dismissed" | "shown";

export async function getClassificationSuggestions(
  submissionId: string
): Promise<ClassificationSuggestions> {
  const res = await api.get<ClassificationSuggestions>(
    `/ai/submissions/${submissionId}/suggestions`,
    { validateStatus: () => true }
  );
  if (res.status !== 200) throw new Error("suggestions_unavailable");
  return res.data;
}

export async function getSimilarMedia(
  submissionId: string
): Promise<SimilarMediaAsset[]> {
  const res = await api.get<SimilarMediaAsset[]>(
    `/ai/submissions/${submissionId}/similar-media`,
    { validateStatus: () => true }
  );
  if (res.status !== 200) return [];
  return res.data;
}

export interface MediaSuggestResult {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  aiCategory: string | null;
  similarityScore: number;
  matchReasons?: string[];
  createdAt: string;
}

export interface MediaSuggestRequest {
  eventTitle?: string;
  caption?: string;
  category?: string;
  tags?: string[];
}

export async function suggestMedia(
  submissionId: string,
  params: MediaSuggestRequest
): Promise<MediaSuggestResult[]> {
  const res = await api.post<MediaSuggestResult[]>(
    `/ai/submissions/${submissionId}/suggest-media`,
    params,
    { validateStatus: () => true }
  );
  if (res.status !== 200) return [];
  return res.data ?? [];
}

/** Fire-and-forget — never throws. */
export function logAiInteraction(
  submissionId: string,
  type: AiInteractionType,
  actionTaken: AiInteractionAction
): void {
  api
    .post(`/ai/submissions/${submissionId}/log-interaction`, {
      submissionId,
      type,
      actionTaken,
    })
    .catch(() => {});
}

// ─── UC-3.2 Caption Generation ───────────────────────────────────────────────

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
