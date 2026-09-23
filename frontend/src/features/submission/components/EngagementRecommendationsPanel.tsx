import { useId, useState } from "react";
import type { EngagementRecommendations } from "../../../api/submissionApi";
import "./EngagementRecommendationsPanel.css";

/** Weekday / date / time parts for a slot tile, in the viewer's locale. */
function slotParts(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return { weekday: "", day: value, time: "" };
  return {
    weekday: new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(date),
    day: new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date),
    time: new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(date),
  };
}

/**
 * Suggested publishing times as a row of calendar-style tiles (weekday,
 * date, time; a "Top pick" badge on the best-scoring slot when Page history
 * separates them; a relative
 * engagement bar when the suggestions come from real Page history). Picking one fills the
 * Date / Time fields below it. The explanation (where the suggestions come
 * from, and that any valid custom time is fine) sits behind the ⓘ toggle,
 * and the "why" line only appears for the selected tile — so the step reads
 * as decisions, with the guidance one tap away.
 */
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
  const infoId = useId();

  if (loading) {
    return (
      <div className="sub-engagement-panel sub-engagement-loading" aria-live="polite">
        <i className="ti ti-loader-2 sub-spin" aria-hidden="true" />
        <span>Finding the best times to post…</span>
      </div>
    );
  }
  if (!recommendations || recommendations.slots.length === 0) return null;

  const historical = recommendations.source === "HISTORICAL";
  const selectedTime = selectedAt ? new Date(selectedAt).getTime() : NaN;
  const selectedSlot = recommendations.slots.find(
    (slot) => new Date(slot.scheduledAt).getTime() === selectedTime,
  );
  const scores = recommendations.slots.map((slot) => slot.score);
  const maxScore = Math.max(...scores, 0);
  // "Top pick" only when real Page history actually separates the slots; with
  // general guidance every slot scores the same, so a badge would be arbitrary.
  const topSlot =
    historical && maxScore > Math.min(...scores)
      ? recommendations.slots.find((slot) => slot.score === maxScore)
      : undefined;

  return (
    <div className="sub-engagement-panel">
      <div className="sub-engagement-heading">
        <span className="sub-engagement-title">
          <i className="ti ti-chart-line" aria-hidden="true" /> Suggested times
        </span>
        <span className={`sub-engagement-source${historical ? " is-historical" : ""}`}>
          {historical ? `Based on ${recommendations.sampleSize} posts` : "General guidance"}
        </span>
        <button
          type="button"
          className="sub-engagement-info-btn"
          aria-expanded={infoOpen}
          aria-controls={infoId}
          aria-label="About suggested times"
          onClick={() => setInfoOpen((open) => !open)}
        >
          <i className={`ti ${infoOpen ? "ti-x" : "ti-info-circle"}`} aria-hidden="true" />
        </button>
      </div>

      {infoOpen && (
        <div id={infoId} className="sub-engagement-info">
          <p>
            {recommendations.notice ||
              (historical
                ? `Picked from when your Page's last ${recommendations.sampleSize} Facebook posts got the most reactions, comments, and shares.`
                : "Based on general weekday-evening guidance. Suggestions improve as more Facebook history is collected.")}
          </p>
          <p>
            Tap a suggestion to fill in the date and time, or choose any valid time yourself.
            {historical && " The bar under each time shows how its engagement compares."}
          </p>
        </div>
      )}

      <div className="sub-engagement-tiles">
        {recommendations.slots.map((slot) => {
          const selected = slot === selectedSlot;
          const top = slot === topSlot;
          const { weekday, day, time } = slotParts(slot.scheduledAt);
          const strength = maxScore > 0 ? Math.max(0.15, slot.score / maxScore) : 0;
          return (
            <button
              type="button"
              key={slot.scheduledAt}
              className={`sub-engagement-tile${selected ? " is-selected" : ""}${top ? " is-top" : ""}`}
              aria-pressed={selected}
              aria-label={`${weekday} ${day}, ${time}${top ? ", top pick" : ""}. ${slot.windowLabel}${
                slot.warnings[0] ? `. Note: ${slot.warnings[0]}` : ""
              }`}
              title={[slot.windowLabel, ...slot.warnings].join(" — ")}
              onClick={() => onSelect(slot.scheduledAt)}
            >
              {top && (
                <span className="sub-engagement-tile-badge" aria-hidden="true">
                  <i className="ti ti-star-filled" /> Top pick
                </span>
              )}
              {slot.warnings.length > 0 && (
                <i className="ti ti-alert-triangle sub-engagement-tile-warn" aria-hidden="true" />
              )}
              <span className="sub-engagement-tile-weekday" aria-hidden="true">{weekday}</span>
              <span className="sub-engagement-tile-day" aria-hidden="true">{day}</span>
              <span className="sub-engagement-tile-time" aria-hidden="true">{time}</span>
              {historical && (
                <span className="sub-engagement-tile-meter" aria-hidden="true">
                  <span style={{ width: `${Math.round(strength * 100)}%` }} />
                </span>
              )}
              {selected && (
                <i className="ti ti-circle-check-filled sub-engagement-tile-check" aria-hidden="true" />
              )}
            </button>
          );
        })}
      </div>

      {selectedSlot && (
        <p className="sub-engagement-why" aria-live="polite">
          <i className="ti ti-trending-up" aria-hidden="true" />
          <span>
            {selectedSlot.windowLabel}
            {selectedSlot.warnings[0] && (
              <em className="sub-engagement-why-warn">{selectedSlot.warnings[0]}</em>
            )}
          </span>
        </p>
      )}
    </div>
  );
}
