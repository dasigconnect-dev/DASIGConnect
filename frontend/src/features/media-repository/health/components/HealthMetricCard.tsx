import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export type MetricTone = "default" | "good" | "warn" | "danger";

interface HealthMetricCardProps {
  label: string;
  value: string | number;
  caption?: string;
  tone?: MetricTone;
  /** When set, the whole card drills into the affected Media Library asset set. */
  to?: string;
  toLabel?: string;
  /** Optional extra content, e.g. a CoverageMeter. */
  children?: ReactNode;
}

/**
 * One Repository Health metric tile. Drillable tiles render as a router Link so each count
 * navigates to the affected asset set (UC-4.12 Phase 7C acceptance). Tone adds a colored
 * accent, but the caption always carries the meaning in text (never color alone).
 */
export default function HealthMetricCard({
  label,
  value,
  caption,
  tone = "default",
  to,
  toLabel,
  children,
}: HealthMetricCardProps) {
  const body = (
    <>
      <span className="rh-card-label">{label}</span>
      <span className="rh-card-value">{value}</span>
      {children}
      {caption && <span className="rh-card-caption">{caption}</span>}
      {to && <span className="rh-card-drill">{toLabel ?? "View assets"} &rarr;</span>}
    </>
  );

  if (to) {
    return (
      <Link className={`rh-card rh-card--${tone} rh-card--link`} to={to}>
        {body}
      </Link>
    );
  }
  return <div className={`rh-card rh-card--${tone}`}>{body}</div>;
}
