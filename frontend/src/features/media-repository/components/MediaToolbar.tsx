import BrandedSelect from "../../../components/ui/BrandedSelect";
import type { SortOption, ViewMode } from "../types";

interface TagChip {
  label: string;
  count: number;
}

interface MediaToolbarProps {
  isAdmin: boolean;
  institutions: { id: string; name: string }[];
  selectedInstitutionId: string | null;
  onInstitutionChange: (id: string | null) => void;

  search: string;
  onSearchChange: (value: string) => void;
  semantic: boolean;
  onSemanticToggle: () => void;
  onSemanticSearch: () => void;
  semanticBusy: boolean;

  sort: SortOption;
  onSortChange: (value: SortOption) => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;

  activeTags: Set<string>;
  tagChips: TagChip[];
  onTagToggle: (tag: string) => void;
}

export default function MediaToolbar({
  isAdmin,
  institutions,
  selectedInstitutionId,
  onInstitutionChange,
  search,
  onSearchChange,
  semantic,
  onSemanticToggle,
  onSemanticSearch,
  semanticBusy,
  sort,
  onSortChange,
  viewMode,
  onViewModeChange,
  activeTags,
  tagChips,
  onTagToggle,
}: MediaToolbarProps) {
  return (
    <div className="med-filter-bar">
      <div className="med-filter-row1">
        <div className={`med-search-wrap${semantic ? " semantic" : ""}`}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            className="med-search-input"
            placeholder={
              semantic
                ? "Describe what you're looking for, then press Enter…"
                : "Search filename, folder, tag, or description…"
            }
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && semantic) onSemanticSearch();
            }}
          />
          <button
            type="button"
            className={`med-semantic-toggle${semantic ? " on" : ""}`}
            aria-pressed={semantic}
            title={semantic ? "Semantic search on — press Enter to run" : "Turn on semantic (meaning-based) search"}
            onClick={onSemanticToggle}
          >
            {semanticBusy ? (
              <span className="med-semantic-spinner" aria-hidden="true" />
            ) : (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1" />
              </svg>
            )}
            <span>Semantic</span>
          </button>
        </div>

        {isAdmin && (
          <BrandedSelect
            className="med-inst-select"
            value={selectedInstitutionId ?? ""}
            onChange={(value) => onInstitutionChange(value || null)}
            ariaLabel="Filter by institution"
            options={[
              { value: "", label: "All institutions" },
              ...institutions.map((inst) => ({ value: inst.id, label: inst.name })),
            ]}
          />
        )}

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
          <span className="med-filter-label">Tags</span>
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
