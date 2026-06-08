import { sparklinePath } from "../../analyticsUtils";

interface MetricTileProps {
  label: string;
  value: string;
  /** Period-over-period change; green when up, red when down. Omit when unavailable. */
  delta?: number | null;
  sublabel?: string;
  /** Optional daily series for a tiny trend line under the value. */
  series?: number[];
  /** Highlight the tile (e.g. the active/primary metric, like Facebook's selected card). */
  active?: boolean;
  onClick?: () => void;
}

export default function MetricTile({ label, value, delta, sublabel, series, active, onClick }: MetricTileProps) {
  const interactive = Boolean(onClick);
  const deltaTone = delta == null ? null : delta > 0 ? "up" : delta < 0 ? "down" : "flat";
  return (
    <div
      className={`ins-tile${active ? " active" : ""}${interactive ? " interactive" : ""}`}
      role={interactive ? "button" : undefined}
      tabIndex={interactive ? 0 : undefined}
      onClick={onClick}
      onKeyDown={interactive ? (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick?.(); } } : undefined}
    >
      <div className="ins-tile-top">
        <span className="ins-tile-value">{value}</span>
        {deltaTone && (
          <span className={`ins-tile-delta ${deltaTone}`}>
            {deltaTone === "up" ? "▲" : deltaTone === "down" ? "▼" : "–"} {Math.abs(delta as number).toFixed(0)}%
          </span>
        )}
      </div>
      <span className="ins-tile-label">{label}</span>
      {sublabel && <span className="ins-tile-sub">{sublabel}</span>}
      {series && series.length > 1 && (
        <svg className="ins-tile-spark" viewBox="0 0 160 36" preserveAspectRatio="none" aria-hidden="true">
          <path d={sparklinePath(series, 160, 36)} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
    </div>
  );
}
