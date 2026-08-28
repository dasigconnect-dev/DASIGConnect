import { useMemo } from "react";
import type { StatusBreakdownDto } from "../../../api/analyticsApi";
import { BLUE_GRADIENT_PALETTE, formatNumber } from "../analyticsUtils";

interface Props {
  rows: StatusBreakdownDto[];
}

function formatStatus(status: string) {
  return status.replaceAll("_", " ").replace(/\b\w/g, (l) => l.toUpperCase());
}

export default function StatusDonutChart({ rows }: Props) {
  const total = useMemo(() => rows.reduce((sum, r) => sum + r.count, 0), [rows]);

  const slices = useMemo(() => {
    if (total === 0) return [];
    let currentAngle = 0;

    return rows.map((r, i) => {
      const percent = (r.count / total) * 100;
      const angle = (r.count / total) * 360;
      const startAngle = currentAngle;
      currentAngle += angle;
      const color = BLUE_GRADIENT_PALETTE[i % BLUE_GRADIENT_PALETTE.length];

      return {
        ...r,
        label: formatStatus(r.status),
        percent: Math.round(percent),
        startAngle,
        angle,
        color,
      };
    });
  }, [rows, total]);

  // SVG circle calculation: Radius = 38, Circumference = 2 * Math.PI * 38 ≈ 238.76
  const radius = 38;
  const circumference = 2 * Math.PI * radius;

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">Status Breakdown</h3>
          <p className="analytics-chart-subtitle">Workflow distribution across submission states</p>
        </div>
      </div>

      <div className="analytics-donut-container">
        {total === 0 ? (
          <div className="analytics-empty-state">No submissions in this scope</div>
        ) : (
          <>
            <div className="analytics-donut-svg-wrap">
              <svg viewBox="0 0 100 100" className="analytics-donut-svg">
                {slices.map((slice, i) => {
                  const strokeDasharray = `${(slice.percent / 100) * circumference} ${circumference}`;
                  // Calculate offset from accumulated percentage
                  const prevPercent = slices.slice(0, i).reduce((sum, s) => sum + s.percent, 0);
                  const strokeDashoffset = -((prevPercent / 100) * circumference);

                  return (
                    <circle
                      key={slice.status}
                      cx="50"
                      cy="50"
                      r={radius}
                      fill="transparent"
                      stroke={slice.color}
                      strokeWidth="16"
                      strokeDasharray={strokeDasharray}
                      strokeDashoffset={strokeDashoffset}
                      style={{
                        transform: "rotate(-90deg)",
                        transformOrigin: "50% 50%",
                        transition: "stroke-dasharray 0.5s ease",
                      }}
                    />
                  );
                })}
              </svg>
              <div className="analytics-donut-center">
                <span className="analytics-donut-total">{formatNumber(total)}</span>
                <span className="analytics-donut-sub">Total</span>
              </div>
            </div>

            <div className="analytics-donut-legend">
              {slices.map((slice) => (
                <div className="analytics-donut-legend-item" key={slice.status}>
                  <span className="analytics-donut-dot" style={{ backgroundColor: slice.color }} />
                  <span className="analytics-donut-name">{slice.label}</span>
                  <span className="analytics-donut-val">{slice.percent}%</span>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
