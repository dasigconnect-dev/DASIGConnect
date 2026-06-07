import { Folder, Images, Plus, Pencil, Trash2, Activity, Copy } from "lucide-react";
import type { Folder as MediaFolder } from "../../../api/folderApi";

export type FolderFilterMode = "all" | "unfiled" | "folder";

interface FolderSidebarProps {
  folders: MediaFolder[];
  loading: boolean;
  filterMode: FolderFilterMode;
  selectedFolderId: string | null;
  allCount: number;
  unfiledCount: number;
  selectionCount: number;
  disabledReason?: string | null;
  onSelectAll: () => void;
  onSelectUnfiled: () => void;
  onSelectFolder: (id: string) => void;
  onOpenCollections?: () => void;
  onOpenTrash?: () => void;
  onOpenHealth?: () => void;
  onOpenDuplicates?: () => void;
  onCreate: () => void;
  onRename: (folder: MediaFolder) => void;
  onDelete: (folder: MediaFolder) => void;
  onMoveSelected: (folderId: string | null) => void;
}

export default function FolderSidebar({
  folders,
  loading,
  filterMode,
  selectedFolderId,
  allCount,
  unfiledCount,
  selectionCount,
  disabledReason,
  onSelectAll,
  onSelectUnfiled,
  onSelectFolder,
  onOpenCollections,
  onOpenTrash,
  onOpenHealth,
  onOpenDuplicates,
  onCreate,
  onRename,
  onDelete,
  onMoveSelected,
}: FolderSidebarProps) {
  const moving = selectionCount > 0;
  const organizationDisabled = Boolean(disabledReason);

  return (
    <aside className="fld-sidebar" aria-label="Media library organization">
      <div className="fld-head">
        <span className="fld-head-title">Organization</span>
      </div>

      <nav className="fld-nav">
        <button
          type="button"
          className={`fld-item${filterMode === "all" ? " active" : ""}`}
          onClick={onSelectAll}
        >
          <span className="fld-item-label">All media</span>
          <span className="fld-count">{allCount}</span>
        </button>

        <div className="fld-row">
          <button
            type="button"
            className={`fld-item${filterMode === "unfiled" ? " active" : ""}`}
            onClick={onSelectUnfiled}
          >
            <span className="fld-item-label">Needs organization</span>
            <span className="fld-count">{unfiledCount}</span>
          </button>
          {moving && !organizationDisabled && (
            <button className="fld-move-btn" type="button" title="Move selected out of folders" onClick={() => onMoveSelected(null)}>
              Move here
            </button>
          )}
        </div>

        {onOpenCollections && (
          <button type="button" className="fld-item fld-collection-link" onClick={onOpenCollections}>
            <Images size={15} aria-hidden="true" />
            <span className="fld-item-label">Collections</span>
          </button>
        )}

        {onOpenTrash && (
          <button type="button" className="fld-item fld-collection-link" onClick={onOpenTrash}>
            <Trash2 size={15} aria-hidden="true" />
            <span className="fld-item-label">Trash</span>
          </button>
        )}

        {onOpenHealth && (
          <button type="button" className="fld-item fld-collection-link" onClick={onOpenHealth}>
            <Activity size={15} aria-hidden="true" />
            <span className="fld-item-label">Repository Health</span>
          </button>
        )}

        {onOpenDuplicates && (
          <button type="button" className="fld-item fld-collection-link" onClick={onOpenDuplicates}>
            <Copy size={15} aria-hidden="true" />
            <span className="fld-item-label">Duplicate Review</span>
          </button>
        )}

        <div className="fld-divider" />

        <div className="fld-section-head">
          <span>Storage folders</span>
          <button
            className="fld-add"
            type="button"
            aria-label="New folder"
            title="New folder"
            disabled={organizationDisabled}
            onClick={onCreate}
          >
            <Plus size={16} aria-hidden="true" />
          </button>
        </div>

        {organizationDisabled ? (
          <p className="fld-empty">{disabledReason}</p>
        ) : moving ? (
          <p className="fld-move-hint">Moving {selectionCount} selected. Pick a storage folder.</p>
        ) : null}

        {loading ? (
          <p className="fld-empty">Loading...</p>
        ) : folders.length === 0 && !organizationDisabled ? (
          <p className="fld-empty">No folders yet.</p>
        ) : (
          folders.map((folder) => (
            <div key={folder.id} className="fld-row">
              <button
                type="button"
                className={`fld-item${filterMode === "folder" && selectedFolderId === folder.id ? " active" : ""}`}
                onClick={() => onSelectFolder(folder.id)}
                title={folder.name}
              >
                <Folder className="fld-icon" size={15} aria-hidden="true" />
                <span className="fld-item-label">{folder.name}</span>
                <span className="fld-count">{folder.assetCount}</span>
              </button>
              {moving ? (
                <button className="fld-move-btn" type="button" title={`Move selected to ${folder.name}`} onClick={() => onMoveSelected(folder.id)}>
                  Move here
                </button>
              ) : (
                <span className="fld-actions">
                  <button type="button" aria-label={`Rename ${folder.name}`} title="Rename" onClick={() => onRename(folder)}>
                    <Pencil size={13} aria-hidden="true" />
                  </button>
                  <button type="button" aria-label={`Delete ${folder.name}`} title="Delete" onClick={() => onDelete(folder)}>
                    <Trash2 size={13} aria-hidden="true" />
                  </button>
                </span>
              )}
            </div>
          ))
        )}
      </nav>
    </aside>
  );
}
