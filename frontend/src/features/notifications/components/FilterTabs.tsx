import type { NotificationCounts } from "../hooks/useNotifications";
import type { NotificationFilter } from "../types";
import { FILTER_LABELS, FILTER_ORDER } from "../types";

interface Props {
  activeFilter: NotificationFilter;
  counts: NotificationCounts;
  onChange: (filter: NotificationFilter) => void;
}

export default function FilterTabs({ activeFilter, counts, onChange }: Props) {
  return (
    <div className="notif-filter-tabs">
      {FILTER_ORDER.map((filter) => (
        <button
          key={filter}
          type="button"
          className={`notif-tab${activeFilter === filter ? " active" : ""}`}
          onClick={() => onChange(filter)}
        >
          {FILTER_LABELS[filter]}
          <span className="notif-tab-count">{counts[filter]}</span>
        </button>
      ))}
    </div>
  );
}
