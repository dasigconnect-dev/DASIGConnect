import { useState } from "react";
import type { FailedPublication } from "../../api/resolutionApi";

interface ResolutionCompleteModalProps {
  item: FailedPublication | null;
  busy: boolean;
  onConfirm: (postUrl?: string, notes?: string) => void;
  onClose: () => void;
}

export default function ResolutionCompleteModal({
  item,
  busy,
  onConfirm,
  onClose,
}: ResolutionCompleteModalProps) {
  const [postUrl, setPostUrl] = useState("");
  const [notes, setNotes] = useState("");
  const [postUrlError, setPostUrlError] = useState("");

  if (!item) return null;

  function handleClose() {
    setPostUrl("");
    setNotes("");
    setPostUrlError("");
    onClose();
  }

  function handleConfirm() {
    if (postUrl.trim() && !postUrl.startsWith("https://www.facebook.com/")) {
      setPostUrlError("Must be a facebook.com URL.");
      return;
    }
    onConfirm(postUrl.trim() || undefined, notes.trim() || undefined);
  }

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Complete manual publish"
    >
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <h2 className="modal-card-title">Mark as Published</h2>
          <button
            type="button"
            className="modal-close-btn"
            onClick={handleClose}
            aria-label="Close"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
        <div className="modal-card-body">
          <p className="modal-body-desc">
            Confirm that <strong>"{item.eventTitle}"</strong> has been manually
            posted to Facebook.
          </p>
          <div className="form-field">
            <label htmlFor="res-post-url" className="form-label">
              Facebook Post URL{" "}
              <span className="form-label-optional">(optional)</span>
            </label>
            <input
              id="res-post-url"
              type="url"
              className={`form-input${postUrlError ? " input-error" : ""}`}
              placeholder="https://www.facebook.com/permalink/..."
              value={postUrl}
              onChange={(e) => {
                setPostUrl(e.target.value);
                setPostUrlError("");
              }}
              aria-invalid={postUrlError ? "true" : "false"}
              aria-describedby={postUrlError ? "res-post-url-error" : undefined}
            />
            {postUrlError && (
              <p id="res-post-url-error" className="field-error">
                {postUrlError}
              </p>
            )}
          </div>
          <div className="form-field">
            <label htmlFor="res-notes" className="form-label">
              Notes <span className="form-label-optional">(optional)</span>
            </label>
            <textarea
              id="res-notes"
              className="form-input form-textarea"
              placeholder="Any notes about this manual publish..."
              rows={3}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>
        </div>
        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={handleClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy}
            onClick={handleConfirm}
          >
            {busy ? (
              <>
                <div className="spinner-ring spinner-ring-sm" />
                Saving...
              </>
            ) : (
              "Confirm Published"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
