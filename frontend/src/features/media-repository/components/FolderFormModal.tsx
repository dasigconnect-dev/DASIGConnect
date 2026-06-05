import { useState } from "react";
import { createPortal } from "react-dom";
import { Folder as FolderIcon } from "lucide-react";

interface FolderFormModalProps {
  /** "create" for a new folder, "rename" to edit an existing one. */
  mode: "create" | "rename";
  open: boolean;
  busy?: boolean;
  initialName?: string;
  onCancel: () => void;
  onSubmit: (name: string) => void;
}

/**
 * Reusable create/rename folder dialog (replaces window.prompt for folder CRUD).
 * Deletion is handled separately by the shared ConfirmDialog.
 */
export default function FolderFormModal({
  mode,
  open,
  busy = false,
  initialName = "",
  onCancel,
  onSubmit,
}: FolderFormModalProps) {
  const [name, setName] = useState(initialName);

  if (!open) return null;

  const title = mode === "create" ? "New folder" : "Rename folder";
  const submitLabel = mode === "create" ? "Create" : "Save";
  const busyLabel = mode === "create" ? "Creating..." : "Saving...";
  const trimmed = name.trim();
  const unchanged = mode === "rename" && trimmed === initialName.trim();

  function submit() {
    if (!trimmed || unchanged || busy) return;
    onSubmit(trimmed);
  }

  const modal = (
    <div
      className="fld-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => { if (!busy) onCancel(); }}
    >
      <div className="fld-modal" onClick={(e) => e.stopPropagation()}>
        <h2 className="fld-modal-title">
          <FolderIcon size={18} aria-hidden="true" />
          {title}
        </h2>
        <label className="fld-field">
          <span>Folder name</span>
          <input
            autoFocus
            value={name}
            maxLength={150}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); submit(); } }}
            placeholder="e.g. Event photos 2026"
          />
        </label>
        <div className="fld-modal-actions">
          <button className="med-btn med-btn-ghost med-btn-sm" type="button" disabled={busy} onClick={onCancel}>
            Cancel
          </button>
          <button
            className="med-btn med-btn-primary med-btn-sm"
            type="button"
            disabled={busy || !trimmed || unchanged}
            onClick={submit}
          >
            {busy ? busyLabel : submitLabel}
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}
