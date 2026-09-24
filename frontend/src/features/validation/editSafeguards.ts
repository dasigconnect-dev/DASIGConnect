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

/** A run of a word-level diff; "gap" stands for unchanged text left out. */
export interface DiffSegment {
  kind: "same" | "del" | "ins" | "gap";
  text: string;
}

/** Longest input (tokens × tokens) diffed word by word; beyond it, whole old vs. new. */
const MAX_DIFF_CELLS = 400_000;

/**
 * Word-level diff of `before` → `after`, keeping only `context` unchanged words
 * on each side of a change and replacing the rest with a gap — so a one-phrase
 * edit in a long caption shows as one short line, not two full copies.
 */
export function wordDiff(before: string, after: string, context = 4): DiffSegment[] {
  const a = before.split(/(\s+)/).filter(Boolean);
  const b = after.split(/(\s+)/).filter(Boolean);
  if (a.length * b.length > MAX_DIFF_CELLS) {
    return [
      { kind: "del", text: before },
      { kind: "ins", text: after },
    ];
  }

  // Longest common subsequence over tokens (words and whitespace runs).
  const cols = b.length + 1;
  const lcs = new Uint32Array((a.length + 1) * cols);
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i * cols + j] = a[i] === b[j]
        ? lcs[(i + 1) * cols + j + 1] + 1
        : Math.max(lcs[(i + 1) * cols + j], lcs[i * cols + j + 1]);
    }
  }

  const raw: DiffSegment[] = [];
  const push = (kind: DiffSegment["kind"], text: string) => {
    const last = raw[raw.length - 1];
    if (last && last.kind === kind) last.text += text;
    else raw.push({ kind, text });
  };
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      push("same", a[i]);
      i++;
      j++;
    } else if (lcs[(i + 1) * cols + j] >= lcs[i * cols + j + 1]) {
      push("del", a[i++]);
    } else {
      push("ins", b[j++]);
    }
  }
  while (i < a.length) push("del", a[i++]);
  while (j < b.length) push("ins", b[j++]);

  // Merge a changed phrase into one removed + one added run: changes separated
  // only by whitespace read as "old phrase → new phrase", not word by word.
  const merged: DiffSegment[] = [];
  for (let k = 0; k < raw.length; k++) {
    if (raw[k].kind === "same") {
      merged.push(raw[k]);
      continue;
    }
    let removed = "";
    let added = "";
    while (k < raw.length) {
      const seg = raw[k];
      if (seg.kind === "del") removed += seg.text;
      else if (seg.kind === "ins") added += seg.text;
      else if (/^\s+$/.test(seg.text) && raw[k + 1] && raw[k + 1].kind !== "same") {
        removed += seg.text;
        added += seg.text;
      } else break;
      k++;
    }
    k--;
    if (removed.trim()) merged.push({ kind: "del", text: removed });
    if (added.trim()) merged.push({ kind: "ins", text: added });
    if (!removed.trim() && removed) merged.push({ kind: "same", text: removed });
  }

  // Trim long unchanged runs down to their edges.
  const out: DiffSegment[] = [];
  merged.forEach((segment, index) => {
    if (segment.kind !== "same") {
      out.push(segment);
      return;
    }
    const tokens = segment.text.split(/(\s+)/).filter(Boolean);
    const words = tokens.filter((t) => !/^\s+$/.test(t)).length;
    const isFirst = index === 0;
    const isLast = index === merged.length - 1;
    const keepHead = isFirst ? 0 : context;
    const keepTail = isLast ? 0 : context;
    if (words <= keepHead + keepTail) {
      out.push(segment);
      return;
    }
    if (keepHead > 0) out.push({ kind: "same", text: takeWords(tokens, keepHead) });
    out.push({ kind: "gap", text: "…" });
    if (keepTail > 0) out.push({ kind: "same", text: takeWords([...tokens].reverse(), keepTail, true) });
  });
  return out;
}

/** The first `count` words of `tokens` (whitespace kept); `reversed` = tokens are in reverse order. */
function takeWords(tokens: string[], count: number, reversed = false): string {
  const kept: string[] = [];
  let words = 0;
  for (const token of tokens) {
    if (!/^\s+$/.test(token)) {
      if (words === count) break;
      words++;
    }
    kept.push(token);
  }
  const text = (reversed ? kept.reverse() : kept).join("");
  return reversed ? text.replace(/^\s+/, " ") : text.replace(/\s+$/, " ");
}
