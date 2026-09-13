import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

export function SectionHead({
  icon,
  tone,
  title,
  subtitle,
  revisionComment,
  isDone,
  onToggleDone,
}: {
  icon: string;
  tone: string;
  title: string;
  subtitle: string;
  revisionComment?: string | null;
  isDone?: boolean;
  onToggleDone?: () => void;
}) {
  const [commentOpen, setCommentOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!commentOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setCommentOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [commentOpen]);

  return (
    <div className="sub-section-head">
      <div className="sub-section-label">
        <div className={`sub-section-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <div className="sub-section-title">{title}</div>
            {revisionComment && (
              <div className="sub-field-comment-wrap" ref={popoverRef}>
                <button
                  type="button"
                  className={`sub-field-comment-btn ${isDone ? "is-done" : ""} ${commentOpen ? "is-active" : ""}`}
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    setCommentOpen(!commentOpen);
                  }}
                  title={isDone ? "Revision resolved (Click to view note)" : "Click to view reviewer feedback"}
                  aria-label="View reviewer feedback"
                >
                  <i className={isDone ? "ti ti-check" : "ti ti-message-2"} aria-hidden="true" />
                  <span>{isDone ? "Done" : "Revision Note"}</span>
                </button>

                {commentOpen && (
                  <div
                    className="sub-field-comment-popover"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="sub-field-comment-popover-head">
                      <div className="sub-field-comment-popover-title">
                        <i className="ti ti-message-dots" />
                        <span>Reviewer Feedback</span>
                      </div>
                      <button
                        type="button"
                        className="sub-field-comment-popover-close"
                        onClick={() => setCommentOpen(false)}
                        aria-label="Close comment"
                      >
                        <i className="ti ti-x" />
                      </button>
                    </div>
                    <p className="sub-field-comment-popover-text">{revisionComment}</p>
                    {onToggleDone && (
                      <div className="sub-field-comment-popover-footer">
                        <button
                          type="button"
                          className={`sub-field-comment-ack-btn ${isDone ? "is-done" : ""}`}
                          onClick={(e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            onToggleDone();
                          }}
                        >
                          <i className="ti ti-check" />
                          <span>{isDone ? "Marked as Done" : "Mark as Done"}</span>
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
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
  revisionComment,
  isPulsing,
  isDone,
  onToggleDone,
}: {
  label: string;
  count?: string;
  tone?: string;
  action?: ReactNode;
  tooltip?: string;
  children: ReactNode;
  revisionComment?: string | null;
  isPulsing?: boolean;
  isDone?: boolean;
  onToggleDone?: () => void;
}) {
  const [commentOpen, setCommentOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!commentOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setCommentOpen(false);
      }
    };
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [commentOpen]);

  return (
    <div className={`sub-fgroup ${isPulsing ? "sub-field-pulse" : ""}`}>
      <div className="sub-flabel">
        <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          <span>{label}</span>
          {tooltip && (
            <i
              className="ti ti-info-circle"
              title={tooltip}
              style={{ color: "#9ca3af", cursor: "help", fontSize: "15px" }}
            />
          )}
          {revisionComment && (
            <div className="sub-field-comment-wrap" ref={popoverRef}>
              <button
                type="button"
                className={`sub-field-comment-btn ${isDone ? "is-done" : ""} ${commentOpen ? "is-active" : ""}`}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  setCommentOpen(!commentOpen);
                }}
                title={isDone ? "Revision resolved (Click to view note)" : "Click to view reviewer feedback"}
                aria-label="View reviewer feedback for this field"
              >
                <i className={isDone ? "ti ti-check" : "ti ti-message-2"} aria-hidden="true" />
                <span>{isDone ? "Done" : "Revision Note"}</span>
              </button>

              {commentOpen && (
                <div
                  className="sub-field-comment-popover"
                  onClick={(e) => e.stopPropagation()}
                >
                  <div className="sub-field-comment-popover-head">
                    <div className="sub-field-comment-popover-title">
                      <i className="ti ti-message-dots" />
                      <span>Reviewer Feedback</span>
                    </div>
                    <button
                      type="button"
                      className="sub-field-comment-popover-close"
                      onClick={() => setCommentOpen(false)}
                      aria-label="Close comment"
                    >
                      <i className="ti ti-x" />
                    </button>
                  </div>
                  <p className="sub-field-comment-popover-text">{revisionComment}</p>
                  {onToggleDone && (
                    <div className="sub-field-comment-popover-footer">
                      <button
                        type="button"
                        className={`sub-field-comment-ack-btn ${isDone ? "is-done" : ""}`}
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          onToggleDone();
                        }}
                      >
                        <i className="ti ti-check" />
                        <span>{isDone ? "Marked as Done" : "Mark as Done"}</span>
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </span>
        <span className="sub-flabel-right">
          {count && (
            <span className={`sub-flabel-count ${tone || ""}`}>{count}</span>
          )}
          {action}
        </span>
      </div>
      {children}
    </div>
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
  return createPortal(
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
    </div>,
    document.body,
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

  return createPortal(
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
    </div>,
    document.body,
  );
}
