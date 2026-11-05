import { useState } from "react";
import type { AspectRatioType, ElementRatioConfig, WatermarkElement } from "../../types/watermark.types";
import { resolveRatioConfig } from "./watermarkLayout";
import { textElementStyle } from "./watermarkStyles";

const FALLBACK_LOGO = "/dasig-logo.png";

/** Painted content of one element. Shared by the studio canvas and the post-preview overlay. */
export default function WatermarkElementContent({
  el,
  ratio,
}: {
  el: WatermarkElement;
  ratio?: AspectRatioType;
}) {
  const cfg = ratio ? resolveRatioConfig(el, ratio) : null;

  switch (el.type) {
    case "image":
      return <ImageContent el={el} />;
    case "text":
      return <TextContent el={el} cfg={cfg} />;
    case "shape":
      return <ShapeContent el={el} cfg={cfg} />;
    default:
      return null;
  }
}

function ImageContent({ el }: { el: WatermarkElement }) {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <div className="wm-image-fallback" aria-hidden="true">
        DASIG
      </div>
    );
  }

  return (
    <img
      src={el.imageUrl?.trim() || FALLBACK_LOGO}
      alt=""
      draggable={false}
      onError={() => setFailed(true)}
      className="wm-content-fill wm-content-image"
    />
  );
}

function TextContent({ el, cfg }: { el: WatermarkElement; cfg?: ElementRatioConfig | null }) {
  return (
    <div className="wm-content-fill wm-content-text" style={textElementStyle(el, cfg)}>
      {el.text}
    </div>
  );
}

function ShapeContent({ el, cfg }: { el: WatermarkElement; cfg?: ElementRatioConfig | null }) {
  const isLine = el.shapeType === "line";
  const isGradient = el.shapeType === "gradient";
  const stroke = (cfg?.strokeColor ?? el.strokeColor)?.trim();
  const fill = cfg?.fillColor ?? el.fillColor;
  const hasStroke =
    !isLine && !!stroke && stroke !== "transparent" && stroke.toLowerCase() !== "#ffffff";

  let background: string;
  if (isLine) background = stroke || "#ffffff";
  else if (isGradient) background = `linear-gradient(to bottom, transparent 0%, ${fill || "#07112a"} 100%)`;
  else background = fill || "rgba(15, 23, 42, 0.75)";

  return (
    <div
      className="wm-content-fill"
      style={{
        background,
        borderRadius: isLine ? 0 : 8,
        border: hasStroke ? `2px solid ${stroke}` : "none",
        boxSizing: "border-box",
      }}
    />
  );
}
