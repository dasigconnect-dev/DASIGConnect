import { useState } from "react";
import type { MediaAlbum } from "../../../api/mediaApi";

interface AlbumCardProps {
  album: MediaAlbum;
  animationDelay?: number;
  canManage: boolean;
  institutionCode?: string;
  institutionLogoUrl?: string | null;
  /** Horizontal table-row layout instead of the grid tile. */
  listView?: boolean;
  onOpen: () => void;
  onRename: () => void;
  onMove: () => void;
  onDelete: () => void;
}

const FOLDER_ICON = (
  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
    <path d="M3 7a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
  </svg>
);

export default function AlbumCard({
  album,
  animationDelay = 0,
  canManage,
  institutionCode,
  institutionLogoUrl,
  listView = false,
  onOpen,
  onRename,
  onMove,
  onDelete,
}: AlbumCardProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const isEmpty = album.childAlbumCount === 0 && album.assetCount === 0;
  // Only an explicit `false` from the API blocks delete; if the field is absent
  // (older backend build) fall back to letting the server decide.
  const mayDelete = album.canDelete !== false;

  const parts: string[] = [];
  if (album.childAlbumCount > 0) {
    parts.push(`${album.childAlbumCount} folder${album.childAlbumCount === 1 ? "" : "s"}`);
  }
  if (album.assetCount > 0) {
    parts.push(`${album.assetCount} item${album.assetCount === 1 ? "" : "s"}`);
  }
  const meta = parts.length > 0 ? parts.join(" · ") : "Empty";

  const deleteTitle = !mayDelete
    ? "Only an moderator or the folder's creator can delete it"
    : !isEmpty
      ? "Move or delete everything inside this folder first"
      : undefined;

  const menu = canManage && (
    <div className="med-folder-menu-wrap">
      <button
        className="med-folder-kebab"
        type="button"
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        aria-label={`${album.name} actions`}
        onClick={() => setMenuOpen((v) => !v)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="12" cy="5" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="12" cy="19" r="1.6" />
        </svg>
      </button>
      {menuOpen && (
        <div className="med-folder-menu" role="menu">
          <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onOpen(); }}>Open</button>
          <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onRename(); }}>Rename</button>
          <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onMove(); }}>Move to…</button>
          <button
            type="button"
            role="menuitem"
            className="med-folder-menu-danger"
            disabled={!mayDelete || !isEmpty}
            title={deleteTitle}
            onClick={() => { setMenuOpen(false); onDelete(); }}
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );

  if (listView) {
    return (
      <div
        className="med-folder-card list-view"
        style={{ animationDelay: `${animationDelay}ms` }}
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node)) setMenuOpen(false);
        }}
      >
        <button className="med-folder-row-open" type="button" onClick={onOpen} aria-label={`Open ${album.name}`}>
          <span className="med-folder-icon">{FOLDER_ICON}</span>
          <span className="med-folder-name">{album.name}</span>
        </button>
        <span className="med-folder-row-meta">{meta}</span>
        {institutionCode && <span className="med-folder-inst-badge inline">{institutionCode}</span>}
        {institutionLogoUrl ? (
          <img className="med-folder-row-logo" src={institutionLogoUrl} alt="" aria-hidden="true" />
        ) : (
          <span className="med-folder-row-logo placeholder" aria-hidden="true" />
        )}
        {menu}
      </div>
    );
  }

  return (
    <div
      className={`med-folder-card${institutionCode ? " has-inst" : ""}`}
      style={{ animationDelay: `${animationDelay}ms` }}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setMenuOpen(false);
      }}
    >
      {institutionLogoUrl && (
        <img className="med-folder-inst-watermark" src={institutionLogoUrl} alt="" aria-hidden="true" />
      )}
      {institutionCode && <span className="med-folder-inst-badge">{institutionCode}</span>}

      <button className="med-folder-open" type="button" onClick={onOpen} aria-label={`Open ${album.name}`}>
        <span className="med-folder-icon">{FOLDER_ICON}</span>
        <span className="med-folder-name">{album.name}</span>
        <span className="med-folder-meta">{meta}</span>
      </button>

      {menu}
    </div>
  );
}
