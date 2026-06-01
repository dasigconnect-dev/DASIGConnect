import { useState } from "react";

interface CreateAlbumModalProps {
  creating: boolean;
  onCancel: () => void;
  onCreate: (name: string, description: string) => void;
}

export default function CreateAlbumModal({ creating, onCancel, onCreate }: CreateAlbumModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  return (
    <div
      className="alb-modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="Create collection"
      onClick={() => { if (!creating) onCancel(); }}
    >
      <div className="alb-modal" onClick={(e) => e.stopPropagation()}>
        <h2 className="alb-modal-title">New Collection</h2>
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
            {creating ? "Creating..." : "Create"}
          </button>
        </div>
      </div>
    </div>
  );
}
