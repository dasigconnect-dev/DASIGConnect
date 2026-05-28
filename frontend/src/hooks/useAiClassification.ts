import { useRef, useState } from "react";
import {
  getClassificationSuggestions,
  logAiInteraction,
  type ClassificationSuggestions,
} from "../api/aiApi";

export type AiClassificationState = "idle" | "loading" | "ready" | "error";

export interface UseAiClassificationReturn {
  state: AiClassificationState;
  suggestions: ClassificationSuggestions | null;
  /** True when there is any media (local or saved) — button should be visible but may be disabled. */
  canShow: boolean;
  /** True when backend can be called (submission saved + has saved assets). */
  canFetch: boolean;
  fetch: () => void;
  acceptCategory: (category: string) => void;
  acceptTags: (tags: string[]) => void;
  dismiss: () => void;
}

/**
 * Manages on-demand AI category and tag suggestions for a submission (UC-3.3).
 *
 * Suggestions are derived from AI classification already stored on the submission's
 * attached media assets — no external call happens at fetch time beyond the backend.
 */
export function useAiClassification(
  submissionId: string | null,
  hasSavedAssets: boolean,
  hasAnyMedia: boolean,
  onAcceptCategory: (category: string) => void,
  onAcceptTags: (tags: string[]) => void
): UseAiClassificationReturn {
  const [state, setState] = useState<AiClassificationState>("idle");
  const [suggestions, setSuggestions] = useState<ClassificationSuggestions | null>(null);
  const cooldownRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const canShow = hasAnyMedia;
  const canFetch = !!submissionId && hasSavedAssets;

  async function fetch() {
    if (!canFetch || state === "loading") return;
    if (cooldownRef.current) clearTimeout(cooldownRef.current);
    setState("loading");
    setSuggestions(null);

    try {
      const data = await getClassificationSuggestions(submissionId!);
      setSuggestions(data);
      setState("ready");
    } catch {
      setState("error");
      cooldownRef.current = setTimeout(() => setState("idle"), 5000);
    }
  }

  function acceptCategory(category: string) {
    onAcceptCategory(category);
    if (submissionId) logAiInteraction(submissionId, "tag_classification", "accepted");
    setSuggestions(null);
    setState("idle");
  }

  function acceptTags(tags: string[]) {
    onAcceptTags(tags);
    if (submissionId) logAiInteraction(submissionId, "tag_classification", "accepted");
    setSuggestions(null);
    setState("idle");
  }

  function dismiss() {
    if (submissionId) logAiInteraction(submissionId, "tag_classification", "dismissed");
    setSuggestions(null);
    setState("idle");
  }

  return { state, suggestions, canShow, canFetch, fetch, acceptCategory, acceptTags, dismiss };
}
