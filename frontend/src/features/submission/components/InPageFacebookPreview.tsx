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
  reorderDisabled,
  onMediaIndexChange,
  onReorderMedia,
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
  const hasReorder = mediaItems.length > 1;

  return (
    <section
      className={`sub-preview-workflow sub-preview-workflow--frameless${hasReorder ? " has-reorder" : ""}`}
      aria-label="What followers will see"
    >
      <div className={`sub-preview-split${hasReorder ? " has-reorder" : ""}`}>
        <div className="sub-preview-card-col">
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
        </div>
        {hasReorder && (
          <aside className="sub-preview-reorder-col">
            <FacebookPreviewMediaReorder
              mediaItems={mediaItems}
              activeMediaId={mediaItems[activeMediaIndex]?.id}
              disabled={reorderDisabled}
              onSelect={onMediaIndexChange}
              onReorder={onReorderMedia}
            />
          </aside>
        )}
      </div>

      {/* <div className="sub-preview-footer">
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
      </div> */}
    </section>
  );
}
