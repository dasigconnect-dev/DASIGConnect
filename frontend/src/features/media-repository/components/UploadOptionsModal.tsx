import { createPortal } from "react-dom";

export type UploadDuplicateDecision = "replace" | "keep_both";

interface UploadOptionsModalProps {
  open: boolean;
  /** Filenames among the selected files that already exist in this location. */
  duplicateNames: string[];
  decision: UploadDuplicateDecision;
  onDecisionChange: (decision: UploadDuplicateDecision) => void;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * Google Drive-style "Upload options" dialog shown when a selected filename already exists in the
 * current location. The user chooses to replace the existing file (uploaded as a linked new
 * version — originals stay immutable) or keep both files.
 */
export default function UploadOptionsModal({
  open,
  duplicateNames,
  decision,
  onDecisionChange,
  onCancel,
  onConfirm,
}: UploadOptionsModalProps) {
  if (!open) {
    return null;
  }

  const single = duplicateNames.length === 1;
  const message = single
    ? `“${duplicateNames[0]}” already exists in this location. Do you want to replace the existing file with a new version or keep both files? Replacing the file won’t change sharing settings.`
    : `${duplicateNames.length} files already exist in this location. Do you want to replace the existing files with new versions or keep both copies? Replacing won’t change sharing settings.`;

  const modal = (
    <div
      className="med-modal-overlay open"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="med-modal-card med-modal-card--sm" role="dialog" aria-modal="true" aria-label="Upload options">
        <div className="med-modal-header">
          <span className="med-modal-title">Upload options</span>
          <button className="med-modal-close" onClick={onCancel} type="button" aria-label="Close">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          <p className="med-upload-options-msg">{message}</p>

          {!single && duplicateNames.length <= 6 && (
            <ul className="med-upload-options-names">
              {duplicateNames.map((name) => (
                <li key={name}>{name}</li>
              ))}
            </ul>
          )}

          <div className="med-upload-options-choices" role="radiogroup" aria-label="Upload options">
            <label className={`med-upload-option${decision === "replace" ? " selected" : ""}`}>
              <input
                type="radio"
                name="upload-option"
                checked={decision === "replace"}
                onChange={() => onDecisionChange("replace")}
              />
              <span>
                <strong>Replace existing file{single ? "" : "s"}</strong>
                <small>Uploads a new version and supersedes the current file (the original is preserved).</small>
              </span>
            </label>
            <label className={`med-upload-option${decision === "keep_both" ? " selected" : ""}`}>
              <input
                type="radio"
                name="upload-option"
                checked={decision === "keep_both"}
                onChange={() => onDecisionChange("keep_both")}
              />
              <span>
                <strong>Keep both files</strong>
                <small>Uploads as a separate asset alongside the existing one.</small>
              </span>
            </label>
          </div>
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" onClick={onCancel} type="button">
            Cancel
          </button>
          <button className="med-btn med-btn-primary" onClick={onConfirm} type="button">
            Upload
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
