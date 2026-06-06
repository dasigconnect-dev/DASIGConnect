import { Search, Tag, Image as ImageIcon, User, Images, FolderClosed, Clock, Sparkles, WandSparkles, AlignLeft } from "lucide-react";
import type { Suggestion, SuggestionType } from "../searchSuggestions";

interface SearchSuggestionsDropdownProps {
  suggestions: Suggestion[];
  activeIndex: number;
  query: string;
  /** id of the listbox, wired to the input's aria-controls/aria-activedescendant. */
  listboxId: string;
  showRecentHeader?: boolean;
  onHover: (index: number) => void;
  onSelect: (suggestion: Suggestion) => void;
  onClearRecent?: () => void;
}

const ICONS: Record<SuggestionType, typeof Search> = {
  recent: Clock,
  tag: Tag,
  keyword: Sparkles,
  description: AlignLeft,
  file: ImageIcon,
  uploader: User,
  collection: Images,
  folder: FolderClosed,
  phrase: WandSparkles,
};

/** Renders the matched portion of a suggestion in bold (case-insensitive). */
function highlight(label: string, query: string) {
  const q = query.trim();
  if (!q) return label;
  const idx = label.toLowerCase().indexOf(q.toLowerCase());
  if (idx === -1) return label;
  return (
    <>
      {label.slice(0, idx)}
      <strong>{label.slice(idx, idx + q.length)}</strong>
      {label.slice(idx + q.length)}
    </>
  );
}

/**
 * Google-style related-search dropdown for the media search bar. Presentational only —
 * the owning input manages open state, keyboard navigation, and selection.
 */
export default function SearchSuggestionsDropdown({
  suggestions,
  activeIndex,
  query,
  listboxId,
  showRecentHeader = false,
  onHover,
  onSelect,
  onClearRecent,
}: SearchSuggestionsDropdownProps) {
  if (suggestions.length === 0) return null;

  return (
    <div className="med-suggest" role="presentation">
      {showRecentHeader && (
        <div className="med-suggest-head">
          <span>Recent searches</span>
          {onClearRecent && (
            <button type="button" className="med-suggest-clear" onMouseDown={(e) => { e.preventDefault(); onClearRecent(); }}>
              Clear
            </button>
          )}
        </div>
      )}
      <ul className="med-suggest-list" role="listbox" id={listboxId} aria-label="Search suggestions">
        {suggestions.map((s, index) => {
          const Icon = ICONS[s.type] ?? Search;
          const active = index === activeIndex;
          return (
            <li
              key={s.id}
              id={`${listboxId}-opt-${index}`}
              role="option"
              aria-selected={active}
              className={`med-suggest-item${active ? " active" : ""}`}
              // onMouseDown (not onClick) so selection fires before the input blur closes the list.
              onMouseDown={(e) => { e.preventDefault(); onSelect(s); }}
              onMouseEnter={() => onHover(index)}
            >
              <span className="med-suggest-icon" aria-hidden="true">
                <Icon size={15} />
              </span>
              <span className="med-suggest-text">{highlight(s.label, query)}</span>
              {s.typeLabel && <span className={`med-suggest-tag med-suggest-tag-${s.type}`}>{s.typeLabel}</span>}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
