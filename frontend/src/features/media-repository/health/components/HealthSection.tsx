import type { ReactNode } from "react";

interface HealthSectionProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
}

/** Titled group of health metric tiles. */
export default function HealthSection({ title, subtitle, children }: HealthSectionProps) {
  return (
    <section className="rh-section">
      <header className="rh-section-head">
        <h2 className="rh-section-title">{title}</h2>
        {subtitle && <p className="rh-section-subtitle">{subtitle}</p>}
      </header>
      <div className="rh-grid">{children}</div>
    </section>
  );
}
