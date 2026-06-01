import type { Album } from "../../../api/albumApi";

interface AlbumCardProps {
  album: Album;
  onOpen: (id: string) => void;
  onDelete: (album: Album) => void;
}

/** A single collection in the list grid (presentational). */
export default function AlbumCard({ album, onOpen, onDelete }: AlbumCardProps) {
  return (
    <article className="alb-card" onClick={() => onOpen(album.id)}>
      <div className="alb-card-cover">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21,15 16,10 5,21" />
        </svg>
      </div>
      <div className="alb-card-body">
        <div className="alb-card-name">
          {album.name}
          {album.source === "ai_suggested" && <span className="alb-ai-badge">AI</span>}
        </div>
        <div className="alb-card-meta">
          {album.assetCount} {album.assetCount === 1 ? "item" : "items"}
        </div>
        {album.description && <p className="alb-card-desc">{album.description}</p>}
      </div>
      <button
        className="alb-card-delete"
        type="button"
        aria-label={`Delete collection ${album.name}`}
        onClick={(e) => {
          e.stopPropagation();
          onDelete(album);
        }}
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="3,6 5,6 21,6" />
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
        </svg>
      </button>
    </article>
  );
}
