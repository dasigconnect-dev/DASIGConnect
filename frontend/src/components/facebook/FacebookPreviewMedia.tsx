import type { FacebookPreviewMediaItem } from "../../types/facebook";
import FacebookPreviewMediaCarousel from "./FacebookPreviewMediaCarousel";

interface FacebookPreviewMediaProps {
  mediaItems: FacebookPreviewMediaItem[];
  activeIndex?: number;
  onActiveIndexChange?: (index: number) => void;
  size?: "compact" | "large";
  showWatermark?: boolean;
}

export default function FacebookPreviewMedia({
  mediaItems,
  activeIndex = 0,
  onActiveIndexChange,
  size = "compact",
  showWatermark = true,
}: FacebookPreviewMediaProps) {
  return (
    <FacebookPreviewMediaCarousel
      mediaItems={mediaItems}
      activeIndex={activeIndex}
      onActiveIndexChange={onActiveIndexChange}
      size={size}
      showWatermark={showWatermark}
    />
  );
}
