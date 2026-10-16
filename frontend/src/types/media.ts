export type SubmissionMediaSource = "upload" | "library" | "ai";

export interface SubmissionMediaItem {
  clientId: string;
  source: SubmissionMediaSource;
  assetId?: string;
  file?: File;
  previewUrl: string;
  mediaType: "image" | "video";
  fileName: string;
  /** Media Library album the asset is filed under — only known for saved assets. */
  albumName?: string | null;
  aiCategory?: string;
  similarityScore?: number;
}
