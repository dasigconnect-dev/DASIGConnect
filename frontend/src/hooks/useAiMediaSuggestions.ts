import { useState } from "react";
import { suggestMedia, logAiInteraction, type MediaSuggestResult } from "../api/aiApi";

export type AiMediaSuggestState = "idle" | "loading" | "ready" | "empty" | "error";

export interface UseAiMediaSuggestionsReturn {
  state: AiMediaSuggestState;
  results: MediaSuggestResult[];
  fetch: () => void;
}

export function useAiMediaSuggestions(
  submissionId: string | null,
  eventTitle: string,
  caption: string,
  category: string,
  tags: string[]
): UseAiMediaSuggestionsReturn {
  const [state, setState] = useState<AiMediaSuggestState>("idle");
  const [results, setResults] = useState<MediaSuggestResult[]>([]);

  const hasContext =
    eventTitle.trim().length > 0 ||
    caption.trim().length > 0 ||
    category.length > 0 ||
    tags.length > 0;

  async function fetch() {
    if (!submissionId || !hasContext || state === "loading") return;
    setState("loading");
    setResults([]);
    try {
      const data = await suggestMedia(submissionId, {
        eventTitle: eventTitle.trim() || undefined,
        caption: caption.trim() || undefined,
        category: category.trim() || undefined,
        tags: tags.length > 0 ? tags : undefined,
      });
      setResults(data);
      setState(data.length === 0 ? "empty" : "ready");
      if (data.length > 0) {
        logAiInteraction(submissionId, "media_recommendation", "shown");
      }
    } catch {
      setState("error");
    }
  }

  return { state, results, fetch };
}
