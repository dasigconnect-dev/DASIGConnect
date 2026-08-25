import FacebookPreviewCard from "../../../components/facebook/FacebookPreviewCard";
import FacebookPreviewMediaReorder from "../../../components/facebook/FacebookPreviewMediaReorder";
import type { FacebookPreviewMediaItem } from "../../../types/facebook";

export function InPageFacebookPreview({
  pageName,
  pageAvatarUrl,
  publishDate,
  caption,
  mediaItems,
  activeMediaIndex,
  canSaveDraft,
  canSubmitForReview,
  submitDisabledReason,
  isSaving,
  isSubmitting,
  reorderDisabled,
  onMediaIndexChange,
  onReorderMedia,
  onSaveDraft,
  onSubmitForReview,
  onEditDetails,
}: {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex: number;
  canSaveDraft: boolean;
  canSubmitForReview: boolean;
  submitDisabledReason?: string;
  isSaving: boolean;
  isSubmitting: boolean;
  reorderDisabled?: boolean;
  onMediaIndexChange: (index: number) => void;
  onReorderMedia: (orderedIds: string[]) => void;
  onSaveDraft: () => void;
  onSubmitForReview: () => void;
  onEditDetails: () => void;
}) {
  return (
    <section className="sub-preview-workflow" aria-labelledby="sub-preview-title">
      <div className="sub-preview-tab-panel">
        <div className="sub-preview-stage-head">
          <div>
            <span>Public feed preview</span>
            <h2 id="sub-preview-title">What followers will see</h2>
          </div>
          <p>
            Preview the public-facing post before it moves into approval.
          </p>
        </div>
        <FacebookPreviewCard
          pageName={pageName}
          pageAvatarUrl={pageAvatarUrl}
          publishDate={publishDate}
          caption={caption}
          mediaItems={mediaItems}
          activeMediaIndex={activeMediaIndex}
          onMediaIndexChange={onMediaIndexChange}
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

      <div className="sub-preview-footer">
        <div className="sub-preview-guidance" role="status">
          <i className="ti ti-shield-check" aria-hidden="true" />
          <span>
            {submitDisabledReason ||
              "Submitting sends this post for approval. Save as draft if you still want to refine it."}
          </span>
        </div>
        <button
          className="sub-preview-btn secondary"
          type="button"
          onClick={onEditDetails}
        >
          <i className="ti ti-edit" aria-hidden="true" />
          Back to Editing
        </button>
        {canSaveDraft && (
          <button
            className="sub-preview-btn secondary"
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
        {canSubmitForReview && (
          <button
            className="sub-preview-btn primary"
            type="button"
            disabled={Boolean(submitDisabledReason) || isSaving || isSubmitting}
            onClick={onSubmitForReview}
          >
            <i
              className={`ti ${isSubmitting ? "ti-loader-2 sub-spin" : "ti-send"}`}
              aria-hidden="true"
            />
            {isSubmitting ? "Submitting..." : "Submit for Approval"}
          </button>
        )}
      </div>
    </section>
  );
}
