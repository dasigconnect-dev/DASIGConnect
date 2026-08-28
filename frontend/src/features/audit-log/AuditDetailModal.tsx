import { useEffect } from "react";
import { createPortal } from "react-dom";
import type { AuditLogEntry } from "../../api/auditLogApi";

interface Props {
  entry: AuditLogEntry | null;
  onClose: () => void;
}

function formatDate(iso: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });
}

function categoryBadgeClass(category: string): string {
  switch (category) {
    case "APPROVAL":
    case "PUBLISHING":
      return "sp-approved";
    case "REJECTION":
    case "SECURITY":
      return "pill-failed";
    case "EDIT_AND_REVISION":
    case "RESCHEDULE_AND_OVERRIDE":
      return "pill-revision";
    case "ACCOUNT_MANAGEMENT":
    case "INSTITUTION_MANAGEMENT":
    case "CONFIGURATION":
    case "MEDIA_LIFECYCLE":
    default:
      return "sp-scheduled";
  }
}

export default function AuditDetailModal({ entry, onClose }: Props) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!entry) return null;

  const badgeClass = categoryBadgeClass(entry.category);

  return createPortal(
    <div className="audit-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="audit-modal-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="audit-modal-header">
          <div className="audit-modal-title-wrap">
            <h2>
              <i className="ti ti-shield-check" style={{ color: "var(--d-blue, #1877f2)" }} />
              Audit Event Details
            </h2>
            <span className="audit-modal-subtitle">
              Record ID #{entry.id}
            </span>
          </div>
          <button
            type="button"
            className="audit-modal-close"
            onClick={onClose}
            aria-label="Close dialog"
          >
            <i className="ti ti-x" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="audit-modal-body">
          {/* Top Status & Timestamp Banner */}
          <div className="audit-modal-banner">
            <div className="audit-banner-left">
              <span className={`status-pill ${badgeClass}`}>
                {entry.categoryLabel}
              </span>
              <span className="audit-modal-action-tag">
                {entry.actionLabel}
              </span>
            </div>
            <div className="audit-modal-timestamp">
              <i className="ti ti-clock" />
              <span>{formatDate(entry.timestamp)}</span>
            </div>
          </div>

          {/* Actor & Execution Panel */}
          <div className="audit-panel-card">
            <div className="audit-panel-header">
              <i className="ti ti-user" />
              <span>Actor & Execution</span>
            </div>
            <div className="audit-kv-grid">
              <div className="audit-kv-item">
                <span className="audit-kv-label">Actor</span>
                <div className="audit-actor-kv">
                  <div className="audit-avatar-circle" style={{ width: 22, height: 22, fontSize: 10 }}>
                    {entry.actor?.name ? entry.actor.name.charAt(0).toUpperCase() : "S"}
                  </div>
                  <strong>{entry.actor?.name || "System Automation"}</strong>
                  {entry.actor?.role === "SUPER_ADMINISTRATOR" && (
                    <span className="audit-actor-role">
                      • Super Admin
                    </span>
                  )}
                </div>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Email Address</span>
                <span className="audit-kv-value">{entry.actor?.email || "system@dasigconnect.gov.ph"}</span>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Role</span>
                <span className="audit-kv-value">
                  {entry.actor?.role === "SUPER_ADMINISTRATOR" ? "Super Administrator" : entry.actor?.role || "SYSTEM"}
                </span>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Institution</span>
                <span className="audit-kv-value">{entry.actor?.institutionName || "—"}</span>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Action Code</span>
                <span className="audit-kv-code">{entry.action}</span>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Client IP</span>
                <span className="audit-kv-value" style={{ fontFamily: "monospace" }}>
                  {entry.clientInfo?.ipAddress || "—"}
                </span>
              </div>
            </div>
          </div>

          {/* Target Entity Panel */}
          <div className="audit-panel-card">
            <div className="audit-panel-header">
              <i className="ti ti-box" />
              <span>Target Entity</span>
            </div>
            <div className="audit-kv-grid">
              <div className="audit-kv-item">
                <span className="audit-kv-label">Entity Type</span>
                <span className="audit-kv-value">{entry.entity.typeLabel}</span>
              </div>

              <div className="audit-kv-item">
                <span className="audit-kv-label">Entity Reference</span>
                <div style={{ marginTop: 2 }}>
                  {entry.entity.exists ? (
                    entry.entity.jumpUrl ? (
                      <a
                        href={entry.entity.jumpUrl}
                        className="audit-entity-link"
                        title="View referenced entity in workspace"
                      >
                        <i className="ti ti-link" style={{ fontSize: 12 }} />
                        <span>{entry.entity.label}</span>
                      </a>
                    ) : (
                      <span className="audit-entity-tag">
                        {entry.entity.label}
                      </span>
                    )
                  ) : (
                    <span className="audit-entity-unavailable" title="Entity record is no longer active">
                      <i className="ti ti-alert-circle" style={{ fontSize: 12 }} />
                      <span>Deleted / Unavailable</span>
                    </span>
                  )}
                </div>
              </div>

              {entry.entity.id && (
                <div className="audit-kv-item audit-kv-full">
                  <span className="audit-kv-label">Entity UUID</span>
                  <span className="audit-kv-value" style={{ fontFamily: "monospace", fontSize: "11.5px", color: "var(--d-muted)" }}>
                    {entry.entity.id}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Summary / Justification */}
          {entry.summary && (
            <div className="audit-panel-card">
              <div className="audit-panel-header">
                <i className="ti ti-notes" />
                <span>Summary / Justification</span>
              </div>
              <div className="audit-summary-box">
                {entry.summary}
              </div>
            </div>
          )}

          {/* Before & After Diffs (if any) */}
          {entry.diffs && entry.diffs.length > 0 && (
            <div className="audit-panel-card">
              <div className="audit-panel-header">
                <i className="ti ti-git-compare" />
                <span>Recorded State Changes (Diff)</span>
              </div>
              <div className="audit-diff-wrap">
                <table className="audit-diff-table">
                  <thead>
                    <tr>
                      <th style={{ width: "30%" }}>Field</th>
                      <th style={{ width: "35%" }}>Previous Value</th>
                      <th style={{ width: "35%" }}>Updated Value</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entry.diffs.map((diff, idx) => (
                      <tr key={idx}>
                        <td style={{ fontWeight: 600, color: "#0C1D3D" }}>{diff.fieldLabel}</td>
                        <td className="audit-diff-from">{diff.fromValue || "—"}</td>
                        <td className="audit-diff-to">{diff.toValue || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Raw Metadata JSON Inspector */}
          {entry.metadata && Object.keys(entry.metadata).length > 0 && (
            <div className="audit-panel-card audit-json-card">
              <details className="audit-json-details">
                <summary className="audit-json-summary">
                  <div className="audit-json-summary-left">
                    <i className="ti ti-code" style={{ color: "var(--d-blue, #1877f2)", fontSize: 13 }} />
                    <span>Raw Technical Metadata (JSON)</span>
                  </div>
                  <div className="audit-json-summary-right">
                    <span className="audit-expand-hint">Click to inspect</span>
                    <i className="ti ti-chevron-down audit-chevron-icon" />
                  </div>
                </summary>
                <div className="audit-json-body">
                  <pre className="audit-raw-json">
                    {JSON.stringify(entry.metadata, null, 2)}
                  </pre>
                </div>
              </details>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="audit-modal-footer">
          <button
            type="button"
            className="audit-page-btn"
            style={{ height: "34px", padding: "0 20px", background: "#FFFFFF", fontWeight: 600 }}
            onClick={onClose}
          >
            Close
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
