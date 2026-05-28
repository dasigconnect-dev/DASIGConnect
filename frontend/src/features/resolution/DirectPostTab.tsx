import { useEffect, useState } from "react";
import { api } from "../../api/authApi";
import { createDirectPost } from "../../api/resolutionApi";
import { useToast } from "../../context/ToastContext";
import type { InstitutionResponse } from "../../api/authApi";

const CAPTION_MIN = 80;
const CAPTION_MAX = 280;
const REASON_MIN = 20;

function nowPlusTwoHours() {
  return new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString().slice(0, 16);
}

export default function DirectPostTab() {
  const toast = useToast();
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [institutionId, setInstitutionId] = useState("");
  const [caption, setCaption] = useState("");
  const [publishImmediately, setPublishImmediately] = useState(true);
  const [scheduledAt, setScheduledAt] = useState(nowPlusTwoHours());
  const [reason, setReason] = useState("");
  const [acknowledgedGrH1, setAcknowledgedGrH1] = useState(false);
  const [busy, setBusy] = useState(false);
  const [grH1Warning, setGrH1Warning] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    api.get<InstitutionResponse[]>("/institutions")
      .then((res) => setInstitutions(res.data.filter((i) => i.status === "ACTIVE")))
      .catch(() => {});
  }, []);

  const captionLen = caption.length;
  const reasonLen = reason.trim().length;
  const captionValid = captionLen >= CAPTION_MIN && captionLen <= CAPTION_MAX;
  const reasonValid = reasonLen >= REASON_MIN;
  const scheduleValid = publishImmediately || scheduledAt !== "";
  const canSubmit =
    institutionId !== "" &&
    captionValid &&
    reasonValid &&
    scheduleValid &&
    !busy;

  async function handleSubmit() {
    if (!canSubmit) return;
    setBusy(true);
    setGrH1Warning(false);
    try {
      const payload = {
        institutionId,
        caption,
        mediaAssetIds: [] as string[],
        publishImmediately,
        scheduledAt: publishImmediately ? undefined : new Date(scheduledAt).toISOString(),
        reason: reason.trim(),
        acknowledgedGrH1Conflict: acknowledgedGrH1,
      };
      const res = await createDirectPost(payload);
      if (res.data.grH1ConflictWarning) {
        setGrH1Warning(true);
      }
      setSubmitted(true);
      toast.success(
        publishImmediately
          ? "Direct post submitted — publishing to Facebook now."
          : "Direct post scheduled successfully.",
      );
      // reset form
      setInstitutionId("");
      setCaption("");
      setReason("");
      setPublishImmediately(true);
      setScheduledAt(nowPlusTwoHours());
      setAcknowledgedGrH1(false);
    } catch {
      toast.error("Direct post failed. Please check your inputs and try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="rc-direct-post">
      {submitted && grH1Warning && (
        <div className="rc-alert rc-alert-amber">
          <i className="ti ti-alert-triangle" aria-hidden="true" />
          <span>
            <strong>GR-H1 conflict noted.</strong> Another post from this institution is
            scheduled within 30 minutes. The direct post was created but the conflict has
            been logged.
          </span>
          <button type="button" className="rc-alert-close" onClick={() => setGrH1Warning(false)} aria-label="Dismiss">
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
      )}

      <div className="rc-dp-grid">
        {/* ── Left: Form ── */}
        <div className="rc-dp-form">
          <div className="rc-field">
            <label className="rc-label" htmlFor="dp-institution">
              Attribution institution <span className="rc-required">*</span>
            </label>
            <select
              id="dp-institution"
              className="rc-select"
              value={institutionId}
              onChange={(e) => setInstitutionId(e.target.value)}
            >
              <option value="">Select institution...</option>
              {institutions.map((i) => (
                <option key={i.id} value={i.id}>{i.name}</option>
              ))}
            </select>
            <span className="rc-hint">All direct posts are attributed to a specific institution.</span>
          </div>

          <div className="rc-field">
            <label className="rc-label" htmlFor="dp-caption">
              Post caption <span className="rc-required">*</span>
              <span className={`rc-char-count ${captionLen > CAPTION_MAX ? "rc-char-over" : captionLen >= CAPTION_MIN ? "rc-char-ok" : ""}`}>
                {captionLen}/{CAPTION_MAX}
              </span>
            </label>
            <textarea
              id="dp-caption"
              className={`rc-textarea rc-textarea-lg ${captionLen > 0 && !captionValid ? "rc-input-error" : ""}`}
              rows={5}
              placeholder="Write the post caption (80–280 characters)…"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              maxLength={CAPTION_MAX + 50}
            />
            {captionLen > 0 && captionLen < CAPTION_MIN && (
              <span className="rc-field-error">Minimum {CAPTION_MIN} characters required.</span>
            )}
            {captionLen > CAPTION_MAX && (
              <span className="rc-field-error">Caption exceeds {CAPTION_MAX} character limit.</span>
            )}
          </div>

          <div className="rc-field">
            <label className="rc-label">Publication timing <span className="rc-required">*</span></label>
            <div className="rc-toggle-group">
              <button
                type="button"
                className={`rc-toggle-btn${publishImmediately ? " rc-toggle-active" : ""}`}
                onClick={() => setPublishImmediately(true)}
              >
                <i className="ti ti-bolt" aria-hidden="true" />
                Publish Immediately
              </button>
              <button
                type="button"
                className={`rc-toggle-btn${!publishImmediately ? " rc-toggle-active" : ""}`}
                onClick={() => setPublishImmediately(false)}
              >
                <i className="ti ti-calendar-clock" aria-hidden="true" />
                Schedule for Later
              </button>
            </div>
            {!publishImmediately && (
              <input
                type="datetime-local"
                className="rc-input"
                min={nowPlusTwoHours()}
                value={scheduledAt}
                onChange={(e) => setScheduledAt(e.target.value)}
              />
            )}
          </div>

          <div className="rc-field">
            <label className="rc-label" htmlFor="dp-reason">
              Justification for bypass <span className="rc-required">*</span>
              <span className={`rc-char-count ${reasonLen >= REASON_MIN ? "rc-char-ok" : ""}`}>
                {reasonLen}/{REASON_MIN} min
              </span>
            </label>
            <textarea
              id="dp-reason"
              className={`rc-textarea ${reason.length > 0 && !reasonValid ? "rc-input-error" : ""}`}
              rows={3}
              placeholder="Required: explain why the standard submission workflow is being bypassed (min 20 characters)…"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            {reason.length > 0 && !reasonValid && (
              <span className="rc-field-error">Minimum {REASON_MIN} characters required.</span>
            )}
          </div>

          <div className="rc-dp-guardrail-note">
            <i className="ti ti-info-circle" aria-hidden="true" />
            <div>
              <strong>Guard rail applicability:</strong> Hard rules GR-H2 (2-hour lead time) and
              GR-H3 (slot conflict) apply. GR-H1 (daily post limit) generates a warning but does
              not block the direct post. All guard rail acknowledgements are written to the immutable
              audit log.
            </div>
          </div>

          <div className="rc-field rc-field-checkbox">
            <input
              id="dp-ack-gr"
              type="checkbox"
              className="rc-checkbox"
              checked={acknowledgedGrH1}
              onChange={(e) => setAcknowledgedGrH1(e.target.checked)}
            />
            <label htmlFor="dp-ack-gr" className="rc-checkbox-label">
              I acknowledge that this post may exceed the daily post limit (GR-H1) and accept
              the logged conflict.
            </label>
          </div>

          <div className="rc-dp-actions">
            <button
              type="button"
              className="btn-primary rc-dp-submit"
              disabled={!canSubmit}
              onClick={() => void handleSubmit()}
            >
              {busy ? (
                <><div className="spinner-ring spinner-ring-sm" /> Processing...</>
              ) : publishImmediately ? (
                <><i className="ti ti-bolt" aria-hidden="true" /> Publish Now</>
              ) : (
                <><i className="ti ti-calendar-clock" aria-hidden="true" /> Schedule Direct Post</>
              )}
            </button>
          </div>
        </div>

        {/* ── Right: Info panel ── */}
        <div className="rc-dp-info">
          <div className="rc-dp-info-card">
            <div className="rc-dp-info-title">
              <i className="ti ti-info-circle" aria-hidden="true" />
              About Direct Posts
            </div>
            <ul className="rc-dp-info-list">
              <li>Bypasses the standard Contributor → Validator → Administrator workflow entirely.</li>
              <li>Reserved for crisis, emergency, or strategic scenarios.</li>
              <li>Every direct post is immutably logged with your identity, content, timing, and reason.</li>
              <li>Institution Validators are notified upon successful publication.</li>
              <li>On API failure, the post appears in the API Failures tab for retry or manual fallback.</li>
            </ul>
          </div>

          {caption && (
            <div className="rc-dp-info-card rc-dp-preview-card">
              <div className="rc-dp-info-title">
                <i className="ti ti-brand-facebook" aria-hidden="true" />
                Preview
              </div>
              <div className="rc-dp-preview-body">
                <div className="rc-dp-preview-header">
                  <div className="rc-dp-preview-avatar" aria-hidden="true">D</div>
                  <div>
                    <div className="rc-dp-preview-page">DASIG Connect</div>
                    <div className="rc-dp-preview-time">
                      {publishImmediately
                        ? "Just now"
                        : scheduledAt
                          ? new Date(scheduledAt).toLocaleDateString("en-PH", { month: "short", day: "numeric" })
                          : "Scheduled"}
                    </div>
                  </div>
                </div>
                <p className="rc-dp-preview-caption">{caption}</p>
                {institutionId && (
                  <div className="rc-dp-preview-attr">
                    Posted on behalf of{" "}
                    <strong>
                      {institutions.find((i) => i.id === institutionId)?.name ?? "—"}
                    </strong>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
