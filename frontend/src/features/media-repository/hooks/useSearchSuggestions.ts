import { useMemo } from "react";
import { useDebouncedValue } from "./useDebouncedValue";
import { buildSuggestions, type Suggestion, type SuggestionCorpus } from "../searchSuggestions";

/**
 * Debounced, ranked related-search suggestions for the media search bar.
 * Pass a memoized `corpus` so the suggestion list only recomputes when the (debounced)
 * query, the underlying data, or the recent-search list actually change.
 */
export function useSearchSuggestions(
  query: string,
  corpus: SuggestionCorpus,
  recents: string[],
  delayMs = 250,
): Suggestion[] {
  const debouncedQuery = useDebouncedValue(query, delayMs);
  return useMemo(
    () => buildSuggestions(debouncedQuery, corpus, recents),
    [debouncedQuery, corpus, recents],
  );
}
