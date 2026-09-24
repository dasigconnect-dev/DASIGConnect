import { useCallback, useState } from "react";
import { useToast } from "../context/ToastContext";
import { logProofreadFixApplied, proofreadText, type ProofreadIssue } from "../api/aiApi";
import { applyProofreadFix } from "../lib/proofread";

export type ProofreadState = "idle" | "loading" | "done" | "error";

/**
 * On-demand caption writing check (composer + review editor). Suggestions
 * only: `apply` returns the fixed text for the caller to set, and records the
 * applied fix for AI Feature Adoption.
 */
export function useProofread(submissionId: string | null | undefined) {
  const [state, setState] = useState<ProofreadState>("idle");
  const [issues, setIssues] = useState<ProofreadIssue[]>([]);
  const toast = useToast();

  const check = useCallback(
    async (text: string) => {
      if (!text.trim()) return;
      setState("loading");
      try {
        setIssues(await proofreadText(text, undefined, undefined, submissionId));
        setState("done");
      } catch (error) {
        setState("error");
        toast.error(
          error instanceof Error && error.message === "rate-limit"
            ? "Writing check limit reached for this hour."
            : "Couldn't check the writing right now.",
        );
      }
    },
    [submissionId, toast],
  );

  const dismiss = useCallback((issue: ProofreadIssue) => {
    setIssues((prev) => prev.filter((i) => i !== issue));
  }, []);

  const apply = useCallback(
    (text: string, issue: ProofreadIssue) => {
      logProofreadFixApplied(submissionId);
      return applyProofreadFix(text, issue);
    },
    [submissionId],
  );

  const reset = useCallback(() => {
    setState("idle");
    setIssues([]);
  }, []);

  return { state, issues, check, dismiss, apply, reset };
}
