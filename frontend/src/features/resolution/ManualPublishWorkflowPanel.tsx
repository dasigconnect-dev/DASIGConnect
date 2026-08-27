import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import type { ManualPublishDetail } from "../../api/resolutionApi";

const FACEBOOK_PAGE_URL = "https://www.facebook.com/DostDasig";
const ABANDONMENT_MS = 2 * 60 * 60 * 1000; // 2 hours

interface ManualPublishWorkflowPanelProps {
  detail: ManualPublishDetail | null;
  loading: boolean;
  busy: boolean;
  onConfirm: (postUrl?: string, notes?: string) => void;
  onCancel: () => void;
  onClose: () => void;
}

function useAbandonmentCountdown(startedAt: string | null) {
  const [remaining, setRemaining] = useState<number | null>(null);

  useEffect(() => {
    if (!startedAt) {
      queueMicrotask(() => setRemaining(null));
      return;
    }
    const expiresAt = new Date(startedAt).getTime() + ABANDONMENT_MS;
    function update() {
      const ms = expiresAt - Date.now();
      setRemaining(ms > 0 ? ms : 0);
    }
    const id = window.setInterval(update, 1000);
    queueMicrotask(update);
    return () => clearInterval(id);
  }, [startedAt]);

  return remaining;
}

function formatCountdown(ms: number) {
  const totalSeconds = Math.ceil(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  if (h > 0) return `${h}h ${m}m remaining`;
  if (m > 0) return `${m}m ${s}s remaining`;
  return `${s}s remaining`;
}

function formatDatetime(iso: string | null) {
  if (!iso) return "Not scheduled";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function formatAbandonedAt(iso: string) {
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "gif", "webp"].includes(
    fileType.toLowerCase(),
  );
}

function urlInvalid(url: string) {
  return url.trim().length > 0 && !url.trim().startsWith("https://www.facebook.com/");
}

export default function ManualPublishWorkflowPanel({
  detail,
  loading,
  busy,
  onConfirm,
  onCancel,
  onClose,
}: ManualPublishWorkflowPanelProps) {
  const [postUrl, setPostUrl] = useState("");
  const [notes, setNotes] = useState("");
  const [postUrlError, setPostUrlError] = useState("");
  const [copied, setCopied] = useState(false);
  const copyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const remaining = useAbandonmentCountdown(
    detail?.manualPublishStartedAt ?? null,
  );

  useEffect(() => {
    if (!detail) {
      queueMicrotask(() => {
        setPostUrl("");
        setNotes("");
        setPostUrlError("");
        setCopied(false);
      });
    }
  }, [detail]);

  useEffect(() => {
    return () => {
      if (copyTimer.current) clearTimeout(copyTimer.current);
    };
  }, []);

  if (!detail && !loading) return null;

  function handleUrlChange(val: string) {
    setPostUrl(val);
    if (val.trim().length > 0 && !val.trim().startsWith("https://www.facebook.com/")) {
      setPostUrlError("URL must start with https://www.facebook.com/");
    } else {
      setPostUrlError("");
    }
  }

  async function handleCopyCaption() {
    if (!detail?.caption) return;
    try {
      await navigator.clipboard.writeText(detail.caption);
      setCopied(true);
      if (copyTimer.current) clearTimeout(copyTimer.current);
      copyTimer.current = setTimeout(() => setCopied(false), 2500);
    } catch {
      // clipboard API unavailable
    }
  }

  function handleConfirm() {
    if (urlInvalid(postUrl)) return;
    onConfirm(postUrl.trim() || undefined, notes.trim() || undefined);
  }

  function handleClose() {
    if (busy) return;
    onClose();
  }

  const images = detail?.mediaAssets.filter((a) => isImageType(a.fileType)) ?? [];
  const videos = detail?.mediaAssets.filter((a) => !isImageType(a.fileType)) ?? [];
  const confirmDisabled = busy || loading || urlInvalid(postUrl);

  const contributorName = detail
    ? [detail.contributorFirstName, detail.contributorLastName]
        .filter(Boolean)
        .join(" ") || detail.contributorEmail
    : null;

  return createPortal(
    <div
      className="val-modal-overlay"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Manual publish workflow"
    >
      <div
        className="val-modal res-workflow-card"
        style={{
          maxWidth: "640px",
          width: "100%",
          maxHeight: "calc(100vh - 48px)",
          display: "flex",
          flexDirection: "column",
          padding: 0,
          overflow: "hidden",
          textAlign: "left",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "18px 24px",
            borderBottom: "1px solid var(--val-border, #e2e8f0)",
            background: "var(--val-surface, #ffffff)",
            flexShrink: 0,
          }}
        >
          <div style={{ display: "flex", flexDirection: "column", gap: "2px", minWidth: 0, flex: 1, marginRight: "12px" }}>
            <span style={{ fontSize: "11px", fontWeight: 800, textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--val-muted, #64748b)" }}>
              Manual Publishing Workflow
            </span>
            <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "var(--val-text, #0f172a)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
              {detail ? detail.eventTitle : "Loading..."}
            </h3>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "8px", flexShrink: 0 }}>
            {remaining !== null && (
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "5px",
                  padding: "4px 10px",
                  borderRadius: "20px",
                  fontSize: "12px",
                  fontWeight: 600,
                  background: remaining < 10 * 60 * 1000 ? "#fffbeb" : "#f1f5f9",
                  color: remaining < 10 * 60 * 1000 ? "#b45309" : "#475569",
                  border: `1px solid ${remaining < 10 * 60 * 1000 ? "#fde68a" : "#e2e8f0"}`,
                }}
              >
                <i className="ti ti-clock" aria-hidden="true" />
                <span>{formatCountdown(remaining)}</span>
              </div>
            )}
            <button
              type="button"
              className="val-collapse-btn"
              onClick={handleClose}
              aria-label="Close"
              style={{ color: "#64748b" }}
            >
              <i className="ti ti-x" aria-hidden="true" />
            </button>
          </div>
        </div>

        {/* Body */}
        <div style={{ overflowY: "auto", flex: 1, padding: 0 }}>
          {loading && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: "10px", padding: "48px 24px", color: "var(--val-muted)" }}>
              <i className="ti ti-loader-2 val-spin" style={{ fontSize: "20px" }} />
              <span>Loading submission details...</span>
            </div>
          )}

          {!loading && detail && (
            <>
              {/* Abandonment note (A2) */}
              {detail.lastManualPublishAbandonedAt && (
                <div style={{ display: "flex", alignItems: "flex-start", gap: "8px", padding: "12px 24px", background: "#fffbeb", borderBottom: "1px solid #fde68a", color: "#92400e", fontSize: "13px" }}>
                  <i className="ti ti-info-circle" style={{ marginTop: "2px", flexShrink: 0 }} />
                  <span>
                    A manual publishing attempt was started at{" "}
                    <strong>{formatAbandonedAt(detail.lastManualPublishAbandonedAt)}</strong> and abandoned. The submission is still awaiting manual publication.
                  </span>
                </div>
              )}

              {/* Submission meta */}
              <div style={{ display: "flex", flexWrap: "wrap", gap: "16px 24px", padding: "14px 24px", background: "var(--val-surface-2, #f8fafc)", borderBottom: "1px solid var(--val-border, #e2e8f0)" }}>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "10.5px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--val-muted)" }}>Scheduled</span>
                  <strong style={{ fontSize: "13px", color: "var(--val-text)" }}>{formatDatetime(detail.scheduledAt)}</strong>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "10.5px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--val-muted)" }}>Contributor</span>
                  <strong style={{ fontSize: "13px", color: "var(--val-text)" }}>{contributorName}</strong>
                </div>
                <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
                  <span style={{ fontSize: "10.5px", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.08em", color: "var(--val-muted)" }}>Submission ID</span>
                  <strong style={{ fontSize: "13px", fontFamily: "monospace", color: "var(--val-text)" }}>{detail.submissionId.slice(0, 8)}…</strong>
                </div>
              </div>

              {/* Step 1 — Copy Content */}
              <section style={{ padding: "18px 24px", borderBottom: "1px solid var(--val-border, #e2e8f0)", display: "flex", flexDirection: "column", gap: "12px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "14px", fontWeight: 700, color: "var(--val-text)" }}>
                  <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: "22px", height: "22px", borderRadius: "50%", background: "#1877f2", color: "white", fontSize: "12px", fontWeight: 800 }}>1</span>
                  Copy Content
                </div>

                {detail.caption ? (
                  <div style={{ background: "var(--val-surface-2, #f8fafc)", border: "1px solid var(--val-border, #e2e8f0)", borderRadius: "10px", padding: "14px" }}>
                    <p style={{ margin: "0 0 10px", fontSize: "13.5px", lineHeight: 1.5, color: "var(--val-text-2, #334155)", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                      {detail.caption}
                    </p>
                    <button
                      type="button"
                      className="val-btn val-btn-secondary"
                      style={{ fontSize: "12.5px", height: "32px", padding: "0 12px" }}
                      onClick={handleCopyCaption}
                    >
                      {copied ? (
                        <>
                          <i className="ti ti-check" aria-hidden="true" />
                          Copied!
                        </>
                      ) : (
                        <>
                          <i className="ti ti-copy" aria-hidden="true" />
                          Copy Caption
                        </>
                      )}
                    </button>
                  </div>
                ) : (
                  <p style={{ margin: 0, color: "var(--val-muted)", fontStyle: "italic", fontSize: "13px" }}>No caption set.</p>
                )}

                {images.length > 0 && (
                  <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", marginTop: "4px" }}>
                    {images.map((img) => (
                      <a
                        key={img.id}
                        href={img.storageUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        download={img.fileName}
                        style={{
                          width: "72px",
                          height: "72px",
                          borderRadius: "8px",
                          overflow: "hidden",
                          border: "1px solid var(--val-border, #cbd5e1)",
                          display: "block",
                          position: "relative",
                        }}
                        title={`Download ${img.fileName}`}
                      >
                        <img
                          src={img.storageUrl}
                          alt={img.fileName}
                          style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }}
                        />
                      </a>
                    ))}
                  </div>
                )}

                {videos.length > 0 && (
                  <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginTop: "4px" }}>
                    <p style={{ margin: 0, padding: "8px 12px", background: "#eff6ff", border: "1px solid #bfdbfe", borderRadius: "8px", fontSize: "12.5px", color: "#1e40af" }}>
                      <i className="ti ti-info-circle" style={{ marginRight: "6px" }} />
                      This submission contains a video. Download it to your device, then upload it manually when creating the Facebook post.
                    </p>
                    {videos.map((vid) => (
                      <a
                        key={vid.id}
                        href={vid.storageUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        download={vid.fileName}
                        className="val-btn val-btn-secondary"
                        style={{ alignSelf: "flex-start", fontSize: "12.5px" }}
                      >
                        <i className="ti ti-video" aria-hidden="true" />
                        {vid.fileName}
                        <i className="ti ti-download" aria-hidden="true" style={{ marginLeft: "4px" }} />
                      </a>
                    ))}
                  </div>
                )}
              </section>

              {/* Step 2 — Post to Facebook */}
              <section style={{ padding: "18px 24px", borderBottom: "1px solid var(--val-border, #e2e8f0)", display: "flex", flexDirection: "column", gap: "12px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "14px", fontWeight: 700, color: "var(--val-text)" }}>
                  <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: "22px", height: "22px", borderRadius: "50%", background: "#1877f2", color: "white", fontSize: "12px", fontWeight: 800 }}>2</span>
                  Post to Facebook
                </div>
                <a
                  href={FACEBOOK_PAGE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="val-btn val-btn-primary"
                  style={{ textDecoration: "none", width: "100%", justifyContent: "center", height: "40px", fontSize: "14px" }}
                >
                  <i className="ti ti-brand-facebook" aria-hidden="true" />
                  <span>Open DASIG Facebook Page →</span>
                  <i className="ti ti-external-link" aria-hidden="true" />
                </a>
              </section>

              {/* Step 3 — Record Details */}
              <section style={{ padding: "18px 24px", display: "flex", flexDirection: "column", gap: "14px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px", fontSize: "14px", fontWeight: 700, color: "var(--val-text)" }}>
                  <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: "22px", height: "22px", borderRadius: "50%", background: "#1877f2", color: "white", fontSize: "12px", fontWeight: 800 }}>3</span>
                  Record Details
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                  <label htmlFor="res-wf-post-url" style={{ fontSize: "12.5px", fontWeight: 700, color: "var(--val-text-2)" }}>
                    Live Post URL <span style={{ fontWeight: 400, color: "var(--val-muted)" }}>(optional)</span>
                  </label>
                  <input
                    id="res-wf-post-url"
                    type="url"
                    placeholder="https://www.facebook.com/permalink/..."
                    value={postUrl}
                    onChange={(e) => handleUrlChange(e.target.value)}
                    style={{
                      width: "100%",
                      padding: "8px 12px",
                      borderRadius: "8px",
                      border: `1px solid ${postUrlError ? "var(--val-red, #dc2626)" : "var(--val-border, #cbd5e1)"}`,
                      background: "var(--val-surface, #ffffff)",
                      color: "var(--val-text, #0f172a)",
                      fontSize: "13.5px",
                      boxSizing: "border-box",
                    }}
                  />
                  {postUrlError && (
                    <p style={{ margin: 0, color: "var(--val-red, #dc2626)", fontSize: "12px" }}>
                      {postUrlError}
                    </p>
                  )}
                </div>

                <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                  <label htmlFor="res-wf-notes" style={{ fontSize: "12.5px", fontWeight: 700, color: "var(--val-text-2)" }}>
                    Admin Notes <span style={{ fontWeight: 400, color: "var(--val-muted)" }}>(optional)</span>
                  </label>
                  <textarea
                    id="res-wf-notes"
                    placeholder="Any notes about this manual publish..."
                    rows={3}
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    style={{
                      width: "100%",
                      padding: "8px 12px",
                      borderRadius: "8px",
                      border: "1px solid var(--val-border, #cbd5e1)",
                      background: "var(--val-surface, #ffffff)",
                      color: "var(--val-text, #0f172a)",
                      fontSize: "13.5px",
                      boxSizing: "border-box",
                    }}
                  />
                </div>
              </section>
            </>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "16px 24px",
            borderTop: "1px solid var(--val-border, #e2e8f0)",
            background: "var(--val-surface, #ffffff)",
            flexShrink: 0,
          }}
        >
          <button
            type="button"
            className="val-btn val-btn-danger-outline"
            disabled={busy || loading}
            onClick={onCancel}
          >
            <i className="ti ti-x" aria-hidden="true" />
            <span>Cancel Session</span>
          </button>
          <button
            type="button"
            className="val-btn val-btn-primary"
            disabled={confirmDisabled}
            onClick={handleConfirm}
          >
            {busy ? (
              <>
                <i className="ti ti-loader-2 val-spin" />
                <span>Saving...</span>
              </>
            ) : (
              <>
                <i className="ti ti-circle-check" aria-hidden="true" />
                <span>Mark as Published</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
