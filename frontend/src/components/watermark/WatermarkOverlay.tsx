import { useEffect, useRef, useState } from "react";
import type { WatermarkElement } from "../../types/watermark.types";
import WatermarkElementContent from "./WatermarkElementContent";
import {
  DEFAULT_ASPECT_RATIO,
  computeBox,
  getSortedElementsByRatio,
  resolveRatioConfig,
} from "./watermarkLayout";
import "../../styles/watermark-content.css";

interface WatermarkOverlayProps {
  elements?: WatermarkElement[];
  className?: string;
}

/**
 * Draws the watermark on top of an image preview. It measures the surface it
 * covers, measures the actual image surface, and renders square-reference
 * element sizes against its true dimensions.
 */
export default function WatermarkOverlay({ elements, className = "" }: WatermarkOverlayProps) {
  if (!elements || elements.length === 0) return null;
  return <MeasuredOverlay elements={elements} className={className} />;
}

function MeasuredOverlay({ elements, className }: { elements: WatermarkElement[]; className: string }) {
  const surfaceRef = useRef<HTMLDivElement>(null);
  const [aspect, setAspect] = useState(1);

  useEffect(() => {
    const surface = surfaceRef.current;
    if (!surface) return;

    const measure = () => {
      const { width, height } = surface.getBoundingClientRect();
      if (width > 0 && height > 0) setAspect(width / height);
    };
    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(surface);
    return () => observer.disconnect();
  }, []);

  const ratio = DEFAULT_ASPECT_RATIO;
  const sorted = getSortedElementsByRatio(elements, ratio);

  return (
    <div ref={surfaceRef} className={`wm-overlay-surface ${className}`} aria-hidden="true">
      {sorted.map((el, index) => {
        const cfg = resolveRatioConfig(el, ratio, index);
        if (cfg.hidden) return null;

        const box = computeBox(el, ratio, aspect);
        return (
          <div
            key={el.id || index}
            className={`wm-layer wm-type-${el.type}`}
            style={{
              left: `${box.left}%`,
              top: `${box.top}%`,
              width: `${box.width}%`,
              height: `${box.height}%`,
              opacity: cfg.opacity ?? 1,
              zIndex: index + 1,
            }}
          >
            <WatermarkElementContent el={el} ratio={ratio} />
          </div>
        );
      })}
    </div>
  );
}
