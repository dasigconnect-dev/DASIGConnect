import { createPortal } from "react-dom";
import type { DeleteTier } from "../types";
import type { MediaAsset, MediaUsage } from "../../../api/mediaApi";
import { formatUploadDate, formatFileSize } from "../utils";

interface DeleteModalProps {
  open: boolean;
  tier: DeleteTier | null;
  asset: MediaAsset | null;
  blockingUsages: MediaUsage[];
  warningUsages: MediaUsage[];
  deleting: boolean;
  onClose: () => void;
  onConfirmDelete: () => void;
}

const submissionStatusBadge: Record<string, string> = {
  published: "med-badge-published",
  scheduled: "med-badge-scheduled",
  draft: "med-badge-draft",
  pending: "med-badge-pending",
  in_review: "med-badge-processing",
  needs_revision: "med-badge-revision",
  rejected: "med-badge-revision",
};

const submissionStatusLabel: Record<string, string> = {
  published: "Published",
  scheduled: "Scheduled",
  draft: "Draft",
  pending: "Pending",
  in_review: "In Review",
  needs_revision: "Needs Revision",
  rejected: "Rejected",
};

export default function DeleteModal({
  open,
  tier,
  asset,
  blockingUsages,
  warningUsages,
  deleting,
  onClose,
  onConfirmDelete,
}: DeleteModalProps) {
  const modal = (
    <div
      className={`med-modal-overlay${open ? " open" : ""}`}
      onClick={(e) => { if (e.target === e.currentTarget && !deleting) onClose(); }}
    >
      <div className="med-modal-card" role="dialog" aria-modal="true">
        <div className="med-modal-header">
          <span className="med-modal-title">{modalTitle(tier)}</span>
          <button className="med-modal-close" onClick={onClose} type="button" disabled={deleting} aria-label="Close">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          {tier === "blocked" && (
            <BlockedBody usage={blockingUsages[0] ?? null} />
          )}
          {tier === "warning" && (
            <WarningBody usages={warningUsages} />
          )}
          {tier === "free" && asset && (
            <FreeBody asset={asset} />
          )}
        </div>

        <div className="med-modal-footer">
          {tier === "blocked" ? (
            <button className="med-btn med-btn-ghost" onClick={onClose} type="button">Close</button>
          ) : tier === "warning" ? (
            <>
              <button className="med-btn med-btn-ghost" onClick={onClose} type="button" disabled={deleting}>Cancel</button>
              <button className="med-btn med-btn-warn-confirm" onClick={onConfirmDelete} type="button" disabled={deleting}>
                {deleting ? "Deleting…" : "Delete & Flag Reference"}
              </button>
            </>
          ) : (
            <>
              <button className="med-btn med-btn-ghost" onClick={onClose} type="button" disabled={deleting}>Cancel</button>
              <button className="med-btn med-btn-danger" onClick={onConfirmDelete} type="button" disabled={deleting}>
                {deleting ? "Deleting…" : "Delete Asset"}
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}

function BlockedBody({ usage }: { usage: MediaUsage | null }) {
  return (
    <>
      <div className="med-delete-blocked-banner">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#EF4444" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 2 }}>
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <div>
          <div className="med-banner-title error">Deletion Blocked — Active Submission Reference</div>
          <div className="med-banner-sub">
            This asset is referenced in a submission that is currently <strong>SCHEDULED</strong> for publishing. It cannot be deleted until the submission reaches a terminal state.
          </div>
        </div>
      </div>
      {usage && (
        <>
          <p style={{ fontSize: 13, color: "var(--med-text-2)", marginBottom: 16, lineHeight: 1.6 }}>
            The following active submission is blocking this deletion:
          </p>
          <div className="med-conflict-ref">
            <div>
              <div className="med-conflict-title">{usage.submissionTitle}</div>
              <div style={{ marginTop: 4 }}>
                <span className={`med-badge ${submissionStatusBadge[usage.submissionStatus] ?? "med-badge-tag"}`}>
                  {submissionStatusLabel[usage.submissionStatus] ?? usage.submissionStatus}
                </span>
              </div>
              <div className="med-conflict-sub">Submitted {formatUploadDate(usage.submittedAt)}</div>
            </div>
            <a className="med-jump-link" href={`/submissions/${usage.submissionId}`}>
              Open Submission
              <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                <polyline points="15,3 21,3 21,9" />
                <line x1="10" y1="14" x2="21" y2="3" />
              </svg>
            </a>
          </div>
        </>
      )}
      <p style={{ fontSize: 12, color: "var(--med-muted)", marginTop: 16, lineHeight: 1.5 }}>
        No deletion occurs. The physical media file is retained. This action has been logged.
      </p>
    </>
  );
}

function WarningBody({ usages }: { usages: MediaUsage[] }) {
  return (
    <>
      <div className="med-delete-warning-banner">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#D97706" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 2 }}>
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
        <div>
          <div className="med-banner-title warn">Warning — This Asset Has Active Draft References</div>
          <div className="med-banner-sub">
            Deleting this asset will break its reference in {usages.length} DRAFT submission{usages.length !== 1 ? "s" : ""}. The contributor will be prompted to replace the asset before submitting.
          </div>
        </div>
      </div>
      <p style={{ fontSize: 13, color: "var(--med-text-2)", marginBottom: 16, lineHeight: 1.6 }}>
        The following DRAFT submission{usages.length !== 1 ? "s" : ""} will have a broken asset reference after deletion:
      </p>
      {usages.map((usage) => (
        <div key={usage.submissionId} className="med-conflict-ref" style={{ marginBottom: 8 }}>
          <div>
            <div className="med-conflict-title">{usage.submissionTitle}</div>
            <div style={{ marginTop: 4 }}>
              <span className="med-badge med-badge-draft">Draft</span>
            </div>
            <div className="med-conflict-sub">Submitted {formatUploadDate(usage.submittedAt)}</div>
          </div>
          <a className="med-jump-link" href={`/submissions/${usage.submissionId}`}>
            View Draft
            <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
              <polyline points="15,3 21,3 21,9" />
              <line x1="10" y1="14" x2="21" y2="3" />
            </svg>
          </a>
        </div>
      ))}
      <p style={{ fontSize: 12, color: "var(--med-muted)", marginTop: 16, lineHeight: 1.5 }}>
        The asset's Used In block will be retained for audit purposes. Administrator deletions are logged in the immutable access audit log.
      </p>
    </>
  );
}

function FreeBody({ asset }: { asset: MediaAsset }) {
  return (
    <>
      <div className="med-delete-info-banner">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#10B981" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 2 }}>
          <path d="M9 11l3 3L22 4" />
          <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" />
        </svg>
        <div>
          <div className="med-banner-title ok">No Active References — Safe to Delete</div>
          <div className="med-banner-sub">
            This asset is only referenced in terminal-state submissions or has no submission references. Deletion will not affect any active workflows.
          </div>
        </div>
      </div>
      <p style={{ fontSize: 13, color: "var(--med-text-2)", marginBottom: 12, lineHeight: 1.6 }}>
        You are about to permanently remove this asset from the media library:
      </p>
      <div className="med-conflict-ref" style={{ marginBottom: 16 }}>
        <div>
          <div className="med-conflict-title">{asset.title}</div>
          <div className="med-conflict-sub">
            {asset.code} · {formatFileSize(asset.fileSizeBytes)} · {asset.fileType.toUpperCase()} · Uploaded {formatUploadDate(asset.uploadedAt)}
          </div>
        </div>
      </div>
      <p style={{ fontSize: 12, color: "var(--med-muted)", lineHeight: 1.5 }}>
        Referenced terminal submissions will show "[Asset Deleted]" in their Used In records. The physical media file is retained in storage during the pilot period. This deletion will be logged.
      </p>
    </>
  );
}

function modalTitle(tier: DeleteTier | null) {
  if (tier === "blocked") return "Cannot Delete Asset";
  if (tier === "warning") return "Delete Asset with Warning";
  return "Confirm Asset Deletion";
}
