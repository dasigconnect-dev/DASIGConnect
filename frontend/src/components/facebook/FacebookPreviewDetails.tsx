import type { FacebookPreviewDetailsData } from "../../types/facebook";

interface FacebookPreviewDetailsProps {
  details: FacebookPreviewDetailsData;
}

export default function FacebookPreviewDetails({
  details,
}: FacebookPreviewDetailsProps) {
  const isReady = details.missingItems.length === 0;

  return (
    <section className="fb-preview-details" aria-labelledby="fb-preview-details-title">
      <div className="fb-preview-details-head">
        <div>
          <h3 id="fb-preview-details-title">Submission Details</h3>
          <p>Confirm readiness before sending this content to your validator.</p>
        </div>
        <span className="fb-preview-status">{details.statusLabel}</span>
      </div>

      <div className={`fb-preview-readiness ${isReady ? "ready" : "needs-work"}`} role="status">
        <div className="fb-preview-readiness-ring" aria-hidden="true">
          <span>{details.readinessScore}</span>
        </div>
        <div>
          <strong>{details.completionLabel}</strong>
          <p>
            {isReady
              ? "Everything needed for review is in place."
              : "Complete the remaining required fields before submission."}
          </p>
        </div>
      </div>

      {!isReady && (
        <div className="fb-preview-validation-summary">
          <span>Required before review</span>
          <ul>
            {details.missingItems.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="fb-preview-detail-grid">
        <Detail label="Preferred schedule" value={details.schedule} emphasis />
        <Detail
          label={details.fileValidation.label}
          value={details.fileValidation.value}
          tone={details.fileValidation.tone}
          emphasis
        />
        <Detail
          label={details.slotConfirmation.label}
          value={details.slotConfirmation.value}
          tone={details.slotConfirmation.tone}
          emphasis
        />
        <Detail label="Files attached" value={`${details.fileCount} file(s)`} />
        <Detail label="Event category" value={details.category} />
        <Detail label="Institution scope" value={details.institution} quiet />
        <Detail
          label={details.aiCaptionAssist.label}
          value={details.aiCaptionAssist.value}
          tone={details.aiCaptionAssist.tone}
          quiet
        />
      </div>

      <div className="fb-preview-supporting">
        <div className="fb-preview-detail-block">
          <span>Tags</span>
          {details.tags.length > 0 ? (
            <div className="fb-preview-tag-row">
              {details.tags.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
          ) : (
            <p>No tags selected.</p>
          )}
        </div>

        <div className="fb-preview-detail-block">
          <span>Validator notes</span>
          <p>{details.validatorNotes || "No validator notes added."}</p>
        </div>
      </div>
    </section>
  );
}

function Detail({
  label,
  value,
  tone = "muted",
  emphasis = false,
  quiet = false,
}: {
  label: string;
  value: string;
  tone?: "ok" | "warn" | "error" | "muted";
  emphasis?: boolean;
  quiet?: boolean;
}) {
  return (
    <div
      className={`fb-preview-detail-item ${tone} ${emphasis ? "emphasis" : ""} ${quiet ? "quiet" : ""}`}
    >
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
