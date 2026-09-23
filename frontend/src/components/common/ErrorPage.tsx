import { useEffect, useRef, useState, type ReactNode } from "react";
import dasigLogo from "../../assets/dasigconnect-logo.png";
import "../../styles/error-page.css";

export type ErrorPageVariant = "not-found" | "forbidden" | "crash";

export interface ErrorPageAction {
  label: string;
  icon?: string;
  onClick: () => void;
  tone?: "primary" | "ghost";
}

interface ErrorPageProps {
  variant: ErrorPageVariant;
  title: string;
  message: ReactNode;
  actions: ErrorPageAction[];
  /**
   * `standalone` fills the viewport with the logo on top (logged out, or when
   * the app shell itself crashed); `in-shell` sits inside the dashboard content
   * area so the sidebar and top bar stay usable.
   */
  layout?: "standalone" | "in-shell";
  /** Technical detail, shown collapsed — for crash reports. */
  details?: string;
}

const VARIANT_META: Record<ErrorPageVariant, { code: string; label: string; icon: string }> = {
  "not-found": { code: "404", label: "Page not found", icon: "ti-map-search" },
  forbidden: { code: "403", label: "Access restricted", icon: "ti-lock" },
  crash: { code: "500", label: "Unexpected error", icon: "ti-plug-connected-x" },
};

/** Shared layout for the app's error states (404, 403, crash). */
export default function ErrorPage({
  variant,
  title,
  message,
  actions,
  layout = "standalone",
  details,
}: ErrorPageProps) {
  const meta = VARIANT_META[variant];
  const headingRef = useRef<HTMLHeadingElement | null>(null);
  const [copied, setCopied] = useState(false);

  // Move focus to the heading so screen-reader and keyboard users land on the explanation.
  useEffect(() => {
    headingRef.current?.focus();
  }, []);

  async function copyDetails() {
    if (!details) return;
    try {
      await navigator.clipboard.writeText(details);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard can be blocked; the text is still selectable in the panel.
    }
  }

  return (
    <main className={`errp errp-${layout} errp-${variant}`} role={variant === "crash" ? "alert" : undefined}>
      {layout === "standalone" && (
        <a className="errp-logo" href="/" aria-label="DASIGConnect home">
          <img src={dasigLogo} alt="DASIGConnect" />
        </a>
      )}

      <div className="errp-body">
        <SignalMark icon={meta.icon} code={meta.code} />

        <div className="errp-copy">
          <span className="errp-eyebrow">
            Error {meta.code} · {meta.label}
          </span>
          <h1 className="errp-title" ref={headingRef} tabIndex={-1}>
            {title}
          </h1>
          <div className="errp-message">{message}</div>

          <div className="errp-actions">
            {actions.map((action) => (
              <button
                key={action.label}
                type="button"
                className={`errp-btn ${action.tone === "ghost" ? "is-ghost" : "is-primary"}`}
                onClick={action.onClick}
              >
                {action.icon && <i className={`ti ${action.icon}`} aria-hidden />}
                {action.label}
              </button>
            ))}
          </div>

          {details && (
            <details className="errp-details">
              <summary>Technical details</summary>
              <pre>{details}</pre>
              <button type="button" className="errp-copy-btn" onClick={() => void copyDetails()}>
                <i className={`ti ${copied ? "ti-check" : "ti-copy"}`} aria-hidden />
                {copied ? "Copied" : "Copy details"}
              </button>
            </details>
          )}
        </div>
      </div>
    </main>
  );
}

/**
 * The DASIGConnect "signal" mark (the logo's arcs and nodes) with a broken arc
 * and the variant's icon at its centre — the connection that didn't go through.
 */
function SignalMark({ icon, code }: { icon: string; code: string }) {
  return (
    <div className="errp-mark" aria-hidden>
      <svg viewBox="0 0 220 220" className="errp-mark-svg">
        {/* outer blue arc, broken at the top right */}
        <path className="errp-arc errp-arc-blue" d="M 176 58 A 92 92 0 1 1 138 23" />
        <circle className="errp-node errp-node-blue" cx="138" cy="23" r="7" />
        <circle className="errp-node errp-node-blue" cx="36" cy="160" r="9" />
        {/* the dislodged segment */}
        <path className="errp-arc errp-arc-loose" d="M 166 40 A 92 92 0 0 1 190 72" />
        <circle className="errp-node errp-node-loose" cx="190" cy="72" r="5" />
        {/* inner gold arc */}
        <path className="errp-arc errp-arc-gold" d="M 52 110 A 58 58 0 1 1 110 168" />
        <circle className="errp-node errp-node-gold" cx="52" cy="110" r="7" />
        <circle className="errp-node errp-node-gold" cx="110" cy="168" r="5" />
      </svg>
      <div className="errp-mark-core">
        <i className={`ti ${icon}`} />
        <span>{code}</span>
      </div>
    </div>
  );
}
