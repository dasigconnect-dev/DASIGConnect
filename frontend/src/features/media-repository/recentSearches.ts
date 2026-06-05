/** Session-persistent recent search history for the media search bar (localStorage-backed). */

const STORAGE_KEY = "dasigconnect:recent-searches";
const MAX_RECENTS = 8;

export function getRecentSearches(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** Adds a query to the front (most-recent-first), de-duped case-insensitively. Returns the new list. */
export function addRecentSearch(query: string): string[] {
  const q = query.trim();
  if (!q) return getRecentSearches();
  const existing = getRecentSearches().filter((r) => r.toLowerCase() !== q.toLowerCase());
  const next = [q, ...existing].slice(0, MAX_RECENTS);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    /* ignore quota / unavailable storage */
  }
  return next;
}

export function clearRecentSearches(): string[] {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
  return [];
}
