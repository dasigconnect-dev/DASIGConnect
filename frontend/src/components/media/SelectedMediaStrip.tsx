import { useRef, useState } from "react";
import type { SubmissionMediaItem } from "../../types/media";

interface SelectedMediaStripProps {
  items: SubmissionMediaItem[];
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
  onRemove,
  onReorder,
}: SelectedMediaStripProps) {
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const dragNodeRef = useRef<HTMLDivElement | null>(null);

  function handleDragStart(e: React.DragEvent<HTMLDivElement>, index: number) {
    setDragIndex(index);
    dragNodeRef.current = e.currentTarget;
    e.dataTransfer.effectAllowed = "move";
  }

  function handleDragOver(e: React.DragEvent<HTMLDivElement>, index: number) {
    e.preventDefault();
    e.dataTransfer.dropEffect = "move";
    if (dragOverIndex !== index) setDragOverIndex(index);
  }

  function handleDrop(index: number) {
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

  function moveItem(fromIndex: number, direction: -1 | 1) {
    const toIndex = fromIndex + direction;
    if (toIndex < 0 || toIndex >= items.length) return;
    onReorder(fromIndex, toIndex);
  }

  if (items.length === 0) {
    return (
      <div className="sms-empty" aria-live="polite">
        <i className="ti ti-photo" aria-hidden />
        <span>No media selected — add files below.</span>
      </div>
    );
  }

  return (
    <div className="sms-root" aria-label="Selected media">
      <div className="sms-strip" role="list">
        {items.map((item, index) => {
          const isDragging = dragIndex === index;
          const isOver = dragOverIndex === index && dragIndex !== index;
          return (
            <div
              key={item.clientId}
              className={[
                "sms-item",
                isDragging ? "sms-item--dragging" : "",
                isOver ? "sms-item--drag-over" : "",
              ]
                .filter(Boolean)
                .join(" ")}
              draggable
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
                    <i className="ti ti-video" aria-hidden />
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
              </div>

              <div className="sms-actions">
                <button
                  type="button"
                  className="sms-action-btn"
                  onClick={() => moveItem(index, -1)}
                  disabled={index === 0}
                  aria-label={`Move ${item.fileName} left`}
                  title="Move left"
                >
                  <i className="ti ti-chevron-left" aria-hidden />
                </button>
                <button
                  type="button"
                  className="sms-action-btn"
                  onClick={() => moveItem(index, 1)}
                  disabled={index === items.length - 1}
                  aria-label={`Move ${item.fileName} right`}
                  title="Move right"
                >
                  <i className="ti ti-chevron-right" aria-hidden />
                </button>
                <button
                  type="button"
                  className="sms-action-btn sms-action-btn--remove"
                  onClick={() => onRemove(item.clientId)}
                  aria-label={`Remove ${item.fileName}`}
                  title="Remove"
                >
                  <i className="ti ti-x" aria-hidden />
                </button>
              </div>
            </div>
          );
        })}
      </div>
      <p className="sms-hint" aria-hidden>
        {items.length} selected · Drag or use arrows to reorder · First item is the preview cover
      </p>
    </div>
  );
}
