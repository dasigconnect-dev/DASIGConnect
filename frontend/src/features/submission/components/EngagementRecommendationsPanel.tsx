import { useEffect, useId, useState } from "react";
import type { EngagementRecommendations } from "../../../api/submissionApi";
import "./EngagementRecommendationsPanel.css";

function slotTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en-PH", {
    hour: "numeric",
    minute: "2-digit",
    timeZone: "Asia/Manila",
  }).format(date);
}

const ROWS_PER_PAGE = 4;

function useAvailabilityColumns() {
  const getColumns = () => {
    if (window.matchMedia("(max-width: 420px)").matches) return 2;
    if (window.matchMedia("(max-width: 720px)").matches) return 3;
    return 4;
  };
  const [columns, setColumns] = useState(getColumns);

  useEffect(() => {
    const mobile = window.matchMedia("(max-width: 420px)");
    const tablet = window.matchMedia("(max-width: 720px)");
    const updateColumns = () => setColumns(getColumns());
    mobile.addEventListener("change", updateColumns);
    tablet.addEventListener("change", updateColumns);
    return () => {
      mobile.removeEventListener("change", updateColumns);
      tablet.removeEventListener("change", updateColumns);
    };
  }, []);

  return columns;
}

export function EngagementRecommendationsPanel({
  loading,
  recommendations,
  selectedAt,
  onSelect,
}: {
  loading: boolean;
  recommendations: EngagementRecommendations | null;
  selectedAt?: string;
  onSelect: (scheduledAt: string) => void;
}) {
  const [infoOpen, setInfoOpen] = useState(false);
  const [pageIndex, setPageIndex] = useState(0);
  const infoId = useId();
  const columns = useAvailabilityColumns();
  const pageSize = columns * ROWS_PER_PAGE;
  const slotsCacheKey = recommendations?.slots
    .map((slot) => `${slot.scheduledAt}:${slot.score}`)
    .join("|") ?? "";
  const bestSlotIndex = recommendations?.slots.reduce(
    (bestIndex, slot, index, slots) => slot.score > slots[bestIndex].score ? index : bestIndex,
    0,
  ) ?? 0;

  useEffect(() => {
    setPageIndex(Math.floor(bestSlotIndex / pageSize));
  }, [bestSlotIndex, slotsCacheKey, pageSize]);

  if (loading) {
    return (
      <div className="sub-engagement-panel sub-engagement-loading" aria-live="polite">
        <i className="ti ti-loader-2 sub-spin" aria-hidden="true" />
        <span>Checking available times…</span>
      </div>
    );
  }
  if (!recommendations) return null;

  const selectedTime = selectedAt ? new Date(selectedAt).getTime() : NaN;
  const selectedSlot = recommendations.slots.find(
    (slot) => new Date(slot.scheduledAt).getTime() === selectedTime,
  );
  const bestSlot = recommendations.slots[bestSlotIndex];
  const totalPages = Math.max(1, Math.ceil(recommendations.slots.length / pageSize));
  const currentPage = Math.min(pageIndex, totalPages - 1);
  const visibleSlots = recommendations.slots.slice(
    currentPage * pageSize,
    (currentPage + 1) * pageSize,
  );

  return (
    <div className="sub-engagement-panel">
      <div className="sub-engagement-heading">
        <span className="sub-engagement-title">
          <i className="ti ti-calendar-clock" aria-hidden="true" /> Available times
        </span>
        <button
          type="button"
          className="sub-engagement-info-btn"
          aria-expanded={infoOpen}
          aria-controls={infoId}
          aria-label="About available times"
          onClick={() => setInfoOpen((open) => !open)}
        >
          <i className={`ti ${infoOpen ? "ti-x" : "ti-info-circle"}`} aria-hidden="true" />
        </button>
      </div>

      {infoOpen && (
        <div id={infoId} className="sub-engagement-info">
          <p>{recommendations.notice || "Open calendar times for the selected date."}</p>
          <p>Occupied times and any spacing conflicts required by the current guard rails are removed.</p>
          <p>The highlighted time is the closest available slot to Philippine Facebook engagement benchmarks. Your Page Insights should replace this baseline when enough audience data is available.</p>
        </div>
      )}

      {recommendations.slots.length === 0 ? (
        <div className="sub-engagement-empty" role="status">
          <i className="ti ti-calendar-off" aria-hidden="true" />
          <span>No available times for this date.</span>
        </div>
      ) : (
        <div className="sub-engagement-tiles">
          {visibleSlots.map((slot) => {
            const selected = slot === selectedSlot;
            const best = slot === bestSlot;
            const time = slotTime(slot.scheduledAt);
            return (
              <button
                type="button"
                key={slot.scheduledAt}
                className={`sub-engagement-tile${best ? " is-top" : ""}${selected ? " is-selected" : ""}`}
                aria-pressed={selected}
                aria-label={`${time}, available${best ? ", best available time" : ""}${selected ? ", selected" : ""}`}
                title={best ? slot.windowLabel : "Available calendar time"}
                onClick={() => onSelect(slot.scheduledAt)}
              >
                {best && (
                  <span className="sub-engagement-tile-badge">
                    <i className="ti ti-star-filled" aria-hidden="true" /> Best available
                  </span>
                )}
                <span className="sub-engagement-tile-time">{time}</span>
                <span className="sub-engagement-tile-status">Available</span>
                {selected && (
                  <i className="ti ti-circle-check-filled sub-engagement-tile-check" aria-hidden="true" />
                )}
              </button>
            );
          })}
        </div>
      )}

      {totalPages > 1 && (
        <nav className="sub-engagement-pagination" aria-label="Available time pages">
          <button
            type="button"
            className="sub-engagement-page-btn"
            disabled={currentPage === 0}
            aria-label="Previous available times page"
            onClick={() => setPageIndex((page) => Math.max(0, page - 1))}
          >
            <i className="ti ti-chevron-left" aria-hidden="true" />
          </button>
          <span className="sub-engagement-page-status" aria-live="polite">
            {currentPage + 1} / {totalPages}
          </span>
          <button
            type="button"
            className="sub-engagement-page-btn"
            disabled={currentPage >= totalPages - 1}
            aria-label="Next available times page"
            onClick={() => setPageIndex((page) => Math.min(totalPages - 1, page + 1))}
          >
            <i className="ti ti-chevron-right" aria-hidden="true" />
          </button>
        </nav>
      )}

      {selectedSlot && (
        <p className="sub-engagement-why" aria-live="polite">
          <i className="ti ti-circle-check" aria-hidden="true" />
          <span>
            {slotTime(selectedSlot.scheduledAt)} is available for the selected date.
            {selectedSlot === bestSlot ? " It is also the benchmark-based best available time." : ""}
          </span>
        </p>
      )}
    </div>
  );
}
