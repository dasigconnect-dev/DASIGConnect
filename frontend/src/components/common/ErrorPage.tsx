import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
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
  /** e.g. the address that wasn't found, shown as a small code chip. */
  path?: string;
  /** Technical detail, shown collapsed — for crash reports. */
  details?: string;
}

const VARIANT_META: Record<ErrorPageVariant, { code: string; label: string; icon: string }> = {
  "not-found": { code: "404", label: "Page not found", icon: "ti-map-search" },
  forbidden: { code: "403", label: "Access restricted", icon: "ti-lock" },
  crash: { code: "500", label: "Unexpected error", icon: "ti-plug-connected-x" },
};

/**
 * Full-screen error state (404 / 403 / crash). Portaled to <body> and fixed
 * over the viewport, so it's full-screen even when rendered from inside the
 * dashboard shell (e.g. by ProtectedRoute) — a transformed ancestor there would
 * otherwise become the containing block for position: fixed and trap it.
 */
export default function ErrorPage({ variant, title, message, actions, path, details }: ErrorPageProps) {
  const meta = VARIANT_META[variant];
  const headingRef = useRef<HTMLHeadingElement | null>(null);
  const [copied, setCopied] = useState(false);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const panelId = useId();

  // Land keyboard and screen-reader users on the explanation.
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

  return createPortal(
    <main
      className={`errp errp-${variant}${details ? " has-details" : ""}${detailsOpen ? " is-details-open" : ""}`}
      role={variant === "crash" ? "alert" : undefined}
    >
      <header className="errp-top">
        <a className="errp-logo" href="/" aria-label="DASIGConnect home">
          <img src={dasigLogo} alt="DASIGConnect" />
        </a>
      </header>

      <section className="errp-hero">
        <div className="errp-main">
        <ErrorCode code={meta.code} icon={meta.icon} />

        <p className="errp-eyebrow">{meta.label}</p>
        <h1 className="errp-title" ref={headingRef} tabIndex={-1}>
          {title}
        </h1>
        <div className="errp-message">{message}</div>

        {path && (
          <code className="errp-path" title={path}>
            {path}
          </code>
        )}

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
          <button
            type="button"
            className="errp-details-toggle"
            aria-expanded={detailsOpen}
            aria-controls={panelId}
            onClick={() => setDetailsOpen((open) => !open)}
          >
            <i className="ti ti-code" aria-hidden />
            {detailsOpen ? "Hide technical details" : "Show technical details"}
            <i className="ti ti-chevron-right errp-details-chevron" aria-hidden />
          </button>
        )}
        </div>

        {/* Slides in beside the error (below it on narrow screens) when opened. */}
        {details && (
          <aside className="errp-panel" id={panelId} aria-label="Technical details" inert={!detailsOpen} aria-hidden={!detailsOpen}>
            <div className="errp-panel-inner">
              <div className="errp-panel-head">
                <span>Technical details</span>
                <button type="button" className="errp-copy-btn" onClick={() => void copyDetails()}>
                  <i className={`ti ${copied ? "ti-check" : "ti-copy"}`} aria-hidden />
                  {copied ? "Copied" : "Copy"}
                </button>
              </div>
              <pre>{details}</pre>
            </div>
          </aside>
        )}
      </section>

      <footer className="errp-foot">© {new Date().getFullYear()} DASIGConnect</footer>
    </main>,
    document.body,
  );
}

/**
 * The status code in large display type, with its first "0" drawn as the
 * DASIGConnect signal ring — broken, with the variant's icon inside.
 */
function ErrorCode({ code, icon }: { code: string; icon: string }) {
  const ringIndex = code.indexOf("0");
  return (
    <div className="errp-code" role="img" aria-label={`Error ${code}`}>
      {code.split("").map((char, index) =>
        index === ringIndex ? (
          <SignalRing key={index} icon={icon} />
        ) : (
          <span key={index} className="errp-digit" aria-hidden>
            {char}
          </span>
        ),
      )}
    </div>
  );
}

function SignalRing({ icon }: { icon: string }) {
  return (
    <span className="errp-ring" aria-hidden>
      <svg viewBox="0 0 120 150">
        {/* outer blue arc — broken at the upper right: the connection that didn't go through */}
        <path className="errp-ring-outer" d="M 85.0 17.8 A 50 66 0 1 0 107.0 52.4" />
        <circle className="errp-ring-node-blue" cx="85.0" cy="17.8" r="6" />
        <circle className="errp-ring-node-blue" cx="107.0" cy="52.4" r="6" />
        {/* inner gold arc */}
        <path className="errp-ring-inner" d="M 34.4 80.9 A 26 34 0 1 1 68.9 106.9" />
        <circle className="errp-ring-node-gold" cx="34.4" cy="80.9" r="5" />
      </svg>
      <i className={`ti ${icon}`} />
    </span>
  );
}
