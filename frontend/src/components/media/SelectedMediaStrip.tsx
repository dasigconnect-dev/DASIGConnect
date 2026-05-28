import { useRef, useState } from "react";
import type { SubmissionMediaItem } from "../../types/media";

interface SelectedMediaStripProps {
  items: SubmissionMediaItem[];
  disabled?: boolean;
  onRemove: (clientId: string) => void;
  onReorder: (fromIndex: number, toIndex: number) => void;
}

const SOURCE_LABELS: Record<string, string> = {
  upload: "Uploaded",
  library: "Library",
  ai: "AI Pick",
};

export default function SelectedMediaStrip({
  items,
  disabled = false,
  onRemove,
  onReorder,
}: SelectedMediaStripProps) {
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const dragNodeRef = useRef<HTMLDivElement | null>(null);

  function handleDragStart(e: React.DragEvent<HTMLDivElement>, index: number) {
    if (disabled) return;
    setDragIndex(index);
    dragNodeRef.current = e.currentTarget;
    e.dataTransfer.effectAllowed = "move";
  }

  function handleDragOver(e: React.DragEvent<HTMLDivElement>, index: number) {
    if (disabled) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
    if (dragOverIndex !== index) setDragOverIndex(index);
  }

  function handleDrop(index: number) {
    if (disabled) return;
    if (dragIndex !== null && dragIndex !== index) {
      onReorder(dragIndex, index);
    }
    setDragIndex(null);
    setDragOverIndex(null);
  }

  function handleDragEnd() {
    setDragIndex(null);
    setDragOverIndex(null);
  }

  if (items.length === 0) {
    return (
      <div className="sms-empty" aria-live="polite">
        <i className="ti ti-photo" aria-hidden />
        <span>{disabled ? "No media attached." : "No media selected - add files below."}</span>
      </div>
    );
  }

  return (
    <div className={`sms-root${disabled ? " sms-root--readonly" : ""}`} aria-label="Selected media">
      <div className="sms-strip" role="list">
        {items.map((item, index) => {
          const isDragging = dragIndex === index;
          const isOver = dragOverIndex === index && dragIndex !== index;
          return (
            <div
              key={item.clientId}
              className={[
                "sms-item",
                disabled ? "sms-item--readonly" : "",
                isDragging ? "sms-item--dragging" : "",
                isOver ? "sms-item--drag-over" : "",
              ]
                .filter(Boolean)
                .join(" ")}
              draggable={!disabled}
              onDragStart={(e) => handleDragStart(e, index)}
              onDragOver={(e) => handleDragOver(e, index)}
              onDrop={() => handleDrop(index)}
              onDragEnd={handleDragEnd}
              role="listitem"
              aria-label={`${item.fileName}, position ${index + 1} of ${items.length}`}
            >
              <div className="sms-thumb">
                {item.mediaType === "video" ? (
                  <div className="sms-video-thumb">
                    {item.previewUrl ? (
                      <video
                        src={item.previewUrl}
                        className="sms-video"
                        muted
                        playsInline
                        preload="metadata"
                        draggable={false}
                        aria-label={item.fileName}
                      />
                    ) : (
                      <i className="ti ti-video" aria-hidden />
                    )}
                    <span className="sms-video-play" aria-hidden>
                      <i className="ti ti-player-play-filled" />
                    </span>
                  </div>
                ) : (
                  <img
                    src={item.previewUrl}
                    alt={item.fileName}
                    className="sms-img"
                    draggable={false}
                  />
                )}
                <span className="sms-order-badge" aria-hidden>{index + 1}</span>
                <span className={`sms-source-badge sms-source-badge--${item.source}`} aria-hidden>
                  {SOURCE_LABELS[item.source]}
                </span>
                {!disabled && (
                  <button
                    type="button"
                    className="sms-remove-btn"
                    onClick={() => onRemove(item.clientId)}
                    aria-label={`Remove ${item.fileName}`}
                    title="Remove"
                  >
                    <i className="ti ti-x" aria-hidden />
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
      {!disabled && (
        <p className="sms-hint" aria-hidden>
          {items.length} selected - drag to reorder. First item is the preview cover.
        </p>
      )}
    </div>
  );
}
