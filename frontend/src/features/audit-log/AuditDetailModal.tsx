import { useEffect } from "react";
import type { AuditLogEntry } from "../../api/auditLogApi";

interface Props {
  entry: AuditLogEntry | null;
  onClose: () => void;
}

function formatDate(iso: string) {
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

function categoryClass(category: string): string {
  switch (category) {
    case "APPROVAL": return "cat-approval";
    case "REJECTION": return "cat-rejection";
    case "EDIT_AND_REVISION": return "cat-edit";
    case "RESCHEDULE_AND_OVERRIDE": return "cat-reschedule";
    case "PUBLISHING": return "cat-publish";
    case "ACCOUNT_MANAGEMENT": return "cat-account";
    case "INSTITUTION_MANAGEMENT": return "cat-institution";
    case "MEDIA_LIFECYCLE": return "cat-media";
    case "CONFIGURATION": return "cat-config";
    case "SECURITY": return "cat-security";
    default: return "cat-other";
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

  return (
    <div className="audit-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="audit-modal-dialog" onClick={(e) => e.stopPropagation()}>
        {/* Modal Header */}
        <div className="audit-modal-header">
          <h2>
            <i className="ti ti-clipboard-check" style={{ color: "#0056b3" }} />
            Audit Event Details
          </h2>
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
          {/* Top Status Banner */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "8px" }}>
            <span className={`audit-cat-badge ${categoryClass(entry.category)}`}>
              <i className="ti ti-tag" />
              {entry.categoryLabel}
            </span>
            <span style={{ fontSize: "12px", color: "#64748b", fontWeight: 600 }}>
              <i className="ti ti-clock" style={{ marginRight: "4px" }} />
              {formatDate(entry.timestamp)}
            </span>
          </div>

          {/* Actor & Action Grid */}
          <div className="audit-modal-section">
            <span className="audit-modal-section-title">
              <i className="ti ti-user" /> Actor & Action Overview
            </span>
            <div className="audit-detail-grid">
              <div className="audit-detail-item">
                <span className="audit-detail-label">Actor Name</span>
                <span className="audit-detail-value">{entry.actor?.name ?? "System / Automated"}</span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Email Address</span>
                <span className="audit-detail-value">{entry.actor?.email ?? "system@dasigconnect.gov.ph"}</span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Role</span>
                <span className="audit-detail-value">{entry.actor?.role ?? "SYSTEM"}</span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Institution</span>
                <span className="audit-detail-value">{entry.actor?.institutionName ?? "—"}</span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Action Code</span>
                <span className="audit-detail-value" style={{ fontFamily: "monospace", color: "#0056b3" }}>
                  {entry.action}
                </span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Client IP</span>
                <span className="audit-detail-value">{entry.clientInfo?.ipAddress ?? "—"}</span>
              </div>
            </div>
          </div>

          {/* Affected Entity */}
          <div className="audit-modal-section">
            <span className="audit-modal-section-title">
              <i className="ti ti-box" /> Affected Entity
            </span>
            <div className="audit-detail-grid">
              <div className="audit-detail-item">
                <span className="audit-detail-label">Entity Type</span>
                <span className="audit-detail-value">{entry.entity.typeLabel}</span>
              </div>
              <div className="audit-detail-item">
                <span className="audit-detail-label">Entity Identifier / Reference</span>
                <div style={{ marginTop: "4px" }}>
                  {entry.entity.exists ? (
                    entry.entity.jumpUrl ? (
                      <a
                        href={entry.entity.jumpUrl}
                        className="audit-entity-badge audit-entity-link"
                        title="View referenced entity in workspace"
                      >
                        <i className="ti ti-external-link" />
                        {entry.entity.label}
                      </a>
                    ) : (
                      <span className="audit-entity-badge audit-entity-link">
                        {entry.entity.label}
                      </span>
                    )
                  ) : (
                    <span className="audit-entity-badge audit-entity-unavailable" title="Entity record is no longer active in the database">
                      <i className="ti ti-alert-triangle" />
                      [Entity no longer available]
                    </span>
                  )}
                </div>
              </div>
              {entry.entity.id && (
                <div className="audit-detail-item" style={{ gridColumn: "1 / -1" }}>
                  <span className="audit-detail-label">Entity UUID</span>
                  <span className="audit-detail-value" style={{ fontFamily: "monospace", fontSize: "12px", color: "#64748b" }}>
                    {entry.entity.id}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Summary / Reason */}
          {entry.summary && (
            <div className="audit-modal-section">
              <span className="audit-modal-section-title">
                <i className="ti ti-notes" /> Summary / Justification
              </span>
              <div
                style={{
                  padding: "12px 16px",
                  background: "#f8fafc",
                  border: "1px solid #e2e8f0",
                  borderRadius: "8px",
                  fontSize: "13px",
                  color: "#334155",
                  lineHeight: "1.5",
                }}
              >
                {entry.summary}
              </div>
            </div>
          )}

          {/* Before & After Diffs (if any) */}
          {entry.diffs && entry.diffs.length > 0 && (
            <div className="audit-modal-section">
              <span className="audit-modal-section-title">
                <i className="ti ti-git-compare" /> Recorded State Changes (Diff)
              </span>
              <div className="audit-diff-card">
                <div className="audit-diff-row" style={{ background: "#f8fafc", fontWeight: 700, color: "#64748b" }}>
                  <div className="audit-diff-field">Field</div>
                  <div className="audit-diff-from" style={{ background: "#f8fafc", color: "#64748b" }}>Before / Original</div>
                  <div className="audit-diff-to" style={{ background: "#f8fafc", color: "#64748b" }}>After / Updated</div>
                </div>
                {entry.diffs.map((diff, idx) => (
                  <div key={idx} className="audit-diff-row">
                    <div className="audit-diff-field">{diff.fieldLabel}</div>
                    <div className="audit-diff-from">{diff.fromValue}</div>
                    <div className="audit-diff-to">{diff.toValue}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Raw Metadata JSON Inspector */}
          <div className="audit-modal-section">
            <details style={{ cursor: "pointer" }}>
              <summary className="audit-modal-section-title" style={{ outline: "none", userSelect: "none" }}>
                <i className="ti ti-code" /> Raw Technical Metadata (JSON)
              </summary>
              <pre className="audit-raw-json" style={{ marginTop: "8px" }}>
                {JSON.stringify(entry.metadata, null, 2)}
              </pre>
            </details>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="audit-modal-footer">
          <button type="button" className="audit-reset-btn" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
