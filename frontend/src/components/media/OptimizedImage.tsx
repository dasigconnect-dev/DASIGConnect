import { useMemo, useState, type CSSProperties, type ImgHTMLAttributes } from "react";

type ResizeMode = "cover" | "contain" | "fill";

interface OptimizedImageProps
  extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "srcSet" | "width" | "height"> {
  src: string;
  width: number;
  height: number;
  candidateWidths?: number[];
  quality?: number;
  resize?: ResizeMode;
  transform?: boolean;
  style?: CSSProperties;
}

const SUPABASE_PUBLIC_OBJECT_SEGMENT = "/storage/v1/object/public/";
const SUPABASE_PUBLIC_RENDER_SEGMENT = "/storage/v1/render/image/public/";

export function buildOptimizedImageUrl(
  src: string,
  {
    width,
    height,
    quality = 72,
    resize = "cover",
  }: {
    width: number;
    height: number;
    quality?: number;
    resize?: ResizeMode;
  },
) {
  if (!src.includes(SUPABASE_PUBLIC_OBJECT_SEGMENT)) return null;

  try {
    const url = new URL(src);
    url.pathname = url.pathname.replace(
      SUPABASE_PUBLIC_OBJECT_SEGMENT,
      SUPABASE_PUBLIC_RENDER_SEGMENT,
    );
    url.searchParams.set("width", String(width));
    url.searchParams.set("height", String(height));
    url.searchParams.set("resize", resize);
    url.searchParams.set("quality", String(quality));
    return url.toString();
  } catch {
    return null;
  }
}

export function canTransformImageType(fileType?: string | null) {
  const type = fileType?.toLowerCase() ?? "";
  return ["jpg", "jpeg", "png", "webp"].includes(type);
}

export default function OptimizedImage({
  src,
  width,
  height,
  candidateWidths,
  quality = 72,
  resize = "cover",
  transform = true,
  loading = "lazy",
  decoding = "async",
  onError,
  style,
  ...imgProps
}: OptimizedImageProps) {
  const [useOriginal, setUseOriginal] = useState(false);

  const srcSet = useMemo(() => {
    if (!transform || useOriginal) return undefined;
    const requestedWidths = candidateWidths?.length ? candidateWidths : [width, width * 2];
    const ratio = height / width;
    const variants = requestedWidths
      .map((candidateWidth) => {
        const candidateHeight = Math.max(1, Math.round(candidateWidth * ratio));
        const optimizedUrl = buildOptimizedImageUrl(src, {
          width: candidateWidth,
          height: candidateHeight,
          quality,
          resize,
        });
        return optimizedUrl ? `${optimizedUrl} ${candidateWidth}w` : null;
      })
      .filter(Boolean);
    return variants.length > 0 ? variants.join(", ") : undefined;
  }, [candidateWidths, height, quality, resize, src, transform, useOriginal, width]);

  const optimizedSrc =
    transform && !useOriginal
      ? buildOptimizedImageUrl(src, { width, height, quality, resize }) ?? src
      : src;

  return (
    <img
      {...imgProps}
      src={optimizedSrc}
      srcSet={srcSet}
      width={width}
      height={height}
      loading={loading}
      decoding={decoding}
      style={{ aspectRatio: `${width} / ${height}`, ...style }}
      onError={(event) => {
        if (optimizedSrc !== src) {
          setUseOriginal(true);
          return;
        }
        onError?.(event);
      }}
    />
  );
}
