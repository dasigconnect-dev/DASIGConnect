interface RevisionFeedbackBannerProps {
  remarks: string | null | undefined;
}

export function RevisionFeedbackBanner({
  remarks,
}: RevisionFeedbackBannerProps) {
  const text = remarks && remarks.trim().length > 0 ? remarks.trim() : null;

  return (
    <div className="sub-revision-banner" role="alert">
      <div className="sub-revision-banner-icon">
        <i className="ti ti-edit" aria-hidden="true" />
      </div>

      <div className="sub-revision-banner-content">
        <strong>Revision Requested</strong>
        <p>
          {text ? `“${text}”` : "The moderator requested changes before this post can be approved."}
        </p>
      </div>
    </div>
  );
}
