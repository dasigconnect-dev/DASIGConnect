/**
 * Google-style related-search suggestion engine for the media search bar.
 *
 * Pure, client-side ranking over real system data (AI tags, filenames, uploaders,
 * collection/folder names) plus the user's recent searches. Smart phrases are appended
 * last and only ever produce real natural-language queries the hybrid search endpoint
 * already understands — never fake or random results.
 */

export type SuggestionType = "recent" | "tag" | "keyword" | "file" | "uploader" | "collection" | "folder" | "phrase";

export interface Suggestion {
  /** Stable key for React + de-duplication. */
  id: string;
  /** The query string applied to the search input when selected. */
  text: string;
  /** Display text (defaults to `text`). */
  label: string;
  type: SuggestionType;
  /** Short type label shown on the right of the row. */
  typeLabel?: string;
}

/** Real data the suggestions are drawn from. Each list is deduplicated by the caller. */
export interface SuggestionCorpus {
  /** AI tags / categories detected on assets. */
  tags?: string[];
  /** Asset filenames. */
  filenames?: string[];
  /** Uploader names or emails. */
  uploaders?: string[];
  /** Collection and/or storage-folder names. */
  collections?: string[];
}

export const MAX_SUGGESTIONS = 8;

const EMPTY_CORPUS: SuggestionCorpus = {};

function norm(value: string): string {
  return value.trim().toLowerCase();
}

/** 0 = exact, 1 = starts-with, 2 = contains, null = no match. */
function matchKind(value: string, q: string): 0 | 1 | 2 | null {
  const v = norm(value);
  if (!v) return null;
  if (v === q) return 0;
  if (v.startsWith(q)) return 1;
  if (v.includes(q)) return 2;
  return null;
}

// Lower rank sorts first. Tiers encode the requested priority:
// exact tag/collection → recent exact → filename starts-with → tag starts-with →
// uploader → contains matches → smart phrases (appended after sorting).
const RANK: Record<SuggestionType, [exact: number, starts: number, contains: number]> = {
  tag: [0, 11, 20],
  collection: [1, 12, 21],
  folder: [1, 12, 21],
  recent: [2, 13, 22],
  keyword: [3, 13, 22],
  file: [4, 10, 23],
  uploader: [5, 14, 24],
  phrase: [99, 99, 99],
};

const TYPE_LABEL: Record<SuggestionType, string> = {
  recent: "Recent",
  tag: "Tag",
  keyword: "AI",
  file: "File",
  uploader: "Uploader",
  collection: "Collection",
  folder: "Folder",
  phrase: "Smart",
};

/**
 * Build the ranked, de-duplicated suggestion list for the current query.
 * With an empty query, returns the most recent searches so a focused-but-empty
 * input still offers a useful dropdown.
 */
export function buildSuggestions(
  rawQuery: string,
  corpus: SuggestionCorpus = EMPTY_CORPUS,
  recents: string[] = [],
): Suggestion[] {
  const q = norm(rawQuery);

  if (!q) {
    return recents.slice(0, MAX_SUGGESTIONS).map((r) => ({
      id: `recent:${norm(r)}`,
      text: r,
      label: r,
      type: "recent",
      typeLabel: "Recent",
    }));
  }

  const seen = new Set<string>();
  const scored: Array<{ s: Suggestion; rank: number }> = [];

  const consider = (value: string, type: SuggestionType, typeLabel: string) => {
    const kind = matchKind(value, q);
    if (kind === null) return;
    const key = norm(value);
    if (seen.has(key)) return; // de-dupe across sources; first (highest-priority) wins
    seen.add(key);
    scored.push({
      s: { id: `${type}:${key}`, text: value, label: value, type, typeLabel },
      rank: RANK[type][kind],
    });
  };

  // Order of iteration only affects ties; the rank tiers above drive the real ordering.
  for (const tag of corpus.tags ?? []) consider(tag, "tag", "Tag");
  for (const col of corpus.collections ?? []) consider(col, "collection", "Collection");
  for (const rec of recents) consider(rec, "recent", "Recent");
  for (const name of corpus.filenames ?? []) consider(name, "file", "File");
  for (const up of corpus.uploaders ?? []) consider(up, "uploader", "Uploader");

  scored.sort((a, b) => a.rank - b.rank);
  const results = scored.map((entry) => entry.s).slice(0, MAX_SUGGESTIONS);

  appendSmartPhrases(results, rawQuery, seen);
  return results;
}

/**
 * Composes the dropdown when suggestions come from the backend autocomplete endpoint
 * (whole-library, not just the loaded page): matching recent searches first (user intent),
 * then the server-ranked suggestions, then smart phrases to fill — all de-duplicated.
 */
export function mergeServerSuggestions(
  rawQuery: string,
  serverSuggestions: Suggestion[],
  recents: string[] = [],
): Suggestion[] {
  const q = norm(rawQuery);
  if (!q) {
    return recents.slice(0, MAX_SUGGESTIONS).map((r) => ({
      id: `recent:${norm(r)}`,
      text: r,
      label: r,
      type: "recent",
      typeLabel: "Recent",
    }));
  }

  const seen = new Set<string>();
  const results: Suggestion[] = [];
  const push = (s: Suggestion) => {
    const key = norm(s.text);
    if (!key || seen.has(key)) return;
    seen.add(key);
    results.push(s);
  };

  for (const r of recents) {
    if (results.length >= MAX_SUGGESTIONS) break;
    if (norm(r).includes(q)) push({ id: `recent:${norm(r)}`, text: r, label: r, type: "recent", typeLabel: "Recent" });
  }
  for (const s of serverSuggestions) {
    if (results.length >= MAX_SUGGESTIONS) break;
    push(s);
  }
  appendSmartPhrases(results, rawQuery, seen);
  return results.slice(0, MAX_SUGGESTIONS);
}

export function typeLabelFor(type: SuggestionType): string {
  return TYPE_LABEL[type] ?? "";
}

// Smart phrases: only to fill remaining slots. Each is a real NL query the hybrid
// search endpoint handles (semantic + temporal parsing), not a fabricated result.
function appendSmartPhrases(results: Suggestion[], rawQuery: string, seen: Set<string>) {
  if (results.length >= MAX_SUGGESTIONS) return;
  const base = rawQuery.trim();
  if (!base) return;
  const phrases = [`${base} photos`, `${base} event`, `${base} uploaded this month`];
  for (const phrase of phrases) {
    if (results.length >= MAX_SUGGESTIONS) break;
    const key = norm(phrase);
    if (seen.has(key)) continue;
    seen.add(key);
    results.push({ id: `phrase:${key}`, text: phrase, label: phrase, type: "phrase", typeLabel: "Smart" });
  }
}
