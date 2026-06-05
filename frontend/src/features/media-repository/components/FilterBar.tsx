import { useEffect, useId, useRef, useState } from "react";
import BrandedSelect from "../../../components/ui/BrandedSelect";
import type { SortOption, ViewMode } from "../types";
import { useSearchSuggestions } from "../hooks/useSearchSuggestions";
import { useRemoteSearchSuggestions } from "../hooks/useRemoteSearchSuggestions";
import { addRecentSearch, clearRecentSearches, getRecentSearches } from "../recentSearches";
import { type Suggestion, type SuggestionCorpus } from "../searchSuggestions";
import type { RemoteSuggestion } from "../../../api/mediaApi";
import SearchSuggestionsDropdown from "./SearchSuggestionsDropdown";

interface AiTagChip {
  label: string;
  count: number;
}

interface FilterBarProps {
  search: string;
  sort: SortOption;
  viewMode: ViewMode;
  activeTags: Set<string>;
  tagChips: AiTagChip[];
  searching: boolean;
  searchActive: boolean;
  onSearchChange: (value: string) => void;
  /** Executes the search. The optional override is the exact query to run (e.g. a picked suggestion). */
  onSearchSubmit: (query?: string) => void;
  onSearchClear: () => void;
  onSortChange: (value: SortOption) => void;
  onViewModeChange: (mode: ViewMode) => void;
  onTagToggle: (tag: string) => void;
  /** Real data the related-search dropdown is drawn from (client-side). Omit to disable. */
  suggestionCorpus?: SuggestionCorpus;
  /** Backend autocomplete fetcher (whole library). Takes precedence over suggestionCorpus. */
  fetchSuggestions?: (query: string, signal?: AbortSignal) => Promise<RemoteSuggestion[]>;
}

const EMPTY_CORPUS: SuggestionCorpus = {};

export default function FilterBar({
  search,
  sort,
  viewMode,
  activeTags,
  tagChips,
  searching,
  searchActive,
  onSearchChange,
  onSearchSubmit,
  onSearchClear,
  onSortChange,
  onViewModeChange,
  onTagToggle,
  suggestionCorpus,
  fetchSuggestions,
}: FilterBarProps) {
  const showClear = searchActive || search.trim().length > 0;

  const suggestionsEnabled = suggestionCorpus !== undefined || fetchSuggestions !== undefined;
  const listboxId = useId();
  const wrapRef = useRef<HTMLFormElement>(null);
  const [recents, setRecents] = useState<string[]>(() => getRecentSearches());
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  // Backend autocomplete (whole library) takes precedence; otherwise rank the local corpus.
  const localSuggestions = useSearchSuggestions(search, suggestionCorpus ?? EMPTY_CORPUS, recents);
  const remoteSuggestions = useRemoteSearchSuggestions(search, fetchSuggestions, recents);
  const suggestions = fetchSuggestions ? remoteSuggestions : localSuggestions;
  const isEmptyQuery = search.trim().length === 0;
  const showRecentHeader = isEmptyQuery && recents.length > 0;
  const dropdownOpen = suggestionsEnabled && open && suggestions.length > 0;
  const safeActive = activeIndex >= 0 && activeIndex < suggestions.length ? activeIndex : -1;

  // Close on outside click.
  useEffect(() => {
    if (!dropdownOpen) return;
    function onDocMouseDown(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
        setActiveIndex(-1);
      }
    }
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [dropdownOpen]);

  function executeSearch(query: string) {
    const term = query.trim();
    if (!term) return;
    setRecents(addRecentSearch(term));
    setOpen(false);
    setActiveIndex(-1);
    onSearchSubmit(term);
  }

  function selectSuggestion(suggestion: Suggestion) {
    onSearchChange(suggestion.text);
    executeSearch(suggestion.text);
  }

  function handleClear() {
    setActiveIndex(-1);
    onSearchClear();
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (!suggestionsEnabled) {
      if (e.key === "Escape" && searchActive) onSearchClear();
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (!open) { setOpen(true); return; }
      setActiveIndex((i) => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, -1));
    } else if (e.key === "Enter") {
      if (dropdownOpen && safeActive >= 0) {
        e.preventDefault();
        selectSuggestion(suggestions[safeActive]);
      }
      // otherwise: let the form submit (executeSearch on typed query) proceed
    } else if (e.key === "Escape") {
      if (open) {
        e.preventDefault();
        setOpen(false);
        setActiveIndex(-1);
      } else if (searchActive) {
        onSearchClear();
      }
    }
  }

  return (
    <div className="med-filter-bar">
      <div className="med-filter-row1">
        <form
          ref={wrapRef}
          className={`med-search-wrap${searchActive ? " is-active" : ""}`}
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            executeSearch(search);
          }}
        >
          <button
            type="submit"
            className="med-search-icon"
            aria-label="Search media"
            disabled={searching || search.trim().length === 0}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </button>
          <input
            type="search"
            className="med-search-input"
            placeholder="Describe what you're looking for — e.g. students holding certificates"
            aria-label="Search media by natural-language description"
            value={search}
            enterKeyHint="search"
            role={suggestionsEnabled ? "combobox" : undefined}
            aria-expanded={suggestionsEnabled ? dropdownOpen : undefined}
            aria-controls={suggestionsEnabled ? listboxId : undefined}
            aria-autocomplete={suggestionsEnabled ? "list" : undefined}
            aria-activedescendant={dropdownOpen && safeActive >= 0 ? `${listboxId}-opt-${safeActive}` : undefined}
            onChange={(e) => {
              onSearchChange(e.target.value);
              if (suggestionsEnabled) { setOpen(true); setActiveIndex(-1); }
            }}
            onFocus={() => { if (suggestionsEnabled) setOpen(true); }}
            onKeyDown={handleKeyDown}
          />
          {searching && <span className="med-search-spinner" role="status" aria-label="Searching" />}
          {showClear && !searching && (
            <button
              type="button"
              className="med-search-clear"
              aria-label="Clear search"
              onClick={handleClear}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}

          {dropdownOpen && (
            <SearchSuggestionsDropdown
              suggestions={suggestions}
              activeIndex={safeActive}
              query={search}
              listboxId={listboxId}
              showRecentHeader={showRecentHeader}
              onHover={setActiveIndex}
              onSelect={selectSuggestion}
              onClearRecent={() => setRecents(clearRecentSearches())}
            />
          )}
        </form>

        <BrandedSelect
          className="med-sort-select"
          value={sort}
          onChange={(value) => onSortChange(value as SortOption)}
          ariaLabel="Sort media assets"
          options={[
            { value: "newest", label: "Newest" },
            { value: "oldest", label: "Oldest" },
            { value: "name", label: "Name A-Z" },
            { value: "size", label: "Largest" },
          ]}
        />
        <div className="med-view-toggle">
          <button
            className={`med-view-btn${viewMode === "grid" ? " active" : ""}`}
            onClick={() => onViewModeChange("grid")}
            title="Grid view"
            type="button"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="7" height="7" />
              <rect x="14" y="3" width="7" height="7" />
              <rect x="14" y="14" width="7" height="7" />
              <rect x="3" y="14" width="7" height="7" />
            </svg>
          </button>
          <button
            className={`med-view-btn${viewMode === "list" ? " active" : ""}`}
            onClick={() => onViewModeChange("list")}
            title="List view"
            type="button"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <line x1="8" y1="6" x2="21" y2="6" />
              <line x1="8" y1="12" x2="21" y2="12" />
              <line x1="8" y1="18" x2="21" y2="18" />
              <line x1="3" y1="6" x2="3.01" y2="6" />
              <line x1="3" y1="12" x2="3.01" y2="12" />
              <line x1="3" y1="18" x2="3.01" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      {tagChips.length > 0 && (
        <div className="med-filter-row2">
          <span className="med-filter-label">AI Tags</span>
          {tagChips.map((chip) => (
            <button
              key={chip.label}
              className={`med-chip${activeTags.has(chip.label) ? " active" : ""}`}
              onClick={() => onTagToggle(chip.label)}
              type="button"
            >
              {chip.label}
              <span className="med-chip-count">{chip.count}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
