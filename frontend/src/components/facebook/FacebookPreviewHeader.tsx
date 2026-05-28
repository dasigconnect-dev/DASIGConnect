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
          {publishDate ? formatPreviewDate(publishDate) : "Draft schedule"}
          <i className="ti ti-world" aria-hidden="true" />
        </div>
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
