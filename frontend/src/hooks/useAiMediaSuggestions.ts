import { useCallback, useEffect, useRef, useState } from "react";
import {
  getMediaSuggestionProcessingStatus,
  suggestMedia,
  logAiInteraction,
  isRateLimitError,
  MediaSuggestionApiError,
  type MediaSuggestResponse,
  type MediaSuggestResult,
} from "../api/aiApi";

export type AiMediaSuggestState =
  | "idle"
  | "loading"
  | "processing"
  | "ready"
  | "empty"
  | "no_candidates"
  | "embedding_error"
  | "rate_limited"
  | "error";

export interface UseAiMediaSuggestionsReturn {
  state: AiMediaSuggestState;
  results: MediaSuggestResult[];
  fetch: () => void;
  accept: () => void;
}

// Keep the first checks close together so a completed visual embedding is not
// hidden behind a multi-second polling gap. The later backoff still protects
// the status endpoint when an external provider is slow.
const PROCESSING_RETRY_DELAYS_MS = [750, 1_000, 1_500, 2_000, 3_000, 5_000, 8_000] as const;

export function hasSufficientMediaContext(eventTitle: string, caption: string, tags: string[]) {
  return [eventTitle, caption, ...tags].join(" ").trim().length >= 10;
}

export function useAiMediaSuggestions(
  submissionId: string | null,
  eventTitle: string,
  caption: string,
  tags: string[],
  selectedImageAssetIds: string[] = [],
  enabled: boolean = true,
): UseAiMediaSuggestionsReturn {
  const [state, setState] = useState<AiMediaSuggestState>("idle");
  const [results, setResults] = useState<MediaSuggestResult[]>([]);
  const [processing, setProcessing] = useState(false);
  const [processingCheckVersion, setProcessingCheckVersion] = useState(0);

  const previousSubmissionIdRef = useRef<string | null>(submissionId);
  const generatedForSubmissionRef = useRef<string | null>(null);

  const hasTextContext = hasSufficientMediaContext(eventTitle, caption, tags);
  const tagsKey = JSON.stringify(tags);
  const selectedImagesKey = JSON.stringify([...new Set(selectedImageAssetIds)].sort());
  const hasContext = hasTextContext || selectedImageAssetIds.length > 0;
  const requestKey = JSON.stringify([
    submissionId,
    eventTitle.trim(),
    caption.trim(),
    tagsKey,
    selectedImagesKey,
  ]);

  const requestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const requestIdRef = useRef(0);
  const visualRetryRef = useRef({ key: "", attempts: 0 });
  const lastLoggedResultsRef = useRef("");
  const hasResultsRef = useRef(false);
  const interactionRef = useRef<{
    submissionId: string;
    resultKey: string;
    settled: boolean;
  } | null>(null);

  const settleCurrentInteraction = useCallback((action: "accepted" | "dismissed") => {
    const interaction = interactionRef.current;
    if (!interaction || interaction.settled) return;
    interaction.settled = true;
    logAiInteraction(interaction.submissionId, "media_recommendation", action);
  }, []);

  const accept = useCallback(() => {
    settleCurrentInteraction("accepted");
  }, [settleCurrentInteraction]);

  // When submission draft changes, reset generated cache & state
  useEffect(() => {
    if (previousSubmissionIdRef.current !== submissionId) {
      previousSubmissionIdRef.current = submissionId;
      generatedForSubmissionRef.current = null;
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
      hasResultsRef.current = false;
      setState("idle");
      setResults([]);
      setProcessing(false);
    }
  }, [submissionId]);

  const applyResponse = useCallback((response: MediaSuggestResponse, reqKey: string) => {
    const retriesExhausted = visualRetryRef.current.attempts >= PROCESSING_RETRY_DELAYS_MS.length;
    hasResultsRef.current = response.results.length > 0;
    setResults(response.results);
    setProcessing(response.processing && !retriesExhausted);
    if (response.processing && !retriesExhausted) {
      setState("processing");
    } else if (response.results.length > 0) {
      setState("ready");
    } else if (response.outcome === "EMBEDDING_FAILED") {
      setState("embedding_error");
    } else if (response.outcome === "NO_INDEXED_CANDIDATES") {
      setState("no_candidates");
    } else if (response.outcome === "ERROR" || response.outcome === "PROCESSING") {
      setState("error");
    } else {
      setState("empty");
    }
    if (response.results.length > 0 && submissionId) {
      const loggedKey = JSON.stringify([reqKey, response.results.map((item) => item.id)]);
      if (lastLoggedResultsRef.current !== loggedKey) {
        settleCurrentInteraction("dismissed");
        lastLoggedResultsRef.current = loggedKey;
        interactionRef.current = {
          submissionId,
          resultKey: loggedKey,
          settled: false,
        };
        logAiInteraction(submissionId, "media_recommendation", "shown");
      }
    }
  }, [settleCurrentInteraction, submissionId]);

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
    if (!background) {
      settleCurrentInteraction("dismissed");
      hasResultsRef.current = false;
      setProcessing(false);
      setState("loading");
    }

    const handleError = (err: unknown) => {
      setProcessing(false);
      const isRateLimit =
        (err instanceof MediaSuggestionApiError && (err.status === 429 || err.status === 503)) ||
        isRateLimitError(err);
      if (isRateLimit) {
        setState("rate_limited");
      } else {
        if (!hasResultsRef.current) setState("error");
      }
    };

    try {
      const response = await suggestMedia(submissionId, {
        eventTitle: eventTitle.trim() || undefined,
        caption: caption.trim() || undefined,
        tags: requestTags.length > 0 ? requestTags : undefined,
        selectedAssetIds: requestAssetIds.length > 0 ? requestAssetIds : undefined,
      }, request.controller.signal);
      if (requestRef.current?.id !== request.id) return;
      applyResponse(response, requestKey);
    } catch (err: unknown) {
      if (requestRef.current?.id !== request.id || request.controller.signal.aborted) return;
      const is400 = err instanceof MediaSuggestionApiError && err.status === 400;
      // If 400 occurred and we included selectedAssetIds that backend rejected as not attached,
      // fallback to text context only
      if (is400 && requestAssetIds.length > 0) {
        try {
          const fallback = await suggestMedia(submissionId, {
            eventTitle: eventTitle.trim() || undefined,
            caption: caption.trim() || undefined,
            tags: requestTags.length > 0 ? requestTags : undefined,
          }, request.controller.signal);
          if (requestRef.current?.id !== request.id) return;
          applyResponse(fallback, requestKey);
          return;
        } catch (fallbackErr: unknown) {
          if (requestRef.current?.id !== request.id || request.controller.signal.aborted) return;
          handleError(fallbackErr);
          return;
        }
      }
      handleError(err);
    } finally {
      if (requestRef.current?.id === request.id) requestRef.current = null;
    }
  }, [applyResponse, caption, eventTitle, hasContext, requestKey, selectedImagesKey, settleCurrentInteraction, submissionId, tagsKey]);

  // Fetch / Redo: user explicitly requests generation or redo
  const fetch = useCallback(() => {
    if (!submissionId || !hasContext) return;
    generatedForSubmissionRef.current = submissionId;
    visualRetryRef.current = { key: requestKey, attempts: 0 };
    void requestSuggestions();
  }, [hasContext, requestKey, requestSuggestions, submissionId]);

  // Generate once when opened (enabled) if context is available and not already generated for this draft
  useEffect(() => {
    if (!enabled || !submissionId || !hasContext) {
      return;
    }
    if (generatedForSubmissionRef.current === submissionId) {
      return;
    }
    if (state === "loading" || state === "processing") {
      return;
    }
    generatedForSubmissionRef.current = submissionId;
    visualRetryRef.current = { key: requestKey, attempts: 0 };
    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) void requestSuggestions();
    });
    return () => {
      cancelled = true;
    };
  }, [enabled, submissionId, hasContext, state, requestKey, requestSuggestions]);

  useEffect(() => () => {
    settleCurrentInteraction("dismissed");
  }, [settleCurrentInteraction]);

  // Poll visual processing when embeddings are pending
  useEffect(() => {
    if (
      !submissionId
      || selectedImageAssetIds.length === 0
      || !processing
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
            setState(hasResultsRef.current ? "ready" : "error");
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
    selectedImageAssetIds.length,
    selectedImagesKey,
    submissionId,
  ]);

  return {
    state: submissionId && hasContext ? state : "idle",
    results: submissionId && hasContext ? results : [],
    fetch,
    accept,
  };
}
