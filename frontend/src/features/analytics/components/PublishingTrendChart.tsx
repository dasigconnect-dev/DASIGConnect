import { useMemo } from "react";
import type { KpiMetricDto } from "../../../api/analyticsApi";
import { generateSmoothLinePath } from "../analyticsUtils";

interface Props {
  metric: KpiMetricDto;
  onOpenReport: () => void;
}

export default function PublishingTrendChart({ metric, onOpenReport }: Props) {
  const points = useMemo(() => {
    if (metric.sparkline && metric.sparkline.length >= 2) {
      return metric.sparkline;
    }
    // Fallback demonstration points if sparkline has fewer samples
    return [1, 2, 4, 3, 5, 6, 8];
  }, [metric.sparkline]);

  const width = 580;
  const height = 180;
  const { linePath, areaPath, coords } = useMemo(
    () => generateSmoothLinePath(points, width, height, 24),
    [points],
  );

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">Publishing & Activity Trend</h3>
          <p className="analytics-chart-subtitle">Publication volume trajectory across the selected timeframe</p>
        </div>
        <button type="button" className="analytics-text-btn" onClick={onOpenReport}>
          <span>View Report</span>
          <i className="ti ti-arrow-right" />
        </button>
      </div>

      <div className="analytics-chart-body">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="analytics-trend-svg"
          preserveAspectRatio="none"
        >
          <defs>
            <linearGradient id="trendGradient" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#1877F2" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#1877F2" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* Background grid lines */}
          <line x1="20" y1="40" x2={width - 20} y2="40" stroke="#F1F5F9" strokeDasharray="3 3" />
          <line x1="20" y1="90" x2={width - 20} y2="90" stroke="#F1F5F9" strokeDasharray="3 3" />
          <line x1="20" y1="140" x2={width - 20} y2="140" stroke="#F1F5F9" strokeDasharray="3 3" />

          {/* Area fill */}
          {areaPath && <path d={areaPath} fill="url(#trendGradient)" />}

          {/* Smooth line */}
          {linePath && (
            <path
              d={linePath}
              fill="none"
              stroke="#0C1D3D"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          )}

          {/* Data point markers and labels */}
          {coords.map((pt, i) => (
            <g key={i}>
              <circle
                cx={pt.x}
                cy={pt.y}
                r="4.5"
                fill="#1877F2"
                stroke="#FFFFFF"
                strokeWidth="2"
              />
              <text
                x={pt.x}
                y={pt.y - 10}
                textAnchor="middle"
                fontSize="10.5"
                fontWeight="700"
                fill="#0C1D3D"
              >
                {pt.val}
              </text>
            </g>
          ))}
        </svg>
      </div>

      <div className="analytics-trend-footer">
        <span>Timeline Interval</span>
        <span className="analytics-trend-badge">
          <i className="ti ti-trending-up" /> Active Trajectory
        </span>
      </div>
    </div>
  );
}
