import { createPortal } from "react-dom";
import type { SubmissionSummary } from "../../../api/submissionApi";

interface AddToDraftModalProps {
  open: boolean;
  assetCount: number;
  drafts: SubmissionSummary[];
  loading: boolean;
  busyDraftId: string | null;
  onClose: () => void;
  onSelectDraft: (draftId: string) => void;
  onNewPostInstead: () => void;
}

export default function AddToDraftModal({
  open,
  assetCount,
  drafts,
  loading,
  busyDraftId,
  onClose,
  onSelectDraft,
  onNewPostInstead,
}: AddToDraftModalProps) {
  const busy = busyDraftId !== null;

  const modal = (
    <div
      className={`med-modal-overlay${open ? " open" : ""}`}
      onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose(); }}
    >
      <div className="med-modal-card" role="dialog" aria-modal="true" aria-label="Add to Draft">
        <div className="med-modal-header">
          <span className="med-modal-title">
            Add {assetCount} {assetCount === 1 ? "asset" : "assets"} to a draft
          </span>
          <button className="med-modal-close" onClick={onClose} type="button" aria-label="Close" disabled={busy}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          {loading ? (
            <div className="med-draft-empty">Loading your drafts…</div>
          ) : drafts.length === 0 ? (
            <div className="med-draft-empty">
              <div className="med-draft-empty-title">No drafts yet</div>
              <p>Start a new post with the selected media instead.</p>
              <button className="med-btn med-btn-primary" onClick={onNewPostInstead} type="button" style={{ marginTop: 16 }}>
                New Post
              </button>
            </div>
          ) : (
            <div className="med-sel-list">
              {drafts.map((draft) => (
                <div
                  key={draft.id}
                  className={`med-sel-row${busyDraftId === draft.id ? " active" : ""}`}
                  onClick={() => { if (!busy) onSelectDraft(draft.id); }}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => { if ((e.key === "Enter" || e.key === " ") && !busy) onSelectDraft(draft.id); }}
                  aria-disabled={busy}
                >
                  <div className="med-sel-thumb">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.7)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                    </svg>
                  </div>
                  <div className="med-sel-info">
                    <div className="med-sel-name">{draft.eventTitle || "Untitled draft"}</div>
                    <div className="med-sel-meta">
                      {(draft.mediaCount ?? 0)} media · {formatDraftDate(draft.eventDate)}
                    </div>
                  </div>
                  <div className="med-sel-row-right">
                    {busyDraftId === draft.id ? (
                      <span className="med-draft-spinner" aria-label="Adding" />
                    ) : (
                      <svg className="med-sel-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="9,18 15,12 9,6" />
                      </svg>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" onClick={onClose} type="button" disabled={busy}>
            Cancel
          </button>
          {drafts.length > 0 && (
            <button className="med-btn med-btn-ghost" onClick={onNewPostInstead} type="button" disabled={busy}>
              New Post instead
            </button>
          )}
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}

function formatDraftDate(value: string) {
  if (!value) return "No date";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date);
}
