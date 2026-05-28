export type SubmissionMediaSource = "upload" | "library" | "ai";

export interface SubmissionMediaItem {
  clientId: string;
  source: SubmissionMediaSource;
  assetId?: string;
  file?: File;
  previewUrl: string;
  mediaType: "image" | "video";
  fileName: string;
  aiCategory?: string;
  similarityScore?: number;
}
