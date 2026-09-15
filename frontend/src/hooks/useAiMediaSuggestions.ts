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
  const [responseKey, setResponseKey] = useState("");

  const hasContext = hasSufficientMediaContext(eventTitle, caption, category, tags);
  const tagsKey = JSON.stringify(tags);
  const requestKey = JSON.stringify([
    submissionId,
    eventTitle.trim(),
    caption.trim(),
    category.trim(),
    tagsKey,
  ]);
  const lastAutomaticRequest = useRef("");
  const requestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const requestIdRef = useRef(0);

  const fetch = useCallback(async () => {
    if (!submissionId || !hasContext) return;
    const requestTags = JSON.parse(tagsKey) as string[];
    requestRef.current?.controller.abort();
    const request = {
      id: ++requestIdRef.current,
      controller: new AbortController(),
    };
    requestRef.current = request;
    setResponseKey(requestKey);
    setState("loading");
    setResults([]);
    try {
      const data = await suggestMedia(submissionId, {
        eventTitle: eventTitle.trim() || undefined,
        caption: caption.trim() || undefined,
        category: category.trim() || undefined,
        tags: requestTags.length > 0 ? requestTags : undefined,
      }, request.controller.signal);
      if (requestRef.current?.id !== request.id) return;
      setResults(data);
      setState(data.length === 0 ? "empty" : "ready");
      if (data.length > 0) {
        logAiInteraction(submissionId, "media_recommendation", "shown");
      }
    } catch {
      if (requestRef.current?.id === request.id && !request.controller.signal.aborted) {
        setState("error");
      }
    } finally {
      if (requestRef.current?.id === request.id) requestRef.current = null;
    }
  }, [caption, category, eventTitle, hasContext, requestKey, submissionId, tagsKey]);

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
    return () => {
      window.clearTimeout(timer);
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
    };
  }, [fetch, requestKey, submissionId, hasContext]);

  return {
    state: submissionId && hasContext && responseKey === requestKey ? state : "idle",
    results: submissionId && hasContext && responseKey === requestKey ? results : [],
    fetch,
  };
}
