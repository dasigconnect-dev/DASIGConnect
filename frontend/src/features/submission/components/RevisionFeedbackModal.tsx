import { useEffect } from "react";
import { createPortal } from "react-dom";

interface RevisionFeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
  eventTitle: string;
  remarks: string | null | undefined;
  onStartEditing: () => void;
}

export function RevisionFeedbackModal({
  isOpen,
  onClose,
  eventTitle,
  remarks,
  onStartEditing,
}: RevisionFeedbackModalProps) {
  useEffect(() => {
    if (!isOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };

    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const hasRemarks = Boolean(remarks && remarks.trim().length > 0);

  return createPortal(
    <div
      className="sub-modal-overlay"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="revision-modal-title"
    >
      <div
        className="sub-modal sub-revision-modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="sub-revision-modal-icon">
          <i className="ti ti-edit" aria-hidden="true" />
        </div>

        <h3 id="revision-modal-title" className="sub-revision-modal-title">
          Revision Requested
        </h3>

        {eventTitle && (
          <p className="sub-revision-modal-subtitle">{eventTitle}</p>
        )}

        <div className="sub-revision-modal-content">
          {hasRemarks ? (
            <p className="sub-revision-modal-text">{remarks}</p>
          ) : (
            <p className="sub-revision-modal-empty">
              The moderator requested updates before this post can be approved. Please review your content and resubmit.
            </p>
          )}
        </div>

        <div className="sub-revision-modal-actions">
          <button
            type="button"
            className="sub-revision-btn-secondary"
            onClick={onClose}
          >
            Close
          </button>
          <button
            type="button"
            className="sub-revision-btn-primary"
            onClick={() => {
              onClose();
              onStartEditing();
            }}
          >
            Edit Submission
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
