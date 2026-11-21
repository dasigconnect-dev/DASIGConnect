interface FacebookPreviewHeaderProps {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  publishLabel?: string;
  showWatermarkToggle?: boolean;
  showWatermark?: boolean;
  onToggleWatermark?: () => void;
}

export default function FacebookPreviewHeader({
  pageName,
  pageAvatarUrl,
  publishDate,
  publishLabel,
  showWatermarkToggle = false,
  showWatermark = true,
  onToggleWatermark,
}: FacebookPreviewHeaderProps) {
  const dateText =
    publishLabel || (publishDate ? formatPreviewDate(publishDate) : "Draft schedule");

  return (
    <div className="fb-preview-head">
      <div className="fb-preview-avatar" aria-hidden="true">
        {pageAvatarUrl ? (
          <img src={pageAvatarUrl} alt="" />
        ) : (
          <i className="ti ti-brand-facebook" />
        )}
      </div>
      <div className="fb-preview-page-meta">
        <div className="fb-preview-page-name">{pageName}</div>
        <div className="fb-preview-page-date">
          Published by {pageName}
          <span aria-hidden="true">•</span>
          {dateText}
          <i className="ti ti-world" aria-hidden="true" />
        </div>
      </div>
      <div className="fb-preview-head-actions">
        {showWatermarkToggle && onToggleWatermark && (
          <button
            type="button"
            className={`fb-preview-wm-toggle ${showWatermark ? "active" : ""}`}
            onClick={(event) => {
              event.stopPropagation();
              onToggleWatermark();
            }}
            title="Toggle watermark overlay on media preview"
          >
            <i
              className={`ti ${showWatermark ? "ti-shield-check-filled" : "ti-shield-off"}`}
              aria-hidden="true"
            />
            <span>{showWatermark ? "Watermark: ON" : "Watermark: OFF"}</span>
          </button>
        )}
        <button className="fb-preview-more" type="button" aria-label="Post options" tabIndex={-1}>
          <i className="ti ti-dots" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}

function formatPreviewDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Draft schedule";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}
