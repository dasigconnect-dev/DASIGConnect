import { createPortal } from "react-dom";
import { useMemo, useRef, useState } from "react";
import type { MediaAlbum } from "../../../api/mediaApi";
import type { InstitutionResponse } from "../../../api/authApi";
import AlbumCombobox from "../../../components/ui/AlbumCombobox";

export interface UploadMetadata {
  albumId?: string | null;
  albumName?: string;
  autoMatchAlbum: boolean;
  tags: string[];
  /** Institution the upload is scoped to — resolved from the open folder or the modal's picker. */
  institutionId: string | null;
}

interface UploadModalProps {
  open: boolean;
  institutionName: string;
  onClose: () => void;
  albums: MediaAlbum[];
  /** The folder currently open in the repository — offered as the default upload target. */
  currentAlbum?: MediaAlbum | null;
  /** Admin only — lets the user pick an institution when none is in context. */
  institutions?: InstitutionResponse[];
  /** Institution to scope album selection/creation to when no folder is open. */
  defaultInstitutionId?: string | null;
  onCreateAlbum: (name: string, institutionId: string, parentAlbumId: string | null) => Promise<MediaAlbum>;
  onUpload: (file: File, metadata: UploadMetadata, onProgress?: (pct: number) => void) => Promise<void>;
}

const ACCEPTED_EXTENSIONS = new Set(["jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "webm"]);
const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

export default function UploadModal({
  open,
  institutionName,
  onClose,
  albums,
  currentAlbum,
  institutions = [],
  defaultInstitutionId,
  onCreateAlbum,
  onUpload,
}: UploadModalProps) {
  const [dragOver, setDragOver] = useState(false);
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [progress, setProgress] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [albumInput, setAlbumInput] = useState("");
  const [autoMatched, setAutoMatched] = useState(false);
  const [useCurrentAlbum, setUseCurrentAlbum] = useState(true);
  const [pickedInstId, setPickedInstId] = useState("");
  const [tagsInput, setTagsInput] = useState("");
  const [inlineError, setInlineError] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  const tags = useMemo(
    () => tagsInput.split(",").map((tag) => tag.trim()).filter(Boolean),
    [tagsInput],
  );

  const institutionCodeById = useMemo(() => {
    const map = new Map<string, string>();
    for (const inst of institutions) map.set(inst.id, inst.institutionCode);
    return map;
  }, [institutions]);

  const usingCurrentAlbum = Boolean(currentAlbum && useCurrentAlbum);
  // Which institution album selection/creation is scoped to.
  const effectiveInstId =
    defaultInstitutionId || currentAlbum?.institutionId || pickedInstId || "";
  // Only ask for an institution when there is genuinely no context (admin "All institutions" root).
  const needInstitutionPick = !defaultInstitutionId && !currentAlbum;

  const scopedAlbums = useMemo(
    () => (effectiveInstId ? albums.filter((a) => a.institutionId === effectiveInstId) : albums),
    [albums, effectiveInstId],
  );
  const albumNames = useMemo(() => scopedAlbums.map((album) => album.name), [scopedAlbums]);
  const albumBadges = useMemo(() => {
    const map: Record<string, string> = {};
    for (const a of scopedAlbums) {
      const code = institutionCodeById.get(a.institutionId);
      if (code) map[a.name] = code;
    }
    return map;
  }, [scopedAlbums, institutionCodeById]);

  const resolvedExistingAlbum = useMemo(() => {
    const trimmed = albumInput.trim().toLowerCase();
    if (!trimmed) return null;
    return scopedAlbums.find((album) => album.name.trim().toLowerCase() === trimmed) ?? null;
  }, [albumInput, scopedAlbums]);

  const autoMatchedAlbum = useMemo(() => {
    const cues = new Set(tags.map((tag) => tag.toLowerCase()));
    for (const file of selectedFiles) {
      const name = file.name.toLowerCase();
      name
        .replace(/\.[^.]+$/, "")
        .split(/[^a-z0-9]+/i)
        .map((part) => part.trim().toLowerCase())
        .filter(Boolean)
        .forEach((part) => cues.add(part));
    }

    return scopedAlbums.find((album) => {
      const albumName = album.name.trim().toLowerCase();
      if (!albumName) return false;
      const albumWords = albumName.split(/[^a-z0-9]+/i).filter(Boolean);
      return [...cues].some((cue) =>
        cue === albumName ||
        albumName.includes(cue) ||
        cue.includes(albumName) ||
        albumWords.some((word) => word === cue),
      );
    }) ?? null;
  }, [scopedAlbums, selectedFiles, tags]);

  const fileError = useMemo(() => {
    for (const file of selectedFiles) {
      const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
      if (!ACCEPTED_EXTENSIONS.has(ext)) {
        return `${file.name} is unsupported. Accepted formats: JPG, PNG, WEBP, GIF, MP4, MOV, WEBM.`;
      }
      if (file.size > MAX_UPLOAD_BYTES) {
        return `${file.name} is ${(file.size / (1024 * 1024)).toFixed(1)} MB, over the 50 MB limit.`;
      }
    }
    return "";
  }, [selectedFiles]);

  const metadataError = useMemo(() => {
    if (!usingCurrentAlbum) {
      if (needInstitutionPick && !pickedInstId) return "Select an institution.";
      if (!albumInput.trim()) return "Select an existing folder or type a name to create one.";
    }
    if (tags.length === 0) return "Add at least one media tag.";
    return "";
  }, [usingCurrentAlbum, needInstitutionPick, pickedInstId, albumInput, tags.length]);

  const canUpload = selectedFiles.length > 0 && !fileError && !metadataError && !uploading;

  function handleFilesSelect(files: File[]) {
    setSelectedFiles(files);
    setProgress(0);
    setInlineError("");
  }

  function resetForm() {
    setDragOver(false);
    setSelectedFiles([]);
    setProgress(0);
    setInlineError("");
    setAlbumInput("");
    setAutoMatched(false);
    setUseCurrentAlbum(true);
    setPickedInstId("");
    setTagsInput("");
  }

  function handleAlbumChange(value: string) {
    setAlbumInput(value);
    setAutoMatched(false);
    setInlineError("");
  }

  function handleAutoMatchAlbum() {
    if (autoMatchedAlbum) {
      setAlbumInput(autoMatchedAlbum.name);
      setAutoMatched(true);
      setInlineError("");
    } else {
      setInlineError("No confident folder match from tags or filenames. Type a folder name instead.");
    }
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

  async function handleUpload() {
    if (selectedFiles.length === 0) return;
    if (fileError || metadataError) {
      setInlineError(fileError || metadataError);
      return;
    }
    setUploading(true);
    setProgress(0);
    try {
      let albumId: string | null | undefined;
      let albumName: string;
      let autoMatchAlbum = false;
      let institutionId: string | null;

      if (usingCurrentAlbum && currentAlbum) {
        albumId = currentAlbum.id;
        albumName = currentAlbum.name;
        institutionId = currentAlbum.institutionId;
      } else if (resolvedExistingAlbum) {
        albumId = resolvedExistingAlbum.id;
        albumName = resolvedExistingAlbum.name;
        autoMatchAlbum = autoMatched;
        institutionId = resolvedExistingAlbum.institutionId;
      } else {
        if (!effectiveInstId) {
          setUploading(false);
          setInlineError("Select an institution before creating a folder.");
          return;
        }
        // New folder is created at the current location (open folder, else institution root).
        const parentId = currentAlbum ? currentAlbum.id : null;
        const created = await onCreateAlbum(albumInput.trim(), effectiveInstId, parentId);
        albumId = created.id;
        albumName = created.name;
        institutionId = effectiveInstId;
      }

      const metadata: UploadMetadata = { albumId, albumName, autoMatchAlbum, tags, institutionId };
      const total = selectedFiles.length;
      for (const [index, file] of selectedFiles.entries()) {
        const completedBase = (index / total) * 100;
        await onUpload(file, metadata, (pct) => {
          setProgress(Math.round(completedBase + pct / total));
        });
      }
      setProgress(100);
      setTimeout(() => {
        resetForm();
        setUploading(false);
        onClose();
      }, 600);
    } catch (err) {
      console.error("[UploadModal] upload failed:", err);
      setUploading(false);
      setProgress(0);
      const detail = err instanceof Error ? err.message : String(err);
      setInlineError(`Upload failed: ${detail}`);
    }
  }

  function handleClose() {
    if (uploading) return;
    resetForm();
    onClose();
  }

  const selectedCount = selectedFiles.length;
  const uploadLabel = selectedCount > 1 ? `Upload ${selectedCount} Assets` : "Upload Asset";

  const isCreatingNewAlbum = Boolean(albumInput.trim()) && !resolvedExistingAlbum;
  const effectiveInstName = effectiveInstId
    ? institutions.find((i) => i.id === effectiveInstId)?.name ?? ""
    : "";
  const newAlbumLocationLabel = currentAlbum
    ? `inside “${currentAlbum.name}”`
    : effectiveInstName
      ? `at the top level of ${effectiveInstName}`
      : "at the top level";

  if (!open) return null;

  const modal = (
    <div className={`med-modal-overlay${open ? " open" : ""}`} onClick={(e) => { if (e.target === e.currentTarget) handleClose(); }}>
      <div className="med-modal-card" role="dialog" aria-modal="true" aria-label="Upload Asset">
        <div className="med-modal-header">
          <span className="med-modal-title">Upload Asset to Library</span>
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
                Drop files here or <span className="med-dropzone-link">browse multiple assets</span>
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
              {selectedFiles.map((file) => (
                <div className="med-upload-file-row" key={`${file.name}-${file.lastModified}-${file.size}`}>
                  <div className="med-upload-file-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2" />
                      <circle cx="8.5" cy="8.5" r="1.5" />
                      <polyline points="21,15 16,10 5,21" />
                    </svg>
                  </div>
                  <div className="med-upload-file-info">
                    <div className="med-upload-file-name">{file.name}</div>
                    <div style={{ fontSize: 11, color: "var(--med-muted)", marginTop: 2 }}>
                      {(file.size / (1024 * 1024)).toFixed(1)} MB
                    </div>
                  </div>
                </div>
              ))}
              <div className="med-upload-progress-bar" style={{ marginTop: 12 }}>
                <div className="med-upload-progress-fill" style={{ width: `${progress}%` }} />
              </div>
              <div style={{ fontSize: 12, fontWeight: 700, color: "var(--med-blue)", marginTop: 8, textAlign: "right" }}>
                {progress > 0 ? `${progress}%` : `${selectedCount} selected`}
              </div>
            </div>
          )}

          {selectedCount > 0 && (
            <div className="med-upload-organize">
              <div className="med-upload-section-title">Folder</div>

              {usingCurrentAlbum && currentAlbum ? (
                <div className="med-upload-note success med-upload-current">
                  <span>Uploading to <strong>{currentAlbum.name}</strong>.</span>
                  <button
                    type="button"
                    className="med-upload-current-x"
                    aria-label="Choose a different folder"
                    disabled={uploading}
                    onClick={() => setUseCurrentAlbum(false)}
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                      <line x1="18" y1="6" x2="6" y2="18" />
                      <line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                  </button>
                </div>
              ) : (
                <>
                  {needInstitutionPick && (
                    <select
                      className="med-upload-input"
                      value={pickedInstId}
                      disabled={uploading}
                      onChange={(e) => { setPickedInstId(e.target.value); handleAlbumChange(""); }}
                      style={{ marginBottom: 8 }}
                    >
                      <option value="">Select institution</option>
                      {institutions.map((inst) => (
                        <option key={inst.id} value={inst.id}>{inst.name}</option>
                      ))}
                    </select>
                  )}

                  <AlbumCombobox
                    value={albumInput}
                    existingAlbums={albumNames}
                    albumBadges={albumBadges}
                    readOnly={uploading || (needInstitutionPick && !pickedInstId)}
                    placeholder="Search, select, or create a folder"
                    autoMatchLabel="Auto-Match from Tags & Filenames"
                    createHint={isCreatingNewAlbum ? `Will be created ${newAlbumLocationLabel}` : undefined}
                    onChange={handleAlbumChange}
                    onAutoMatch={handleAutoMatchAlbum}
                  />

                  {autoMatched && resolvedExistingAlbum ? (
                    <div className="med-upload-note success">
                      Auto-matched to <strong>{resolvedExistingAlbum.name}</strong>. Upload will use this folder.
                    </div>
                  ) : isCreatingNewAlbum ? (
                    <div className="med-upload-note">
                      New folder <strong>"{albumInput.trim()}"</strong> will be created {newAlbumLocationLabel}.
                    </div>
                  ) : null}

                  {currentAlbum && (
                    <button
                      type="button"
                      className="med-inline-link"
                      disabled={uploading}
                      onClick={() => setUseCurrentAlbum(true)}
                    >
                      ↩ Upload to “{currentAlbum.name}” instead
                    </button>
                  )}
                </>
              )}

              <div className="med-upload-section-title with-tip">
                Tags
                <span className="med-upload-tip" title="Tip: using the event name as a tag improves search results.">?</span>
              </div>
              <input
                className="med-upload-input"
                value={tagsInput}
                onChange={(event) => setTagsInput(event.target.value)}
                placeholder="Enter tags separated by commas"
                disabled={uploading}
              />
              {tags.length > 0 && (
                <div className="med-upload-tags">
                  {tags.map((tag) => <span key={tag}>{tag}</span>)}
                </div>
              )}
            </div>
          )}

          {(fileError || inlineError) && (
            <div className="med-upload-error">{inlineError || fileError}</div>
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
            Uploaded assets are scoped to the selected institution ({institutionName}) and immediately available in the Media Library. AI classification runs asynchronously and may take up to 60 seconds.
          </p>
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" onClick={handleClose} type="button" disabled={uploading}>
            Cancel
          </button>
          <button
            className="med-btn med-btn-primary"
            onClick={() => void handleUpload()}
            type="button"
            disabled={!canUpload}
            aria-disabled={!canUpload}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="16,16 12,12 8,16" />
              <line x1="12" y1="12" x2="12" y2="21" />
              <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
            </svg>
            {uploading ? "Uploading..." : uploadLabel}
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
