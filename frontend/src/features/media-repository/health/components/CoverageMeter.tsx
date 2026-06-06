export type MeterTone = "good" | "warn" | "danger" | "neutral";

interface CoverageMeterProps {
  /** 0–100. */
  pct: number;
  tone?: MeterTone;
  /** Accessible name, e.g. "Checksum coverage". */
  label: string;
}

/**
 * Accessible horizontal progress bar for percentage health metrics (checksum coverage,
 * metadata completeness). The numeric value is rendered as text so meaning never depends
 * on color alone.
 */
export default function CoverageMeter({ pct, tone = "neutral", label }: CoverageMeterProps) {
  const clamped = Math.max(0, Math.min(100, pct));
  return (
    <div className="rh-meter">
      <div
        className={`rh-meter-track rh-meter--${tone}`}
        role="progressbar"
        aria-label={label}
        aria-valuenow={Math.round(clamped)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span className="rh-meter-fill" style={{ width: `${clamped}%` }} />
      </div>
      <span className="rh-meter-value">{clamped.toFixed(1)}%</span>
    </div>
  );
}
