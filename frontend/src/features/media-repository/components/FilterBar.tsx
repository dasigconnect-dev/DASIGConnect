import BrandedSelect from "../../../components/ui/BrandedSelect";
import type { SortOption, ViewMode } from "../types";

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
  onSearchSubmit: () => void;
  onSearchClear: () => void;
  onSortChange: (value: SortOption) => void;
  onViewModeChange: (mode: ViewMode) => void;
  onTagToggle: (tag: string) => void;
}

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
}: FilterBarProps) {
  const showClear = searchActive || search.trim().length > 0;
  return (
    <div className="med-filter-bar">
      <div className="med-filter-row1">
        <form
          className={`med-search-wrap${searchActive ? " is-active" : ""}`}
          role="search"
          onSubmit={(e) => {
            e.preventDefault();
            onSearchSubmit();
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
            onChange={(e) => onSearchChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Escape" && searchActive) onSearchClear();
            }}
          />
          {searching && <span className="med-search-spinner" role="status" aria-label="Searching" />}
          {showClear && !searching && (
            <button
              type="button"
              className="med-search-clear"
              aria-label="Clear search"
              onClick={onSearchClear}
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
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
