import { useState } from "react";
import { suggestMedia, getSimilarMedia, logAiInteraction, type MediaSuggestResult } from "../api/aiApi";

export type AiMediaSuggestState = "idle" | "loading" | "ready" | "empty" | "error";
export type AiMediaSuggestMode = "similar" | "details";

/** Unified suggestion shape; `similarityScore`/`matchReasons` are present only for text-based. */
export interface UnifiedSuggestion {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  aiCategory: string | null;
  similarityScore?: number;
  matchReasons?: string[];
}

export interface MediaSuggestDetails {
  eventTitle: string;
  caption: string;
  category: string;
  tags: string[];
}

export interface UseAiMediaSuggestionsReturn {
  state: AiMediaSuggestState;
  results: UnifiedSuggestion[];
  mode: AiMediaSuggestMode | null;
  /** Image-based: "more like the photos you selected" (no text needed). */
  fetchSimilar: () => void;
  /** Text-based: rank library media from the post's title/caption/tags. */
  fetchFromDetails: () => void;
  reset: () => void;
}

function fromSuggest(r: MediaSuggestResult): UnifiedSuggestion {
  return {
    id: r.id,
    assetCode: r.assetCode,
    storageUrl: r.storageUrl,
    fileName: r.fileName,
    fileType: r.fileType,
    aiCategory: r.aiCategory,
    similarityScore: r.similarityScore,
    matchReasons: r.matchReasons,
  };
}

/**
 * AI media suggestions for the submission Media step. Both modes call `ensureDraft()` first so
 * the draft is auto-saved (and its media attached) — the user never has to manually save to
 * unlock suggestions. `details` feeds the text-based mode.
 */
export function useAiMediaSuggestions(
  ensureDraft: () => Promise<string | null>,
  details: MediaSuggestDetails,
): UseAiMediaSuggestionsReturn {
  const [state, setState] = useState<AiMediaSuggestState>("idle");
  const [results, setResults] = useState<UnifiedSuggestion[]>([]);
  const [mode, setMode] = useState<AiMediaSuggestMode | null>(null);

  async function run(nextMode: AiMediaSuggestMode, fetcher: (id: string) => Promise<UnifiedSuggestion[]>) {
    if (state === "loading") return;
    setState("loading");
    setResults([]);
    setMode(nextMode);
    const submissionId = await ensureDraft();
    if (!submissionId) {
      setState("error");
      return;
    }
    try {
      const data = await fetcher(submissionId);
      setResults(data);
      setState(data.length === 0 ? "empty" : "ready");
      if (data.length > 0) logAiInteraction(submissionId, "media_recommendation", "shown");
    } catch {
      setState("error");
    }
  }

  function fetchSimilar() {
    void run("similar", async (id) => (await getSimilarMedia(id)).map((a) => ({
      id: a.id,
      assetCode: a.assetCode,
      storageUrl: a.storageUrl,
      fileName: a.fileName,
      fileType: a.fileType,
      aiCategory: a.aiCategory,
    })));
  }

  function fetchFromDetails() {
    void run("details", async (id) =>
      (await suggestMedia(id, {
        eventTitle: details.eventTitle.trim() || undefined,
        caption: details.caption.trim() || undefined,
        category: details.category.trim() || undefined,
        tags: details.tags.length > 0 ? details.tags : undefined,
      })).map(fromSuggest));
  }

  function reset() {
    setState("idle");
    setResults([]);
    setMode(null);
  }

  return { state, results, mode, fetchSimilar, fetchFromDetails, reset };
}
