const VIDEO_TYPES = new Set(["mp4", "mov", "webm", "avi", "mkv"]);

export function isVideoType(fileType: string) {
  return VIDEO_TYPES.has(fileType.toLowerCase());
}

export function formatFileSize(bytes: number): string {
  if (bytes >= 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
}

export function formatUploadDate(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}

export function formatResolution(asset: { widthPx?: number; heightPx?: number; durationSeconds?: number; fileType: string }): string {
  if (isVideoType(asset.fileType) && asset.durationSeconds) {
    const mins = Math.floor(asset.durationSeconds / 60);
    const secs = asset.durationSeconds % 60;
    const dim = asset.widthPx && asset.heightPx ? `${asset.widthPx}×${asset.heightPx} · ` : "";
    return `${dim}${mins}:${secs.toString().padStart(2, "0")}`;
  }
  if (asset.widthPx && asset.heightPx) return `${asset.widthPx} × ${asset.heightPx} px`;
  return "—";
}

export function formatFileTypeName(fileType: string): string {
  const map: Record<string, string> = {
    jpeg: "JPEG Image", jpg: "JPEG Image", png: "PNG Image",
    gif: "GIF Image", webp: "WebP Image",
    mp4: "MP4 Video", mov: "MOV Video", webm: "WebM Video",
  };
  return map[fileType.toLowerCase()] ?? fileType.toUpperCase();
}
