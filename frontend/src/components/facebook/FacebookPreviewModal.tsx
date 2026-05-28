import { useEffect } from "react";
import type {
  FacebookPreviewDetailsData,
  FacebookPreviewMediaItem,
} from "../../types/facebook";
import FacebookPreviewCard from "./FacebookPreviewCard";
import FacebookPreviewDetails from "./FacebookPreviewDetails";
import FacebookPreviewMediaReorder from "./FacebookPreviewMediaReorder";

interface FacebookPreviewModalProps {
  open: boolean;
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex: number;
  details: FacebookPreviewDetailsData;
  canSaveDraft: boolean;
  canSubmitForReview: boolean;
  submitDisabledReason?: string;
  isSaving: boolean;
  isSubmitting: boolean;
  previewError?: string;
  reorderDisabled?: boolean;
  onClose: () => void;
  onMediaIndexChange: (index: number) => void;
  onReorderMedia: (orderedIds: string[]) => void;
  onSaveDraft: () => void;
  onSubmitForReview: () => void;
  onEditDetails: () => void;
  onRetryPreview?: () => void;
}

export default function FacebookPreviewModal({
  open,
  pageName,
  pageAvatarUrl,
  publishDate,
  caption,
  mediaItems,
  activeMediaIndex,
  details,
  canSaveDraft,
  canSubmitForReview,
  submitDisabledReason,
  isSaving,
  isSubmitting,
  previewError,
  reorderDisabled = false,
  onClose,
  onMediaIndexChange,
  onReorderMedia,
  onSaveDraft,
  onSubmitForReview,
  onEditDetails,
  onRetryPreview,
}: FacebookPreviewModalProps) {
  useEffect(() => {
    if (!open) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, open]);

  if (!open) return null;

  return (
    <div
      className="fb-preview-modal-overlay"
      role="presentation"
      onMouseDown={onClose}
    >
      <div
        className="fb-preview-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="fb-preview-modal-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="fb-preview-modal-head">
          <div>
            <span className="fb-preview-modal-kicker">Preview and review</span>
            <h2 id="fb-preview-modal-title">Facebook Preview</h2>
          </div>
          <button
            className="fb-preview-modal-close"
            type="button"
            aria-label="Close Facebook preview"
            onClick={onClose}
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        <div className="fb-preview-modal-body">
          <div className="fb-preview-modal-post">
            <FacebookPreviewCard
              pageName={pageName}
              pageAvatarUrl={pageAvatarUrl}
              publishDate={publishDate}
              caption={caption}
              mediaItems={mediaItems}
              activeMediaIndex={activeMediaIndex}
              onMediaIndexChange={onMediaIndexChange}
              error={previewError}
              size="large"
            />
            <FacebookPreviewMediaReorder
              mediaItems={mediaItems}
              activeMediaId={mediaItems[activeMediaIndex]?.id}
              disabled={reorderDisabled}
              onSelect={onMediaIndexChange}
              onReorder={onReorderMedia}
            />
          </div>
          <FacebookPreviewDetails details={details} />
        </div>

        {submitDisabledReason && (
          <div className="fb-preview-action-note" role="status">
            <i className="ti ti-info-circle" aria-hidden="true" />
            {submitDisabledReason}
          </div>
        )}

        <div className="fb-preview-modal-actions">
          {previewError && onRetryPreview && (
            <button
              className="fb-preview-modal-btn secondary"
              type="button"
              onClick={onRetryPreview}
            >
              <i className="ti ti-refresh" aria-hidden="true" />
              Retry Preview Load
            </button>
          )}
          <button
            className="fb-preview-modal-btn secondary"
            type="button"
            onClick={onEditDetails}
          >
            <i className="ti ti-edit" aria-hidden="true" />
            Edit Details
          </button>
          {canSaveDraft && (
            <button
              className="fb-preview-modal-btn secondary"
              type="button"
              disabled={isSaving || isSubmitting}
              onClick={onSaveDraft}
            >
              <i
                className={`ti ${isSaving ? "ti-loader-2 sub-spin" : "ti-device-floppy"}`}
                aria-hidden="true"
              />
              {isSaving ? "Saving..." : "Save Draft"}
            </button>
          )}
          <button
            className="fb-preview-modal-btn ghost"
            type="button"
            onClick={onClose}
          >
            Close
          </button>
          {canSubmitForReview && (
            <button
              className="fb-preview-modal-btn primary"
              type="button"
              disabled={Boolean(submitDisabledReason) || isSaving || isSubmitting}
              onClick={onSubmitForReview}
            >
              <i
                className={`ti ${isSubmitting ? "ti-loader-2 sub-spin" : "ti-send"}`}
                aria-hidden="true"
              />
              {isSubmitting ? "Submitting..." : "Submit for Review"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
