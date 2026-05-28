interface FacebookPreviewHeaderProps {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
}

export default function FacebookPreviewHeader({
  pageName,
  pageAvatarUrl,
  publishDate,
}: FacebookPreviewHeaderProps) {
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
          {publishDate ? formatPreviewDate(publishDate) : "Draft schedule"}
          <i className="ti ti-world" aria-hidden="true" />
        </div>
      </div>
      <button className="fb-preview-more" type="button" aria-label="Post options" tabIndex={-1}>
        <i className="ti ti-dots" aria-hidden="true" />
      </button>
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
