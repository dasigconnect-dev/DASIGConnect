import type { ProofreadIssue } from "../api/aiApi";

/** Replaces the first occurrence of the issue's excerpt with its suggestion. */
export function applyProofreadFix(text: string, issue: ProofreadIssue): string {
  const at = text.indexOf(issue.excerpt);
  if (at < 0 || !issue.suggestion) return text;
  return text.slice(0, at) + issue.suggestion + text.slice(at + issue.excerpt.length);
}
