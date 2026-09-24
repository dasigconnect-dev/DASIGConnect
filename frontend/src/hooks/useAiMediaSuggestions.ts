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
  tags: string[],
  selectedImageAssetIds: string[] = [],
): UseAiMediaSuggestionsReturn {
  const [state, setState] = useState<AiMediaSuggestState>("idle");
  const [results, setResults] = useState<MediaSuggestResult[]>([]);
  const [responseKey, setResponseKey] = useState("");

  const hasTextContext = hasSufficientMediaContext(eventTitle, caption, category, tags);
  const tagsKey = JSON.stringify(tags);
  const selectedImagesKey = JSON.stringify([...selectedImageAssetIds].sort());
  const hasContext = hasTextContext || selectedImageAssetIds.length > 0;
  const requestKey = JSON.stringify([
    submissionId,
    eventTitle.trim(),
    caption.trim(),
    category.trim(),
    tagsKey,
    selectedImagesKey,
  ]);
  const lastAutomaticRequest = useRef("");
  const requestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const requestIdRef = useRef(0);
  const visualRetryRef = useRef({ key: "", attempts: 0 });

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
      visualRetryRef.current = { key: "", attempts: 0 };
      return;
    }
    if (visualRetryRef.current.key !== requestKey) {
      visualRetryRef.current = { key: requestKey, attempts: 0 };
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

  useEffect(() => {
    // A new upload is attached before its asynchronous image embedding is
    // necessarily ready. Retry only visual-only empty results; hybrid/text
    // retries would repeatedly spend Voyage text-embedding tokens.
    if (
      !submissionId
      || hasTextContext
      || selectedImageAssetIds.length === 0
      || state !== "empty"
      || responseKey !== requestKey
    ) {
      return;
    }
    if (visualRetryRef.current.key !== requestKey) {
      visualRetryRef.current = { key: requestKey, attempts: 0 };
    }
    const retry = visualRetryRef.current;
    const delays = [3_000, 6_000, 12_000];
    if (retry.attempts >= delays.length) return;
    const delay = delays[retry.attempts];
    retry.attempts += 1;
    const timer = window.setTimeout(() => void fetch(), delay);
    return () => window.clearTimeout(timer);
  }, [fetch, hasTextContext, requestKey, responseKey, selectedImageAssetIds.length, state, submissionId]);

  return {
    state: submissionId && hasContext && responseKey === requestKey ? state : "idle",
    results: submissionId && hasContext && responseKey === requestKey ? results : [],
    fetch,
  };
}
