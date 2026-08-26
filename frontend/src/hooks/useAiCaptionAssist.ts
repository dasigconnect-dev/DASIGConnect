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
  suggest: () => void;
  dismissAll: () => void;
  regenerate: () => void;
  logApply: (tone: CaptionTone, action?: "use" | "use_then_edited") => void;
  logDismissOne: (tone: CaptionTone) => void;
}

export function useAiCaptionAssist(
  submissionId: string | null,
  hasImageAssets: boolean,
  existingCaption?: string
): UseAiCaptionAssistReturn {
  const [state, setState] = useState<AiCaptionState>("idle");
  const [variants, setVariants] = useState<CaptionVariant[] | null>(null);
  const [rateLimitReset, setRateLimitReset] = useState<number | null>(null);
  const cooldownRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const canSuggest = !!submissionId && hasImageAssets;

  async function suggest() {
    if (!canSuggest || state === "loading") return;
    if (cooldownRef.current) clearTimeout(cooldownRef.current);
    setState("loading");

    try {
      const response = await suggestCaption(submissionId!, existingCaption);
      setVariants(response.variants);
      setState("idle");
    } catch (err) {
      if (isRateLimitError(err)) {
        setRateLimitReset(err.rateLimitReset ?? null);
        setState("rate-limited");
        return;
      }
      const msg = err instanceof Error ? err.message : "";
      setState(msg === "timeout" ? "error-timeout" : "error-unavailable");
      cooldownRef.current = setTimeout(() => setState("idle"), 5000);
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
    suggest();
  }

  function logApply(
    tone: CaptionTone,
    action: "use" | "use_then_edited" = "use"
  ) {
    if (submissionId) logCaptionInteraction(submissionId, action, tone);
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
    suggest,
    dismissAll,
    regenerate,
    logApply,
    logDismissOne,
  };
}
