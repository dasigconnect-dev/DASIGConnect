import type { RejectionReasonCode } from "../api/validationApi";

/**
 * The reasons a reviewer can pick when rejecting (BR-VAL-03), in display
 * order. Only problems a revision can't fix — missing or fixable content goes
 * back to the contributor with Request Revision instead.
 */
export const REJECTION_REASONS: ReadonlyArray<{
  code: RejectionReasonCode;
  label: string;
  description: string;
}> = [
  {
    code: "INAPPROPRIATE_CONTENT",
    label: "Inappropriate content",
    description: "Offensive, misleading, or against DOST and DASIG guidelines.",
  },
  {
    code: "OUT_OF_SCOPE",
    label: "Not a DASIG activity",
    description: "Unrelated to DASIG's programs or the network's mission.",
  },
  {
    code: "DUPLICATE_EVENT",
    label: "Duplicate",
    description: "This event was already posted or is already in the queue.",
  },
  {
    code: "NO_LONGER_RELEVANT",
    label: "No longer timely",
    description: "Posting it now would be outdated or misleading.",
  },
  {
    code: "RIGHTS_OR_PRIVACY",
    label: "Rights or privacy issue",
    description: "Media used without permission, or people shown without consent.",
  },
  {
    code: "WRONG_INSTITUTION",
    label: "Belongs to another institution",
    description: "The institution that ran the event should submit it.",
  },
  {
    code: "OTHER",
    label: "Other",
    description: "Explain the reason in the note below.",
  },
];

/**
 * Label for every code a stored rejection can carry — the current reasons plus
 * the retired INCOMPLETE_CONTENT / WRONG_FORMAT (now Request Revision cases),
 * so rejections recorded before the change still read properly.
 */
export const REJECTION_REASON_LABELS: Record<string, string> = {
  ...Object.fromEntries(REJECTION_REASONS.map((reason) => [reason.code, reason.label])),
  INCOMPLETE_CONTENT: "Incomplete content",
  WRONG_FORMAT: "Wrong format",
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
  const code = match?.[1];
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
