import { useCallback, useEffect, useRef, useState } from "react";
import {
  getMediaSuggestionProcessingStatus,
  suggestMedia,
  logAiInteraction,
  type MediaSuggestResult,
} from "../api/aiApi";

export type AiMediaSuggestState = "idle" | "loading" | "processing" | "ready" | "empty" | "error";

export interface UseAiMediaSuggestionsReturn {
  state: AiMediaSuggestState;
  results: MediaSuggestResult[];
  fetch: () => void;
}

const PROCESSING_RETRY_DELAYS_MS = [3_000, 6_000, 12_000, 24_000] as const;

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
  const [processing, setProcessing] = useState(false);
  const [processingCheckVersion, setProcessingCheckVersion] = useState(0);
  const [responseKey, setResponseKey] = useState("");

  const hasTextContext = hasSufficientMediaContext(eventTitle, caption, category, tags);
  const tagsKey = JSON.stringify(tags);
  const selectedImagesKey = JSON.stringify([...new Set(selectedImageAssetIds)].sort());
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
  const lastLoggedResultsRef = useRef("");
  const hasResultsRef = useRef(false);

  const requestSuggestions = useCallback(async (background = false) => {
    if (!submissionId || !hasContext) return;
    const requestTags = JSON.parse(tagsKey) as string[];
    const requestAssetIds = JSON.parse(selectedImagesKey) as string[];
    requestRef.current?.controller.abort();
    const request = {
      id: ++requestIdRef.current,
      controller: new AbortController(),
    };
    requestRef.current = request;
    setResponseKey(requestKey);
    if (!background) {
      hasResultsRef.current = false;
      setProcessing(false);
      setState("loading");
      setResults([]);
    }
    try {
      const response = await suggestMedia(submissionId, {
        eventTitle: eventTitle.trim() || undefined,
        caption: caption.trim() || undefined,
        category: category.trim() || undefined,
        tags: requestTags.length > 0 ? requestTags : undefined,
        selectedAssetIds: requestAssetIds.length > 0 ? requestAssetIds : undefined,
      }, request.controller.signal);
      if (requestRef.current?.id !== request.id) return;
      const retriesExhausted = visualRetryRef.current.attempts >= PROCESSING_RETRY_DELAYS_MS.length;
      hasResultsRef.current = response.results.length > 0;
      setResults(response.results);
      setProcessing(response.processing && !retriesExhausted);
      setState(response.results.length > 0
        ? "ready"
        : response.processing && !retriesExhausted ? "processing" : "empty");
      if (response.results.length > 0) {
        const loggedKey = JSON.stringify([requestKey, response.results.map((item) => item.id)]);
        if (lastLoggedResultsRef.current !== loggedKey) {
          lastLoggedResultsRef.current = loggedKey;
          logAiInteraction(submissionId, "media_recommendation", "shown");
        }
      }
    } catch {
      if (requestRef.current?.id === request.id && !request.controller.signal.aborted) {
        setProcessing(false);
        if (!hasResultsRef.current) setState("error");
      }
    } finally {
      if (requestRef.current?.id === request.id) requestRef.current = null;
    }
  }, [caption, category, eventTitle, hasContext, requestKey, selectedImagesKey, submissionId, tagsKey]);

  const fetch = useCallback(() => {
    visualRetryRef.current = { key: requestKey, attempts: 0 };
    void requestSuggestions();
  }, [requestKey, requestSuggestions]);

  useEffect(() => {
    if (!submissionId || !hasContext) {
      lastAutomaticRequest.current = "";
      visualRetryRef.current = { key: "", attempts: 0 };
      lastLoggedResultsRef.current = "";
      hasResultsRef.current = false;
      return;
    }
    if (visualRetryRef.current.key !== requestKey) {
      visualRetryRef.current = { key: requestKey, attempts: 0 };
    }
    if (lastAutomaticRequest.current === requestKey) return;
    const timer = window.setTimeout(() => {
      lastAutomaticRequest.current = requestKey;
      void requestSuggestions();
    }, 650);
    return () => {
      window.clearTimeout(timer);
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
    };
  }, [requestKey, requestSuggestions, submissionId, hasContext]);

  useEffect(() => {
    if (
      !submissionId
      || selectedImageAssetIds.length === 0
      || !processing
      || responseKey !== requestKey
    ) {
      return;
    }
    if (visualRetryRef.current.key !== requestKey) {
      visualRetryRef.current = { key: requestKey, attempts: 0 };
    }
    const retry = visualRetryRef.current;
    if (retry.attempts >= PROCESSING_RETRY_DELAYS_MS.length) return;
    const delay = PROCESSING_RETRY_DELAYS_MS[retry.attempts];
    retry.attempts += 1;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      const selectedIds = JSON.parse(selectedImagesKey) as string[];
      void getMediaSuggestionProcessingStatus(submissionId, selectedIds, controller.signal)
        .then((stillProcessing) => {
          if (!stillProcessing) {
            void requestSuggestions(true);
            return;
          }
          if (retry.attempts >= PROCESSING_RETRY_DELAYS_MS.length) {
            setProcessing(false);
            if (!hasResultsRef.current) setState("empty");
            return;
          }
          setProcessingCheckVersion((version) => version + 1);
        })
        .catch(() => {
          if (controller.signal.aborted) return;
          setProcessing(false);
          if (!hasResultsRef.current) setState("error");
        });
    }, delay);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [
    processing,
    processingCheckVersion,
    requestKey,
    requestSuggestions,
    responseKey,
    selectedImageAssetIds.length,
    selectedImagesKey,
    submissionId,
  ]);

  return {
    state: submissionId && hasContext && responseKey === requestKey ? state : "idle",
    results: submissionId && hasContext && responseKey === requestKey ? results : [],
    fetch,
  };
}
