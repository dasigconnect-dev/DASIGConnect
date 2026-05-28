import type { FacebookPreviewDetailsData } from "../../types/facebook";

interface FacebookPreviewDetailsProps {
  details: FacebookPreviewDetailsData;
}

export default function FacebookPreviewDetails({
  details,
}: FacebookPreviewDetailsProps) {
  return (
    <section className="fb-preview-details" aria-labelledby="fb-preview-details-title">
      <div className="fb-preview-details-head">
        <div>
          <h3 id="fb-preview-details-title">Submission Details</h3>
          <p>Review the fields that will travel with this Facebook post.</p>
        </div>
        <span className="fb-preview-status">{details.statusLabel}</span>
      </div>

      {details.missingItems.length > 0 && (
        <div className="fb-preview-missing" role="status">
          <i className="ti ti-alert-circle" aria-hidden="true" />
          <div>
            <strong>Needs attention before review</strong>
            <ul>
              {details.missingItems.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </div>
      )}

      <div className="fb-preview-detail-grid">
        <Detail label="Event category" value={details.category} />
        <Detail label="Institution scope" value={details.institution} />
        <Detail label="Preferred schedule" value={details.schedule} />
        <Detail label="Files attached" value={`${details.fileCount} file(s)`} />
        <Detail
          label={details.fileValidation.label}
          value={details.fileValidation.value}
          tone={details.fileValidation.tone}
        />
        <Detail
          label={details.slotConfirmation.label}
          value={details.slotConfirmation.value}
          tone={details.slotConfirmation.tone}
        />
        <Detail
          label={details.aiCaptionAssist.label}
          value={details.aiCaptionAssist.value}
          tone={details.aiCaptionAssist.tone}
        />
      </div>

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
    </section>
  );
}

function Detail({
  label,
  value,
  tone = "muted",
}: {
  label: string;
  value: string;
  tone?: "ok" | "warn" | "error" | "muted";
}) {
  return (
    <div className={`fb-preview-detail-item ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
