import { useCallback, useEffect, useRef, useState } from "react";
import { suggestMedia, logAiInteraction, type MediaSuggestResult } from "../api/aiApi";

export type AiMediaSuggestState = "idle" | "loading" | "ready" | "empty" | "error";

export interface UseAiMediaSuggestionsReturn {
  state: AiMediaSuggestState;
  results: MediaSuggestResult[];
  fetch: () => void;
}

export function hasSufficientMediaContext(eventTitle: string, caption: string, category: string, tags: string[]) {
  return [eventTitle, caption, category, ...tags].join(" ").trim().length >= 10;
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

  const hasContext = hasSufficientMediaContext(eventTitle, caption, category, tags);
  const requestKey = JSON.stringify([submissionId, eventTitle.trim(), caption.trim(), category.trim(), tags]);
  const lastAutomaticRequest = useRef("");

  const fetch = useCallback(async () => {
    if (!submissionId || !hasContext) return;
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
  }, [caption, category, eventTitle, hasContext, submissionId, tags]);

  useEffect(() => {
    if (!submissionId || !hasContext) {
      lastAutomaticRequest.current = "";
      return;
    }
    if (lastAutomaticRequest.current === requestKey) return;
    const timer = window.setTimeout(() => {
      lastAutomaticRequest.current = requestKey;
      void fetch();
    }, 650);
    return () => window.clearTimeout(timer);
  }, [fetch, requestKey, submissionId, hasContext]);

  return {
    state: submissionId && hasContext ? state : "idle",
    results: submissionId && hasContext ? results : [],
    fetch,
  };
}
