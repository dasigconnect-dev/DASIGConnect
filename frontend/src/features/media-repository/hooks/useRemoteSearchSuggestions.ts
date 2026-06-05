import { useEffect, useMemo, useState } from "react";
import { useDebouncedValue } from "./useDebouncedValue";
import { mergeServerSuggestions, typeLabelFor, type Suggestion, type SuggestionType } from "../searchSuggestions";
import type { RemoteSuggestion } from "../../../api/mediaApi";

const KNOWN_TYPES: SuggestionType[] = ["tag", "file", "uploader", "collection", "folder"];

function toSuggestion(raw: RemoteSuggestion): Suggestion {
  const type = (KNOWN_TYPES as string[]).includes(raw.type) ? (raw.type as SuggestionType) : "tag";
  return {
    id: `${type}:${raw.text.toLowerCase()}`,
    text: raw.text,
    label: raw.text,
    type,
    typeLabel: typeLabelFor(type),
  };
}

/**
 * Debounced backend autocomplete for the media search bar (UC-4.5). Fetches whole-library
 * suggestions, then layers matching recent searches and smart phrases on top. The `fetcher`
 * should be memoized by the caller so the effect doesn't refire every render.
 */
export function useRemoteSearchSuggestions(
  query: string,
  fetcher: ((q: string, signal?: AbortSignal) => Promise<RemoteSuggestion[]>) | undefined,
  recents: string[],
  delayMs = 250,
): Suggestion[] {
  const debounced = useDebouncedValue(query, delayMs);
  const [remote, setRemote] = useState<Suggestion[]>([]);

  useEffect(() => {
    const q = debounced.trim();
    if (!q || !fetcher) return;
    const controller = new AbortController();
    let cancelled = false;
    fetcher(q, controller.signal)
      .then((list) => { if (!cancelled) setRemote(list.map(toSuggestion)); })
      .catch(() => { if (!cancelled) setRemote([]); });
    return () => { cancelled = true; controller.abort(); };
  }, [debounced, fetcher]);

  return useMemo(() => mergeServerSuggestions(debounced, remote, recents), [debounced, remote, recents]);
}
