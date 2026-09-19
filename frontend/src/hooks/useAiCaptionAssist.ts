import { useEffect, useRef, useState } from "react";
import {
  suggestCaption,
  logCaptionInteraction,
  isRateLimitError,
  type CaptionVariant,
  type CaptionTone,
} from "../api/aiApi";

export type AiCaptionState =
  | "idle"
  | "loading"
  | "rate-limited"
  | "error-timeout"
  | "error-unavailable";

export interface UseAiCaptionAssistReturn {
  state: AiCaptionState;
  variants: CaptionVariant[] | null;
  rateLimitReset: number | null;
  canSuggest: boolean;
  notice: string | null;
  suggest: (
    prompt?: string,
    tone?: CaptionTone,
    submissionIdOverride?: string,
    existingCaptionOverride?: string,
  ) => Promise<CaptionVariant | null>;
  dismissAll: () => void;
  regenerate: () => void;
  logApply: (tone: CaptionTone, action?: "use" | "use_then_edited") => void;
  logApplyForSubmission: (
    submissionIdOverride: string,
    tone: CaptionTone,
    action?: "use" | "use_then_edited",
  ) => void;
  logDismissOne: (tone: CaptionTone) => void;
}

export function useAiCaptionAssist(
  submissionId: string | null,
  _hasImageAssets: boolean,
  existingCaption?: string
): UseAiCaptionAssistReturn {
  const [state, setState] = useState<AiCaptionState>("idle");
  const [variants, setVariants] = useState<CaptionVariant[] | null>(null);
  const [rateLimitReset, setRateLimitReset] = useState<number | null>(null);
  const [lastPrompt, setLastPrompt] = useState("");
  const [lastTone, setLastTone] = useState<CaptionTone>("professional");
  const [notice, setNotice] = useState<string | null>(null);
  const [responseContext, setResponseContext] = useState<string | null>(null);
  const cooldownRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const requestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const requestIdRef = useRef(0);

  const inFlightSubmissionIdRef = useRef<string | null>(null);
  const previousSubmissionIdRef = useRef<string | null>(submissionId);

  const canSuggest = !!submissionId;
  const contextMatches =
    responseContext === submissionId ||
    (!submissionId && !!inFlightSubmissionIdRef.current && responseContext === inFlightSubmissionIdRef.current);

  useEffect(() => {
    const prevId = previousSubmissionIdRef.current;
    previousSubmissionIdRef.current = submissionId;

    // If submissionId changed from null/empty to the target ID of an in-flight request,
    // do NOT abort the in-flight request — it's the draft that was just created and saved for this generation!
    if (!prevId && submissionId && submissionId === inFlightSubmissionIdRef.current) {
      setResponseContext(submissionId);
      return;
    }

    // If submissionId actually changed to a different submission, clean up previous state
    if (prevId !== submissionId) {
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
      inFlightSubmissionIdRef.current = null;
      if (cooldownRef.current) clearTimeout(cooldownRef.current);
      cooldownRef.current = null;
      setState("idle");
      setVariants(null);
      setNotice(null);
      setResponseContext(submissionId);
    }
  }, [submissionId]);

  // Clean up on component unmount
  useEffect(() => {
    return () => {
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
      inFlightSubmissionIdRef.current = null;
      if (cooldownRef.current) clearTimeout(cooldownRef.current);
      cooldownRef.current = null;
    };
  }, []);

  async function suggest(
    prompt = "",
    tone: CaptionTone = "professional",
    submissionIdOverride?: string,
    existingCaptionOverride?: string,
  ) {
    const targetSubmissionId = submissionIdOverride ?? submissionId;
    if (!targetSubmissionId || (state === "loading" && responseContext === targetSubmissionId)) {
      return null;
    }
    if (cooldownRef.current) clearTimeout(cooldownRef.current);
    cooldownRef.current = null;
    requestRef.current?.controller.abort();
    const request = {
      id: ++requestIdRef.current,
      controller: new AbortController(),
    };
    requestRef.current = request;
    inFlightSubmissionIdRef.current = targetSubmissionId;
    const normalizedPrompt = prompt.trim();
    setLastPrompt(normalizedPrompt);
    setLastTone(tone);
    setNotice(null);
    setResponseContext(targetSubmissionId);
    setState("loading");

    try {
      const response = await suggestCaption(
        targetSubmissionId,
        existingCaptionOverride ?? existingCaption,
        normalizedPrompt,
        tone,
        request.controller.signal,
      );
      if (requestRef.current?.id !== request.id) {
        setState("idle");
        return null;
      }
      const generatedVariant = response.variants[0] ?? null;
      setVariants(response.variants.length > 0 ? response.variants : null);
      setState("idle");
      return generatedVariant;
    } catch (err) {
      if (requestRef.current?.id !== request.id || request.controller.signal.aborted) {
        setState("idle");
        return null;
      }
      if (isRateLimitError(err)) {
        setRateLimitReset(err.rateLimitReset ?? null);
        setState("rate-limited");
        return null;
      }
      const msg = err instanceof Error ? err.message : "";
      const timedOut = msg === "timeout";
      setNotice(
        timedOut
          ? "AI request timed out. Retry or continue editing manually."
          : "AI caption service is unavailable. You can still write captions manually.",
      );
      setState(timedOut ? "error-timeout" : "error-unavailable");
      cooldownRef.current = setTimeout(() => {
        if (requestIdRef.current === request.id) setState("idle");
      }, 5000);
      return null;
    } finally {
      if (requestRef.current?.id === request.id) {
        requestRef.current = null;
        inFlightSubmissionIdRef.current = null;
      }
    }
  }

  function dismissAll() {
    requestIdRef.current += 1;
    requestRef.current?.controller.abort();
    requestRef.current = null;
    if (cooldownRef.current) clearTimeout(cooldownRef.current);
    cooldownRef.current = null;
    setResponseContext(submissionId);
    setVariants(null);
    setState("idle");
    if (submissionId) logCaptionInteraction(submissionId, "dismiss");
  }

  function regenerate() {
    setVariants(null);
    if (submissionId) logCaptionInteraction(submissionId, "re_generate");
    void suggest(lastPrompt, lastTone);
  }

  function logApply(
    tone: CaptionTone,
    action: "use" | "use_then_edited" = "use"
  ) {
    if (submissionId) logCaptionInteraction(submissionId, action, tone);
    setVariants(null);
  }

  function logApplyForSubmission(
    submissionIdOverride: string,
    tone: CaptionTone,
    action: "use" | "use_then_edited" = "use",
  ) {
    logCaptionInteraction(submissionIdOverride, action, tone);
    setVariants(null);
  }

  function logDismissOne(tone: CaptionTone) {
    if (submissionId) logCaptionInteraction(submissionId, "dismiss", tone);
    setVariants((current) => {
      if (!current) return null;
      const next = current.filter((v) => v.tone !== tone);
      return next.length === 0 ? null : next;
    });
  }

  return {
    state: contextMatches ? state : "idle",
    variants: contextMatches ? variants : null,
    rateLimitReset: contextMatches ? rateLimitReset : null,
    canSuggest,
    notice: contextMatches ? notice : null,
    suggest,
    dismissAll,
    regenerate,
    logApply,
    logApplyForSubmission,
    logDismissOne,
  };
}
