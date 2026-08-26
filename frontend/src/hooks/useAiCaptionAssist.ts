import { useRef, useState } from "react";
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
  const cooldownRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const canSuggest = !!submissionId;

  async function suggest(
    prompt = "",
    tone: CaptionTone = "professional",
    submissionIdOverride?: string,
    existingCaptionOverride?: string,
  ) {
    const targetSubmissionId = submissionIdOverride ?? submissionId;
    if (!targetSubmissionId || state === "loading") return null;
    if (cooldownRef.current) clearTimeout(cooldownRef.current);
    const normalizedPrompt = prompt.trim();
    setLastPrompt(normalizedPrompt);
    setLastTone(tone);
    setNotice(null);
    setState("loading");

    try {
      const response = await suggestCaption(
        targetSubmissionId,
        existingCaptionOverride ?? existingCaption,
        normalizedPrompt,
        tone,
      );
      const generatedVariant = response.variants[0] ?? null;
      setVariants(response.variants.length > 0 ? response.variants : null);
      setState("idle");
      return generatedVariant;
    } catch (err) {
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
      cooldownRef.current = setTimeout(() => setState("idle"), 5000);
      return null;
    }
  }

  function dismissAll() {
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
    state,
    variants,
    rateLimitReset,
    canSuggest,
    notice,
    suggest,
    dismissAll,
    regenerate,
    logApply,
    logApplyForSubmission,
    logDismissOne,
  };
}
