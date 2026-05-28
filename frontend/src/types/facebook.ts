export type FacebookPreviewMediaType = "image" | "video" | "unknown";

export interface FacebookPreviewMediaItem {
  id: string;
  source: "saved" | "local";
  sourceId: string;
  url: string;
  type: FacebookPreviewMediaType;
  alt: string;
  fileName?: string;
}

export interface FacebookPreviewPage {
  name?: string;
  avatarUrl?: string;
}

export type FacebookPreviewDetailTone = "ok" | "warn" | "error" | "muted";

export interface FacebookPreviewDetailItem {
  label: string;
  value: string;
  tone?: FacebookPreviewDetailTone;
}

export interface FacebookPreviewDetailsData {
  statusLabel: string;
  readinessScore: number;
  completionLabel: string;
  category: string;
  institution: string;
  tags: string[];
  schedule: string;
  fileCount: number;
  fileValidation: FacebookPreviewDetailItem;
  slotConfirmation: FacebookPreviewDetailItem;
  aiCaptionAssist: FacebookPreviewDetailItem;
  validatorNotes: string;
  missingItems: string[];
}
