import { useEffect, type ReactNode } from "react";

export function SectionHead({
  icon,
  tone,
  title,
  subtitle,
}: {
  icon: string;
  tone: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="sub-section-head">
      <div className="sub-section-label">
        <div className={`sub-section-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <div>
          <div className="sub-section-title">{title}</div>
          <div className="sub-section-subtitle">{subtitle}</div>
        </div>
      </div>
    </div>
  );
}

export function Field({
  label,
  count,
  tone,
  action,
  tooltip,
  children,
}: {
  label: string;
  count?: string;
  tone?: string;
  action?: ReactNode;
  tooltip?: string;
  children: ReactNode;
}) {
  return (
    <label className="sub-fgroup">
      <span className="sub-flabel">
        <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          {label}
          {tooltip && (
            <i
              className="ti ti-info-circle"
              title={tooltip}
              style={{ color: "#9ca3af", cursor: "help", fontSize: "15px" }}
            />
          )}
        </span>
        <span className="sub-flabel-right">
          {count && (
            <span className={`sub-flabel-count ${tone || ""}`}>{count}</span>
          )}
          {action}
        </span>
      </span>
      {children}
    </label>
  );
}

export function GuardSection({
  title,
  icon,
  meta,
  defaultOpen = false,
  children,
}: {
  title: string;
  icon: string;
  meta?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  return (
    <details className="sub-guard-section" open={defaultOpen}>
      <summary className="sub-guard-section-title">
        <span>
          <i className={`ti ${icon}`}></i> {title}
        </span>
        <span className="sub-guard-section-meta">
          {meta && <small>{meta}</small>}
          <i className="ti ti-chevron-down" aria-hidden="true"></i>
        </span>
      </summary>
      <div className="sub-guard-section-body">{children}</div>
    </details>
  );
}

export function CheckItem({
  pass,
  idle,
  title,
  sub,
  onClick,
}: {
  pass: boolean;
  idle?: boolean;
  title: string;
  sub: string;
  onClick?: () => void;
}) {
  const content = (
    <>
      <div
        className={`sub-check-icon ${idle ? "idle" : pass ? "pass" : "warn"}`}
      >
        <i
          className={`ti ${idle ? "ti-clock" : pass ? "ti-check" : "ti-alert-triangle"}`}
        ></i>
      </div>
      <div>
        <div className="sub-check-title">{title}</div>
        <div className="sub-check-sub">{sub}</div>
      </div>
    </>
  );

  if (onClick) {
    return (
      <button className="sub-check-item sub-check-action" type="button" onClick={onClick}>
        {content}
      </button>
    );
  }

  return (
    <div className="sub-check-item">
      {content}
    </div>
  );
}

export function QueueLoadingState() {
  return (
    <div
      className="sub-queue-loading"
      role="status"
      aria-label="Loading submissions"
    >
      <div className="dc-dot-triangle-container">
        <div className="dc-dot-triangle-label">
          <span>Loading</span>
          <span className="dc-dot-triangle-label-dots">
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
          </span>
        </div>
        <div className="loader-stage" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div className="loader-dots" />
        </div>
      </div>
    </div>
  );
}

export function ReadinessSkeleton() {
  return (
    <div className="sub-readiness-skeleton" aria-label="Loading readiness">
      <span className="sub-skel-ring sub-shimmer"></span>
      <span className="sub-skel-line wide sub-shimmer"></span>
      <span className="sub-skel-line sub-shimmer"></span>
    </div>
  );
}

export function QueueState({
  icon,
  title,
  description,
}: {
  icon: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="sub-queue-state">
      <i className={`ti ${icon}`}></i>
      <span>{title}</span>
      {description && <small>{description}</small>}
    </div>
  );
}

export function ReadinessRing({ score }: { score: number }) {
  const circumference = 175.9;
  const offset = circumference - (score / 100) * circumference;
  return (
    <div className="sub-score-ring">
      <svg viewBox="0 0 64 64">
        <circle className="sub-score-bg" cx="32" cy="32" r="28" />
        <circle
          className="sub-score-fill"
          cx="32"
          cy="32"
          r="28"
          style={{
            strokeDashoffset: offset,
            stroke:
              score >= 80 ? "#16A34A" : score >= 60 ? "#D97706" : "#DC2626",
          }}
        />
      </svg>
      <div
        className="sub-score-num"
        style={{
          color: score >= 80 ? "#16A34A" : score >= 60 ? "#D97706" : "#DC2626",
        }}
      >
        {score}
      </div>
    </div>
  );
}

export function ConfirmModal({
  icon,
  tone = "info",
  title,
  description,
  cancelLabel,
  confirmLabel,
  loading = false,
  disabled = false,
  onCancel,
  onConfirm,
}: {
  icon: string;
  tone?: "info" | "success" | "danger";
  title: string;
  description: string;
  cancelLabel?: string;
  confirmLabel: string;
  loading?: boolean;
  disabled?: boolean;
  onCancel?: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="sub-modal-overlay"
      onClick={disabled ? undefined : onCancel || onConfirm}
    >
      <div className="sub-modal" onClick={(event) => event.stopPropagation()}>
        <div className={`sub-modal-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <div className="sub-modal-title">{title}</div>
        <div className="sub-modal-desc">{description}</div>
        <div className="sub-modal-actions">
          {onCancel && (
            <button
              className="sub-modal-btn cancel"
              type="button"
              onClick={onCancel}
              disabled={disabled}
            >
              {cancelLabel}
            </button>
          )}
          <button
            className={`sub-modal-btn ${tone}`}
            type="button"
            onClick={onConfirm}
            disabled={disabled}
            aria-busy={loading}
          >
            {loading && <i className="ti ti-loader-2 sub-spin"></i>}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export function DraftExitModal({
  saving,
  disabled,
  onSave,
  onDiscard,
  onContinue,
}: {
  saving: boolean;
  disabled: boolean;
  onSave: () => void;
  onDiscard: () => void;
  onContinue: () => void;
}) {
  useEffect(() => {
    if (disabled) return undefined;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.stopPropagation();
      onContinue();
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [disabled, onContinue]);

  return (
    <div
      className="sub-modal-overlay"
      onClick={disabled ? undefined : onContinue}
    >
      <div
        className="sub-modal sub-modal--draft-exit"
        role="dialog"
        aria-modal="true"
        aria-labelledby="draft-exit-title"
        aria-describedby="draft-exit-description"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sub-modal-icon info">
          <i className="ti ti-notes"></i>
        </div>
        <div className="sub-modal-title" id="draft-exit-title">Save this post as a draft?</div>
        <div className="sub-modal-desc" id="draft-exit-description">
          You have unsaved content. Save it as a draft, discard your changes, or
          continue editing.
        </div>
        <div className="sub-modal-actions sub-modal-actions--three">
          <button
            className="sub-modal-btn sub-modal-btn--continue"
            type="button"
            onClick={onContinue}
            disabled={disabled}
          >
            Continue Editing
          </button>
          <button
            className="sub-modal-btn sub-modal-btn--discard"
            type="button"
            onClick={onDiscard}
            disabled={disabled}
          >
            Discard
          </button>
          <button
            className="sub-modal-btn sub-modal-btn--save"
            type="button"
            onClick={onSave}
            disabled={disabled}
            aria-busy={saving}
          >
            {saving && <i className="ti ti-loader-2 sub-spin"></i>}
            {saving ? "Saving..." : "Save Draft"}
          </button>
        </div>
      </div>
    </div>
  );
}
