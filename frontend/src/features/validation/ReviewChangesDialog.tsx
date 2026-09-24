import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { proofreadText, type ProofreadIssue } from "../../api/aiApi";
import ProofreadIssues from "./ProofreadIssues";

/** One changed field, before → after, as shown before saving. */
export interface EditChangeRow {
  key: string;
  label: string;
  before: string;
  after: string;
  /** Put the field back to its last saved value. Omitted for rows that can't be undone here (media). */
  onUndo?: () => void;
}

/**
 * "Review your changes" — shown when a reviewer saves an edit, so an accidental
 * deletion or a paste into the wrong field is caught before it's saved, not
 * afterwards in the review history. When the caption changed, the edit is
 * proofread against the contributor's original (only mistakes the edit
 * introduced, plus changed names/dates/numbers). The check is advisory: it
 * never blocks saving, and a failed check just says so.
 */
export default function ReviewChangesDialog({
  changes,
  caption,
  originalCaption,
  captionChanged,
  isLiveEvent,
  saving,
  onApplyCaptionFix,
  onSave,
  onClose,
}: {
  changes: EditChangeRow[];
  caption: string;
  originalCaption: string;
  captionChanged: boolean;
  isLiveEvent: boolean;
  saving: boolean;
  onApplyCaptionFix: (issue: ProofreadIssue) => void;
  onSave: () => void;
  onClose: () => void;
}) {
  const [checkState, setCheckState] = useState<"idle" | "loading" | "done" | "error">(
    captionChanged ? "loading" : "idle",
  );
  const [issues, setIssues] = useState<ProofreadIssue[]>([]);
  const [checkError, setCheckError] = useState("");

  // Check the edited caption once, when the dialog opens.
  useEffect(() => {
    if (!captionChanged) return;
    const controller = new AbortController();
    proofreadText(caption, originalCaption, controller.signal)
      .then((found) => {
        setIssues(found);
        setCheckState("done");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setCheckError(error instanceof Error ? error.message : "");
        setCheckState("error");
      });
    return () => controller.abort();
    // Only on open: applying a fix changes `caption`, which must not re-run the check.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !saving) onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, saving]);

  const openIssues = issues.filter((issue) => caption.includes(issue.excerpt));
  const nothingChanged = changes.length === 0;

  return createPortal(
    <div
      className="val-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="val-review-changes-title"
      onClick={() => !saving && onClose()}
    >
      <div className="val-review-changes" onClick={(e) => e.stopPropagation()}>
        <header className="val-review-changes-head">
          <h3 id="val-review-changes-title">Review your changes</h3>
          <p>
            {nothingChanged
              ? "Nothing has changed since the last save."
              : `${changes.length} change${changes.length === 1 ? "" : "s"} to this post. The contributor will be told what you edited.`}
          </p>
        </header>

        <div className="val-review-changes-body">
          {isLiveEvent && !nothingChanged && (
            <div className="val-edit-callout is-error">
              <i className="ti ti-bolt" aria-hidden="true" />
              <span>
                This is a Live Event — it publishes the moment it&apos;s approved, so the contributor
                won&apos;t see your edits before they go live.
              </span>
            </div>
          )}

          {!nothingChanged && (
            <ul className="val-change-list">
              {changes.map((change) => (
                <li key={change.key} className="val-change-row">
                  <div className="val-change-head">
                    <strong>{change.label}</strong>
                    {change.onUndo && (
                      <button type="button" onClick={change.onUndo} disabled={saving}>
                        <i className="ti ti-arrow-back-up" aria-hidden="true" /> Undo
                      </button>
                    )}
                  </div>
                  <div className="val-change-values">
                    <p className="is-before">
                      <span>Before</span>
                      {change.before || <em>empty</em>}
                    </p>
                    <p className="is-after">
                      <span>After</span>
                      {change.after || <em>empty</em>}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}

          {captionChanged && (
            <section className="val-change-check" aria-live="polite">
              <h4>
                <i className="ti ti-sparkles" aria-hidden="true" /> Writing check
              </h4>
              {checkState === "loading" && (
                <p className="val-change-check-status">
                  <i className="ti ti-loader-2 val-spin" aria-hidden="true" /> Checking your caption edit…
                </p>
              )}
              {checkState === "error" && (
                <p className="val-change-check-status">
                  Couldn&apos;t check the writing{checkError === "rate-limit" ? " — hourly limit reached" : ""}.
                  You can still save.
                </p>
              )}
              {checkState === "done" && openIssues.length === 0 && (
                <p className="val-change-check-status is-ok">
                  <i className="ti ti-circle-check" aria-hidden="true" /> No new mistakes found in your edit.
                </p>
              )}
              {checkState === "done" && (
                <ProofreadIssues
                  issues={issues}
                  text={caption}
                  onApply={onApplyCaptionFix}
                  onDismiss={(issue) => setIssues((prev) => prev.filter((i) => i !== issue))}
                />
              )}
              <p className="val-change-check-note">
                AI suggestions can be wrong about names and local words — nothing is changed unless you apply it.
              </p>
            </section>
          )}
        </div>

        <footer className="val-review-changes-foot">
          <button type="button" className="val-btn val-btn-secondary" onClick={onClose} disabled={saving}>
            {nothingChanged ? "Close" : "Keep editing"}
          </button>
          {!nothingChanged && (
            <button type="button" className="val-btn val-btn-primary" onClick={onSave} disabled={saving}>
              <i className={`ti ${saving ? "ti-loader-2 val-spin" : "ti-device-floppy"}`} aria-hidden="true" />
              <span>{saving ? "Saving..." : "Save changes"}</span>
            </button>
          )}
        </footer>
      </div>
    </div>,
    document.body,
  );
}
