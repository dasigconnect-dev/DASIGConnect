import type { ProofreadIssue } from "../../api/aiApi";
import type { ValidationLog } from "../../api/validationApi";

/** Review-cycle boundaries: a new cycle starts after any of these. */
const TERMINAL_ACTIONS = new Set(["approved", "needs_revision", "rejected"]);

/** Fields a reviewer can restore to what the contributor submitted. */
export type RestorableField = "eventTitle" | "eventDate" | "caption" | "scheduledAt";

export type ContributorOriginals = Partial<Record<RestorableField, string>>;

/**
 * What the contributor submitted, for fields a reviewer has already changed and
 * saved this review cycle: the earliest `from` value in the cycle's `edited`
 * rows (the backend stores display strings — `LocalDate`/`Instant` toString).
 * A field missing here was never changed this cycle, so its current value is
 * still the contributor's.
 */
export function contributorOriginals(log: ValidationLog[]): ContributorOriginals {
  let start = 0;
  log.forEach((entry, index) => {
    if (TERMINAL_ACTIONS.has(entry.action)) start = index + 1;
  });
  const sorted = log
    .slice(start)
    .filter((entry) => entry.action === "edited" && entry.editDiff)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));

  const originals: ContributorOriginals = {};
  for (const entry of sorted) {
    let diff: Record<string, { from?: unknown }>;
    try {
      diff = JSON.parse(entry.editDiff as string);
    } catch {
      continue;
    }
    for (const field of ["eventTitle", "eventDate", "caption", "scheduledAt"] as const) {
      if (originals[field] === undefined && diff[field] && "from" in diff[field]) {
        originals[field] = String(diff[field].from ?? "");
      }
    }
  }
  return originals;
}

/** An ISO instant as the local date/time pair the edit form's inputs use. */
export function isoToLocalDateTime(iso: string): { date: string; time: string } {
  const d = new Date(iso);
  if (!iso || Number.isNaN(d.getTime())) return { date: "", time: "" };
  const pad = (n: number) => String(n).padStart(2, "0");
  return {
    date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`,
  };
}

/** Replaces the first occurrence of the issue's excerpt with its suggestion. */
export function applyProofreadFix(text: string, issue: ProofreadIssue): string {
  const at = text.indexOf(issue.excerpt);
  if (at < 0 || !issue.suggestion) return text;
  return text.slice(0, at) + issue.suggestion + text.slice(at + issue.excerpt.length);
}
