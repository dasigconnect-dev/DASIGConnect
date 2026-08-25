import type { SavedMediaAsset, SubmissionStatus } from "../../api/submissionApi";

export interface FormState {
  id: string | null;
  status: SubmissionStatus;
  institutionId: string;
  selectedTemplateId: string | null;
  fastTrack: boolean;
  liveEventName: string;
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  category: string;
  scheduledDate: string;
  scheduledTime: string;
  tags: string[];
  albumName: string;
  mediaTags: string[];
  files: File[];
  savedAssets: SavedMediaAsset[];
  mediaOrder: string[];
  mediaCaptions: Record<string, string>;
  mediaSkipWatermark: Record<string, boolean>;
  pendingAssetIds: string[];
  removedAssetIds: string[];
}

export type QueueFilter = "drafts" | "submitted" | "published" | "failed" | "all";
export type ModalState =
  | "submit"
  | "success"
  | "delete"
  | "withdraw"
  | "fast-track-switch"
  | "draft-choice"
  | "draft-exit"
  | null;
export type SaveState = "idle" | "saving" | "saved";
export type PendingLeaveAction = (() => void) | null;
export type ProgressStep = "media" | "details" | "schedule";
export type CenterMode = "edit" | "preview";

export type ReadinessTarget =
  | "eventTitle"
  | "eventDate"
  | "caption"
  | "media"
  | "fileRequirements"
  | "schedule"
  | "album"
  | "captionLength"
  | "tags"
  | "mediaTags"
  | "mediaCaptions"
  | "template";

export interface ReadinessCheck {
  title: string;
  sub: string;
  pass: boolean;
  idle?: boolean;
  target: ReadinessTarget;
}
