import type { FacebookPreviewMediaItem } from "../../types/facebook";

interface FacebookPreviewMediaReorderProps {
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaId?: string;
  disabled?: boolean;
  onSelect: (index: number) => void;
  onReorder: (orderedIds: string[]) => void;
}

export default function FacebookPreviewMediaReorder({
  mediaItems,
  activeMediaId,
  disabled = false,
  onSelect,
  onReorder,
}: FacebookPreviewMediaReorderProps) {
  if (mediaItems.length <= 1) return null;

  function move(fromIndex: number, toIndex: number) {
    if (disabled || fromIndex === toIndex) return;
    const next = [...mediaItems];
    const [moved] = next.splice(fromIndex, 1);
    next.splice(toIndex, 0, moved);
    onReorder(next.map((item) => item.id));
    onSelect(toIndex);
  }

  return (
    <section className="fb-reorder-panel" aria-labelledby="fb-reorder-title">
      <div className="fb-reorder-head">
        <div>
          <h3 id="fb-reorder-title">Media Order</h3>
          <p>{disabled ? "Posting order is locked for submitted posts." : "Drag photos to change their posting order."}</p>
        </div>
        <span>{mediaItems.length} items</span>
      </div>
      <div className="fb-reorder-strip" aria-label="Attached media order">
        {mediaItems.map((item, index) => (
          <div
            className={[
              "fb-reorder-item",
              item.id === activeMediaId ? "active" : "",
              disabled ? "disabled" : "",
            ]
              .filter(Boolean)
              .join(" ")}
            key={item.id}
            draggable={!disabled}
            onDragStart={(event) => {
              event.stopPropagation();
              event.dataTransfer.effectAllowed = "move";
              event.dataTransfer.setData("text/plain", String(index));
            }}
            onDragOver={(event) => {
              event.preventDefault();
              event.dataTransfer.dropEffect = "move";
            }}
            onDrop={(event) => {
              event.preventDefault();
              event.stopPropagation();
              const fromIndex = Number(event.dataTransfer.getData("text/plain"));
              if (Number.isNaN(fromIndex)) return;
              move(fromIndex, index);
            }}
          >
            <button
              className="fb-reorder-thumb"
              type="button"
              aria-label={`Select media ${index + 1}`}
              onClick={() => onSelect(index)}
            >
              {item.type === "image" ? (
                <img src={item.url} alt={item.alt} />
              ) : (
                <span>
                  <i className="ti ti-video" aria-hidden="true" />
                </span>
              )}
              <b>{index + 1}</b>
            </button>
          </div>
        ))}
      </div>
    </section>
  );
}
