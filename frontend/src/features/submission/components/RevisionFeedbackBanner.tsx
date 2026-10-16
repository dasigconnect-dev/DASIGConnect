import { parseRejectionReason } from "../../../lib/rejectionReason";

interface RevisionFeedbackBannerProps {
  type?: "revision" | "rejected";
  remarks: string | null | undefined;
}

export function RevisionFeedbackBanner({
  type = "revision",
  remarks,
}: RevisionFeedbackBannerProps) {
  const isRejected = type === "rejected";
  // A rejection is stored as "CODE: note" — show the code as a label, not raw.
  const rejection = isRejected ? parseRejectionReason(remarks) : null;
  const text = isRejected
    ? rejection?.note ?? null
    : remarks && remarks.trim().length > 0 ? remarks.trim() : null;

  return (
    <div className={isRejected ? "sub-rejection-banner" : "sub-revision-banner"} role="alert">
      <div className={isRejected ? "sub-rejection-banner-icon" : "sub-revision-banner-icon"}>
        <i className={isRejected ? "ti ti-circle-x" : "ti ti-edit"} aria-hidden="true" />
      </div>

      <div className={isRejected ? "sub-rejection-banner-content" : "sub-revision-banner-content"}>
        <strong>{isRejected ? "Submission Rejected" : "Revision Requested"}</strong>
        {rejection?.label && <span className="sub-rejection-reason">{rejection.label}</span>}
        <p>
          {text
            ? `“${text}”`
            : isRejected
              ? rejection?.label
                ? "Update your post details or media, then resubmit for review."
                : "The moderator rejected this post. Update your post details or media, then resubmit for review."
              : "The moderator requested changes before this post can be approved."}
        </p>
      </div>
    </div>
  );
}

export { RevisionFeedbackBanner as RejectionFeedbackBanner };
