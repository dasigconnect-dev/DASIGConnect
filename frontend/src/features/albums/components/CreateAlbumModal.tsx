import { useState } from "react";

interface CreateAlbumModalProps {
  creating: boolean;
  onCancel: () => void;
  onCreate: (name: string, description: string) => void;
  /** Edit mode: prefill + relabel. Omit for create. */
  initialName?: string;
  initialDescription?: string;
  title?: string;
  submitLabel?: string;
  busyLabel?: string;
}

/** Create or edit a collection (reused for both via optional initial values). */
export default function CreateAlbumModal({
  creating,
  onCancel,
  onCreate,
  initialName = "",
  initialDescription = "",
  title = "New Collection",
  submitLabel = "Create",
  busyLabel = "Creating...",
}: CreateAlbumModalProps) {
  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription);

  return (
    <div
      className="alb-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => { if (!creating) onCancel(); }}
    >
      <div className="alb-modal" onClick={(e) => e.stopPropagation()}>
        <h2 className="alb-modal-title">{title}</h2>
        <label className="alb-field">
          <span>Name</span>
          <input
            autoFocus
            value={name}
            maxLength={150}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Graduation 2026 highlights"
          />
        </label>
        <label className="alb-field">
          <span>Description <em>(optional)</em></span>
          <textarea value={description} maxLength={1000} rows={3} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <div className="alb-modal-actions">
          <button className="med-btn med-btn-ghost med-btn-sm" type="button" disabled={creating} onClick={onCancel}>
            Cancel
          </button>
          <button
            className="med-btn med-btn-primary med-btn-sm"
            type="button"
            disabled={creating || !name.trim()}
            onClick={() => onCreate(name, description)}
          >
            {creating ? busyLabel : submitLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
