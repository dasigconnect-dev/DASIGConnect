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
  networkView: boolean;
  isAdmin: boolean;
  activeTags: Set<string>;
  tagChips: AiTagChip[];
  onSearchChange: (value: string) => void;
  onSortChange: (value: SortOption) => void;
  onViewModeChange: (mode: ViewMode) => void;
  onNetworkViewToggle: () => void;
  onTagToggle: (tag: string) => void;
}

export default function FilterBar({
  search,
  sort,
  viewMode,
  networkView,
  isAdmin,
  activeTags,
  tagChips,
  onSearchChange,
  onSortChange,
  onViewModeChange,
  onNetworkViewToggle,
  onTagToggle,
}: FilterBarProps) {
  return (
    <div className="med-filter-bar">
      <div className="med-filter-row1">
        <div className="med-search-wrap">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            className="med-search-input"
            placeholder="Search by filename, AI tag, event name, or uploader…"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>

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

        {isAdmin && (
          <button
            className="med-network-toggle"
            onClick={onNetworkViewToggle}
            title="Toggle Network View"
            type="button"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#92400E" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="2" y1="12" x2="22" y2="12" />
              <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
            </svg>
            <span className="med-network-toggle-label">Network View</span>
            <div className={`med-toggle-switch${networkView ? " on" : ""}`}>
              <div className="med-toggle-knob" />
            </div>
          </button>
        )}
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
