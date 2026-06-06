import { createPortal } from "react-dom";
import { useMemo, useRef, useState } from "react";
import type { MediaDuplicateMatch } from "../../../api/mediaApi";

const MAX_PARALLEL_UPLOADS = 3;

type UploadStatus = "pending" | "uploading" | "success" | "failed";

interface UploadOptions {
  silent?: boolean;
  importBatchId?: string | null;
}

interface UploadItem {
  id: string;
  file: File;
  status: UploadStatus;
  progress: number;
  error?: string;
  /** Exact-duplicate match in the library (same bytes), if any. */
  duplicateOf?: { assetCode: string; fileName: string } | null;
  /** User chose to upload anyway despite the duplicate. */
  includeAnyway?: boolean;
}

interface UploadModalProps {
  open: boolean;
  institutionName: string;
  onClose: () => void;
  onUpload: (file: File, onProgress?: (pct: number) => void, options?: UploadOptions) => Promise<void>;
  onCreateBatch?: (assetCount: number) => Promise<string | null>;
  onBatchComplete?: (importBatchId: string | null) => void;
  /** Optional exact-duplicate (SHA-256) check run before upload. */
  onCheckDuplicates?: (sha256s: string[]) => Promise<MediaDuplicateMatch[]>;
}

/** Hex SHA-256 of a file's bytes, matching the backend's stored content_sha256. */
async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest("SHA-256", buffer);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export default function UploadModal({ open, institutionName, onClose, onUpload, onCreateBatch, onBatchComplete, onCheckDuplicates }: UploadModalProps) {
  const [dragOver, setDragOver] = useState(false);
  const [uploadItems, setUploadItems] = useState<UploadItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [checkingDuplicates, setCheckingDuplicates] = useState(false);
  const [activeBatchId, setActiveBatchId] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  function handleFilesSelect(files: File[]) {
    setActiveBatchId(null);
    const items: UploadItem[] = files.map((file, index) => ({
      id: `${file.name}-${file.lastModified}-${file.size}-${index}`,
      file,
      status: "pending",
      progress: 0,
      duplicateOf: null,
    }));
    setUploadItems(items);
    if (onCheckDuplicates && crypto?.subtle) {
      void flagDuplicates(items);
    }
  }

  async function flagDuplicates(items: UploadItem[]) {
    setCheckingDuplicates(true);
    try {
      const hashed = await Promise.all(
        items.map(async (it) => ({ id: it.id, sha256: await sha256Hex(it.file) })),
      );
      const matches = await onCheckDuplicates!(hashed.map((h) => h.sha256));
      const existingBySha = new Map(matches.map((m) => [m.sha256, m.existingAsset]));
      setUploadItems((prev) =>
        prev.map((it) => {
          const h = hashed.find((x) => x.id === it.id);
          const existing = h ? existingBySha.get(h.sha256) : undefined;
          return existing
            ? { ...it, duplicateOf: { assetCode: existing.assetCode, fileName: existing.fileName } }
            : it;
        }),
      );
    } catch {
      // Best-effort: if the check fails, fall through and allow upload.
    } finally {
      setCheckingDuplicates(false);
    }
  }

  function uploadAnyway(id: string) {
    setUploadItems((prev) => prev.map((it) => (it.id === id ? { ...it, includeAnyway: true } : it)));
  }

  function includeAllDuplicates() {
    setUploadItems((prev) => prev.map((it) => (it.duplicateOf ? { ...it, includeAnyway: true } : it)));
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    setDragOver(false);
    const files = Array.from(e.dataTransfer.files);
    if (files.length > 0) handleFilesSelect(files);
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? []);
    if (files.length > 0) handleFilesSelect(files);
    e.target.value = "";
  }

  function updateItem(id: string, patch: Partial<UploadItem>) {
    setUploadItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  async function uploadOne(item: UploadItem, importBatchId: string | null) {
    updateItem(item.id, { status: "uploading", progress: 0, error: undefined });
    try {
      await onUpload(
        item.file,
        (pct) => updateItem(item.id, { progress: pct }),
        { silent: true, importBatchId },
      );
      updateItem(item.id, { status: "success", progress: 100, error: undefined });
      return true;
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Upload failed.";
      updateItem(item.id, { status: "failed", progress: 0, error: message });
      return false;
    }
  }

  async function runUploadBatch(items: UploadItem[]) {
    if (items.length === 0) return;
    setUploading(true);
    try {
      let importBatchId = activeBatchId;
      if (!importBatchId && onCreateBatch) {
        importBatchId = await onCreateBatch(items.length);
        setActiveBatchId(importBatchId);
      }

      let nextIndex = 0;
      const results: boolean[] = [];
      const workerCount = Math.min(MAX_PARALLEL_UPLOADS, items.length);
      const workers = Array.from({ length: workerCount }, async () => {
        while (nextIndex < items.length) {
          const item = items[nextIndex];
          nextIndex += 1;
          results.push(await uploadOne(item, importBatchId));
        }
      });
      await Promise.all(workers);

      const hasFailure = results.some((ok) => !ok);
      if (results.some(Boolean)) {
        onBatchComplete?.(importBatchId);
      }
      if (!hasFailure) {
        setTimeout(() => {
          setUploadItems([]);
          setActiveBatchId(null);
          setUploading(false);
          onClose();
        }, 700);
      } else {
        setUploading(false);
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Could not create the upload batch.";
      items.forEach((item) => updateItem(item.id, { status: "failed", progress: 0, error: message }));
      setUploading(false);
    }
  }

  function isSkippedDuplicate(item: UploadItem) {
    return Boolean(item.duplicateOf) && !item.includeAnyway;
  }

  function handleUpload() {
    const runnable = uploadItems.filter(
      (item) => (item.status === "pending" || item.status === "failed") && !isSkippedDuplicate(item),
    );
    void runUploadBatch(runnable);
  }

  function retryFailed() {
    const failed = uploadItems.filter((item) => item.status === "failed");
    void runUploadBatch(failed);
  }

  function handleClose() {
    if (uploading) return;
    setUploadItems([]);
    setActiveBatchId(null);
    onClose();
  }

  const selectedCount = uploadItems.length;
  const successCount = uploadItems.filter((item) => item.status === "success").length;
  const failedCount = uploadItems.filter((item) => item.status === "failed").length;
  const uploadingCount = uploadItems.filter((item) => item.status === "uploading").length;
  const pendingCount = uploadItems.filter((item) => item.status === "pending").length;
  const skippedDuplicateCount = uploadItems.filter(isSkippedDuplicate).length;
  const uploadableCount = uploadItems.filter(
    (item) => (item.status === "pending" || item.status === "failed") && !isSkippedDuplicate(item),
  ).length;
  const progress = useMemo(() => {
    if (uploadItems.length === 0) return 0;
    const total = uploadItems.reduce((sum, item) => {
      if (item.status === "success") return sum + 100;
      if (item.status === "failed") return sum;
      return sum + item.progress;
    }, 0);
    return Math.round(total / uploadItems.length);
  }, [uploadItems]);
  const uploadLabel = uploadableCount > 1 ? `Upload ${uploadableCount} files` : "Upload file";
  const footerLabel = failedCount > 0
    ? `${successCount} uploaded, ${failedCount} failed`
    : uploading
      ? `${successCount} uploaded, ${uploadingCount} uploading, ${pendingCount} waiting`
      : `${selectedCount} selected`;

  const modal = (
    <div className={`med-modal-overlay${open ? " open" : ""}`} onClick={(e) => { if (e.target === e.currentTarget) handleClose(); }}>
      <div className="med-modal-card" role="dialog" aria-modal="true" aria-label="Upload media">
        <div className="med-modal-header">
          <span className="med-modal-title">Upload media</span>
          <button className="med-modal-close" onClick={handleClose} type="button" aria-label="Close">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          {selectedCount === 0 ? (
            <div
              className={`med-dropzone${dragOver ? " drag-over" : ""}`}
              onClick={() => inputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
            >
              <div className="med-dropzone-icon">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="16,16 12,12 8,16" />
                  <line x1="12" y1="12" x2="12" y2="21" />
                  <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
                </svg>
              </div>
              <div className="med-dropzone-title">
                Drop files here or <span className="med-dropzone-link">browse</span>
              </div>
              <div className="med-dropzone-sub">Upload directly to the institutional media library</div>
              <input
                ref={inputRef}
                type="file"
                multiple
                style={{ display: "none" }}
                accept=".jpg,.jpeg,.png,.gif,.mp4,.mov,.webm,.webp"
                onChange={handleInputChange}
              />
            </div>
          ) : (
            <div>
              <div className="med-upload-summary">
                <div>
                  <strong>{footerLabel}</strong>
                  <span>Uploads run {MAX_PARALLEL_UPLOADS} files at a time.</span>
                </div>
                <span>{progress}%</span>
              </div>
              <div className="med-upload-progress-bar" style={{ marginTop: 12 }}>
                <div className="med-upload-progress-fill" style={{ width: `${progress}%` }} />
              </div>
              {checkingDuplicates && (
                <div className="med-upload-dupe-note">Checking your library for exact duplicates…</div>
              )}
              {!checkingDuplicates && skippedDuplicateCount > 0 && (
                <div className="med-upload-dupe-note warn">
                  {skippedDuplicateCount} file{skippedDuplicateCount > 1 ? "s are" : " is"} already in your
                  library and will be skipped.{" "}
                  <button type="button" className="med-upload-dupe-link" onClick={includeAllDuplicates}>
                    Upload {skippedDuplicateCount > 1 ? "them" : "it"} anyway
                  </button>
                </div>
              )}
              <div className="med-upload-list">
                {uploadItems.map((item) => {
                const skipped = isSkippedDuplicate(item);
                return (
                <div className={`med-upload-file-row status-${item.status}${skipped ? " is-duplicate" : ""}`} key={item.id}>
                  <div className="med-upload-file-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2" />
                      <circle cx="8.5" cy="8.5" r="1.5" />
                      <polyline points="21,15 16,10 5,21" />
                    </svg>
                  </div>
                  <div className="med-upload-file-info">
                    <div className="med-upload-file-name">{item.file.name}</div>
                    <div style={{ fontSize: 11, color: "var(--med-muted)", marginTop: 2 }}>
                      {(item.file.size / (1024 * 1024)).toFixed(1)} MB
                      {item.error ? ` - ${item.error}` : ""}
                      {item.duplicateOf ? ` · already in library as ${item.duplicateOf.assetCode}` : ""}
                    </div>
                  </div>
                  {skipped ? (
                    <button type="button" className="med-upload-dupe-link" onClick={() => uploadAnyway(item.id)}>
                      Upload anyway
                    </button>
                  ) : (
                    <div className={`med-upload-status-pill status-${item.status}`}>
                      {item.duplicateOf && item.includeAnyway ? "Duplicate" : statusLabel(item)}
                    </div>
                  )}
                </div>
                );
              })}
              </div>
            </div>
          )}

          <div className="med-upload-specs">
            <div className="med-spec-item">
              <div className="med-spec-label">Accepted Formats</div>
              <div className="med-spec-val">JPG, PNG, GIF, MP4, MOV, WEBP</div>
            </div>
            <div className="med-spec-item">
              <div className="med-spec-label">Max File Size</div>
              <div className="med-spec-val">50 MB per file</div>
            </div>
            <div className="med-spec-item">
              <div className="med-spec-label">Classification</div>
              <div className="med-spec-val">AI tags applied after upload</div>
            </div>
          </div>

          <p style={{ fontSize: 12, color: "var(--med-muted)", marginTop: 16, lineHeight: 1.6 }}>
            Uploaded media is scoped to {institutionName}. AI classification runs after upload.
          </p>
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" onClick={handleClose} type="button" disabled={uploading}>
            Cancel
          </button>
          <button
            className="med-btn med-btn-primary"
            onClick={failedCount > 0 && pendingCount === 0 && !uploading ? retryFailed : handleUpload}
            type="button"
            disabled={uploadableCount === 0 || uploading || checkingDuplicates}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="16,16 12,12 8,16" />
              <line x1="12" y1="12" x2="12" y2="21" />
              <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
            </svg>
            {uploading ? "Uploading..." : failedCount > 0 && pendingCount === 0 ? `Retry ${failedCount} failed` : uploadLabel}
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}

function statusLabel(item: UploadItem) {
  if (item.status === "uploading") return `${Math.max(1, item.progress)}%`;
  if (item.status === "success") return "Uploaded";
  if (item.status === "failed") return "Failed";
  return "Waiting";
}
