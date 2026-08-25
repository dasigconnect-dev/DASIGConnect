import type { WatermarkElement } from "../../types/watermark.types";

interface WatermarkOverlayProps {
  elements?: WatermarkElement[];
  className?: string;
}

export default function WatermarkOverlay({ elements, className = "" }: WatermarkOverlayProps) {
  if (!elements || elements.length === 0) return null;

  return (
    <div
      className={`wm-overlay-container ${className}`}
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        overflow: "hidden",
        zIndex: 5,
        containerType: "size",
      }}
      aria-hidden="true"
    >
      {elements.map((el, index) => {
        const isBold = el.fontWeight === "700" || el.fontWeight === "800" || el.fontWeight === "600";
        const isItalic = el.fontStyle === "italic";

        return (
          <div
            key={el.id || index}
            className={`wm-overlay-el wm-type-${el.type}`}
            style={{
              position: "absolute",
              left: `${el.xPercent}%`,
              top: `${el.yPercent}%`,
              width: `${el.widthPercent}%`,
              height: `${el.heightPercent}%`,
              opacity: el.opacity ?? 1,
              zIndex: 10 + index * 5,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              boxSizing: "border-box",
            }}
          >
            {el.type === "image" && (
              <img
                src={el.imageUrl || "/dasig-logo.png"}
                alt=""
                draggable={false}
                style={{
                  width: "100%",
                  height: "100%",
                  objectFit: "contain",
                  display: "block",
                }}
              />
            )}

            {el.type === "text" && (
              <div
                style={{
                  width: "100%",
                  height: "100%",
                  color: el.textColor || "#ffffff",
                  fontSize: `calc(${el.fontSizePercent || 3.2} * 1cqh)`,
                  fontFamily: el.fontFamily || "Inter, -apple-system, sans-serif",
                  fontWeight: isBold ? 700 : 500,
                  fontStyle: isItalic ? "italic" : "normal",
                  display: "flex",
                  alignItems: "center",
                  whiteSpace: "pre-line",
                  wordBreak: "break-word",
                  lineHeight: 1.25,
                  textShadow: "0 1px 3px rgba(0, 0, 0, 0.65), 0 2px 8px rgba(0, 0, 0, 0.35)",
                }}
              >
                {el.text}
              </div>
            )}

            {el.type === "shape" && (
              <div
                style={{
                  width: "100%",
                  height: "100%",
                  backgroundColor: el.fillColor || "transparent",
                  borderColor: el.strokeColor || "transparent",
                  borderWidth: el.shapeType === "line" ? "2px 0 0 0" : (el.strokeColor ? "2px" : "0px"),
                  borderStyle: "solid",
                  borderRadius: el.shapeType === "rectangle" ? "6px" : "0px",
                }}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
