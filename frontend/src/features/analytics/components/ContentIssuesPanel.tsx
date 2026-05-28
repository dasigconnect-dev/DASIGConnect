import { useEffect, useRef, useState } from "react";
import type { ContentIssueDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

const COLLAPSED_ROWS = 3;

export default function ContentIssuesPanel({ rows }: Readonly<{ rows: ContentIssueDto[] }>) {
  const [expanded, setExpanded] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const hasMore = rows.length > COLLAPSED_ROWS;
  const visibleRows = expanded ? rows : rows.slice(0, COLLAPSED_ROWS);
  const hiddenCount = rows.length - COLLAPSED_ROWS;

  // Set collapsed height to fit exactly COLLAPSED_ROWS items, then expand on toggle.
  useEffect(() => {
    const el = wrapRef.current;
    if (!el) return;
    if (expanded) {
      el.style.maxHeight = `${el.scrollHeight}px`;
    } else {
      // Measure height of first N items + their gaps.
      const items = el.querySelectorAll<HTMLElement>(":scope > div");
      let h = 0;
      for (let i = 0; i < Math.min(COLLAPSED_ROWS, items.length); i++) {
        h += items[i].getBoundingClientRect().height;
      }
      // Add gap between items (6px each gap, COLLAPSED_ROWS - 1 gaps).
      h += 6 * (Math.min(COLLAPSED_ROWS, items.length) - 1);
      el.style.maxHeight = `${h}px`;
    }
  }, [expanded, rows]);

  // Auto-scroll into the newly revealed content after expansion.
  useEffect(() => {
    if (!expanded) return;
    const timer = window.setTimeout(() => {
      wrapRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }, 50);
    return () => window.clearTimeout(timer);
  }, [expanded]);

  function handleExpand() {
    setExpanded(true);
  }

  function handleCollapse() {
    setExpanded(false);
  }

  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Missing Requirements</h2>
          <p>Most common fields missing from submitted content</p>
        </div>
        {hasMore && !expanded && (
          <button
            type="button"
            className="analytics-view-btn"
            onClick={handleExpand}
            aria-label="Expand missing requirements list"
          >
            {"View"}
            <i className="ti ti-arrow-up-right" aria-hidden="true" />
          </button>
        )}
      </div>

      {rows.length === 0 ? (
        <span className="analytics-muted" style={{ display: "block", marginTop: 14 }}>
          No repeated missing requirements in this period.
        </span>
      ) : (
        <>
          <div
            ref={wrapRef}
            className="analytics-simple-list analytics-expandable-wrap"
            style={{ overflow: "hidden", transition: "max-height 0.32s cubic-bezier(0.4,0,0.2,1)" }}
          >
            {visibleRows.map((row) => (
              <div key={row.issue}>
                <span>{row.issue}</span>
                <strong>{formatNumber(row.count)}</strong>
              </div>
            ))}
          </div>

          {hasMore && (
            <div className="analytics-expand-footer">
              {expanded ? (
                <button
                  type="button"
                  className="analytics-expand-link"
                  onClick={handleCollapse}
                >
                  <i className="ti ti-chevron-up" aria-hidden="true" />
                  Show less
                </button>
              ) : (
                <button
                  type="button"
                  className="analytics-expand-link"
                  onClick={handleExpand}
                >
                  <i className="ti ti-chevron-down" aria-hidden="true" />
                  {`Show ${hiddenCount} more`}
                </button>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}
