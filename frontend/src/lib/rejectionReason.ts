import type { RejectionReasonCode } from "../api/validationApi";

/** Human labels for the reviewer's rejection reason codes (BR-VAL-03). */
export const REJECTION_REASON_LABELS: Record<RejectionReasonCode, string> = {
  INCOMPLETE_CONTENT: "Incomplete content",
  INAPPROPRIATE_CONTENT: "Inappropriate content",
  WRONG_FORMAT: "Wrong format",
  DUPLICATE_EVENT: "Duplicate event",
  WRONG_INSTITUTION: "Wrong institution",
  OTHER: "Other",
};

export interface ParsedRejectionReason {
  /** Label for the reason code, or null when the text doesn't start with a known code. */
  label: string | null;
  /** The reviewer's free-text note, or null when they didn't write one. */
  note: string | null;
}

/**
 * The backend stores a rejection as "CODE: note", or just "CODE" when the
 * reviewer wrote no note (ValidationService.buildRejectionReason). Splits that
 * into a readable label and the note. Anything else is treated as a plain note.
 */
export function parseRejectionReason(raw: string | null | undefined): ParsedRejectionReason {
  const text = raw?.trim() ?? "";
  if (!text) return { label: null, note: null };
  const match = /^([A-Z_]+)(?::\s*([\s\S]*))?$/.exec(text);
  const code = match?.[1] as RejectionReasonCode | undefined;
  if (!match || !code || !(code in REJECTION_REASON_LABELS)) {
    return { label: null, note: text };
  }
  const note = match[2]?.trim() || null;
  return { label: REJECTION_REASON_LABELS[code], note };
}

/** One-line form, e.g. "Wrong format — the poster needs to be landscape". */
export function formatRejectionReason(raw: string | null | undefined): string {
  const { label, note } = parseRejectionReason(raw);
  return [label, note].filter(Boolean).join(" — ");
}
