import { useEffect, useRef, useState } from "react";
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

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "gif", "webp"].includes(
    fileType.toLowerCase(),
  );
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

  if (!detail && !loading) return null;

  function handleClose() {
    setPostUrl("");
    setNotes("");
    setPostUrlError("");
    setCopied(false);
    onClose();
  }

  function handleCopyCaption() {
    if (!detail?.caption) return;
    void navigator.clipboard.writeText(detail.caption).then(() => {
      setCopied(true);
      if (copyTimer.current) clearTimeout(copyTimer.current);
      copyTimer.current = setTimeout(() => setCopied(false), 2000);
    });
  }

  function handleConfirm() {
    if (postUrl.trim() && !postUrl.startsWith("https://www.facebook.com/")) {
      setPostUrlError("Must be a facebook.com URL.");
      return;
    }
    onConfirm(postUrl.trim() || undefined, notes.trim() || undefined);
  }

  const images = detail?.mediaAssets.filter((a) => isImageType(a.fileType)) ?? [];
  const videos = detail?.mediaAssets.filter((a) => !isImageType(a.fileType)) ?? [];

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Manual publish workflow"
    >
      <div
        className="modal-card res-workflow-card"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="modal-card-header">
          <div className="res-workflow-header-inner">
            <div>
              <p className="res-workflow-kicker">Manual Publishing Workflow</p>
              <h2 className="modal-card-title">
                {detail ? detail.eventTitle : "Loading..."}
              </h2>
            </div>
            {remaining !== null && (
              <div
                className={`res-workflow-timer${remaining < 10 * 60 * 1000 ? " res-workflow-timer-urgent" : ""}`}
              >
                <i className="ti ti-clock" aria-hidden="true" />
                <span>{formatCountdown(remaining)}</span>
              </div>
            )}
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={handleClose}
            aria-label="Close"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        {/* Body */}
        <div className="modal-card-body res-workflow-body">
          {loading && (
            <div className="res-workflow-loading">
              <div className="spinner-ring spinner-ring-sm" />
              <span>Loading submission details...</span>
            </div>
          )}

          {!loading && detail && (
            <>
              {/* Step 1 — Copy Content */}
              <section className="res-workflow-step">
                <div className="res-workflow-step-label">
                  <span className="res-workflow-step-num">1</span>
                  Copy Content
                </div>

                {detail.caption ? (
                  <div className="res-workflow-caption-block">
                    <p className="res-workflow-caption-text">{detail.caption}</p>
                    <button
                      type="button"
                      className="btn-secondary btn-sm res-workflow-copy-btn"
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
                  <p className="res-workflow-no-caption">No caption set.</p>
                )}

                {images.length > 0 && (
                  <div className="res-workflow-media-grid">
                    {images.map((img) => (
                      <a
                        key={img.id}
                        href={img.storageUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        download={img.fileName}
                        className="res-workflow-thumb-link"
                        title={`Download ${img.fileName}`}
                      >
                        <img
                          src={img.storageUrl}
                          alt={img.fileName}
                          className="res-workflow-thumb"
                        />
                        <span className="res-workflow-thumb-overlay">
                          <i className="ti ti-download" aria-hidden="true" />
                        </span>
                      </a>
                    ))}
                  </div>
                )}

                {videos.length > 0 && (
                  <div className="res-workflow-video-list">
                    {videos.map((vid) => (
                      <a
                        key={vid.id}
                        href={vid.storageUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        download={vid.fileName}
                        className="res-workflow-video-link btn-secondary btn-sm"
                      >
                        <i className="ti ti-video" aria-hidden="true" />
                        {vid.fileName}
                        <i className="ti ti-download" aria-hidden="true" />
                      </a>
                    ))}
                  </div>
                )}
              </section>

              {/* Step 2 — Post to Facebook */}
              <section className="res-workflow-step">
                <div className="res-workflow-step-label">
                  <span className="res-workflow-step-num">2</span>
                  Post to Facebook
                </div>
                <a
                  href={FACEBOOK_PAGE_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn-primary btn-sm res-workflow-fb-btn"
                >
                  <i className="ti ti-brand-facebook" aria-hidden="true" />
                  Open DASIG Facebook Page
                  <i className="ti ti-external-link" aria-hidden="true" />
                </a>
              </section>

              {/* Step 3 — Record Details */}
              <section className="res-workflow-step">
                <div className="res-workflow-step-label">
                  <span className="res-workflow-step-num">3</span>
                  Record Details
                </div>

                <div className="form-field">
                  <label htmlFor="res-wf-post-url" className="form-label">
                    Facebook Post URL{" "}
                    <span className="form-label-optional">(optional)</span>
                  </label>
                  <input
                    id="res-wf-post-url"
                    type="url"
                    className={`form-input${postUrlError ? " input-error" : ""}`}
                    placeholder="https://www.facebook.com/permalink/..."
                    value={postUrl}
                    onChange={(e) => {
                      setPostUrl(e.target.value);
                      setPostUrlError("");
                    }}
                    aria-invalid={postUrlError ? "true" : "false"}
                    aria-describedby={
                      postUrlError ? "res-wf-post-url-error" : undefined
                    }
                  />
                  {postUrlError && (
                    <p id="res-wf-post-url-error" className="field-error">
                      {postUrlError}
                    </p>
                  )}
                </div>

                <div className="form-field">
                  <label htmlFor="res-wf-notes" className="form-label">
                    Notes{" "}
                    <span className="form-label-optional">(optional)</span>
                  </label>
                  <textarea
                    id="res-wf-notes"
                    className="form-input form-textarea"
                    placeholder="Any notes about this manual publish..."
                    rows={3}
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </div>
              </section>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="modal-card-footer">
          <button
            type="button"
            className="btn-danger btn-sm"
            disabled={busy || loading}
            onClick={onCancel}
          >
            <i className="ti ti-x" aria-hidden="true" />
            Cancel Session
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy || loading}
            onClick={handleConfirm}
          >
            {busy ? (
              <>
                <div className="spinner-ring spinner-ring-sm" />
                Saving...
              </>
            ) : (
              <>
                <i className="ti ti-circle-check" aria-hidden="true" />
                Mark as Published
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
