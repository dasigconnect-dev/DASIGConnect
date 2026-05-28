import { useEffect, useState } from "react";
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
  const [activeTab, setActiveTab] = useState<"preview" | "details">("preview");

  useEffect(() => {
    if (open) setActiveTab("preview");
  }, [open]);

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

        <div className="fb-preview-modal-tabs" role="tablist" aria-label="Facebook preview sections">
          <button
            type="button"
            className={activeTab === "preview" ? "active" : ""}
            role="tab"
            aria-selected={activeTab === "preview"}
            onClick={() => setActiveTab("preview")}
          >
            <i className="ti ti-brand-facebook" aria-hidden="true" />
            Preview
          </button>
          <button
            type="button"
            className={activeTab === "details" ? "active" : ""}
            role="tab"
            aria-selected={activeTab === "details"}
            onClick={() => setActiveTab("details")}
          >
            <i className="ti ti-list-check" aria-hidden="true" />
            Submission Details
            {details.missingItems.length > 0 && (
              <span>{details.missingItems.length}</span>
            )}
          </button>
        </div>

        <div className={`fb-preview-modal-body ${activeTab === "preview" ? "preview-mode" : "details-mode"}`}>
          {activeTab === "preview" ? (
          <div className="fb-preview-modal-post" role="tabpanel">
            <div className="fb-preview-stage-head">
              <div>
                <span>Public feed preview</span>
                <strong>What followers will see</strong>
              </div>
              <p>Preview only. Engagement controls are shown for context.</p>
            </div>
            <div className="fb-preview-feed-shell">
              <div className="fb-preview-feed-bar">
                <span></span>
                Social post preview
                <span></span>
              </div>
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
            </div>
            <FacebookPreviewMediaReorder
              mediaItems={mediaItems}
              activeMediaId={mediaItems[activeMediaIndex]?.id}
              disabled={reorderDisabled}
              onSelect={onMediaIndexChange}
              onReorder={onReorderMedia}
            />
          </div>
          ) : (
            <div className="fb-preview-details-tab" role="tabpanel">
              <FacebookPreviewDetails details={details} />
            </div>
          )}
        </div>

        <div className="fb-preview-modal-actions">
          <div className="fb-preview-footer-guidance" role="status">
            <i className="ti ti-shield-check" aria-hidden="true" />
            <span>
              {submitDisabledReason
                ? submitDisabledReason
                : "Submitting sends this post to your institution validator. You can still save changes as a draft before sending."}
            </span>
          </div>
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
