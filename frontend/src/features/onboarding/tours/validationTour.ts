import type { TourStep } from "../types";

export const validationQueueTourSteps: TourStep[] = [
  {
    target: ".val-tabs",
    title: "Queue Filters & Stages",
    description: "Filter submissions by stage: All, Pending (awaiting moderation), In Review (currently locked by an editor), or Failed (automated publishing retries).",
    icon: "ti ti-filter",
    placement: "bottom",
  },
  {
    target: ".val-search",
    title: "Search & Sort Submissions",
    description: "Instantly find submissions by title, author, or tags. Sort pending items by scheduled publishing slot or submission date.",
    icon: "ti ti-search",
    placement: "bottom",
  },
  {
    target: ".val-queue-list",
    title: "Submissions Awaiting Review",
    description: "Browse cards of all event submissions that need to be reviewed. Each card shows the scheduled countdown, author badge, and media count. Click any item to inspect its content.",
    icon: "ti ti-list-check",
    placement: "right",
  },
];

export const validationReviewTourSteps: TourStep[] = [
  {
    target: ".val-fb-card",
    title: "Live Facebook Preview",
    description: "Inspect the rendered post exactly as followers will see it on Facebook. Switch media slides, toggle watermark overlays, and check hashtags.",
    icon: "ti ti-brand-facebook",
    placement: "right",
  },
  {
    target: ".val-details-panel",
    title: "Submission Details & Audit History",
    description: "Examine contributor credentials, institution name, target publishing slot, and the full audit log of previous review actions.",
    icon: "ti ti-file-text",
    placement: "left",
  },
  {
    target: ".val-action-bar",
    title: "Start Review & Lock",
    description: "Click 'Start Review' to claim exclusive editing ownership. This locks the post for 15 minutes to prevent conflicting decisions by other moderators.",
    icon: "ti ti-lock",
    placement: "top",
  },
  {
    target: ".val-action-bar",
    title: "Moderator Decisions & Direct Editing",
    description: "Once locked, you can Approve for publication, Request Revision from the contributor, Reject with reason codes, or Edit content directly.",
    icon: "ti ti-gavel",
    placement: "top",
  },
];

export const validationFailedTourSteps: TourStep[] = [
  {
    target: ".val-fb-card",
    title: "What Was Supposed to Publish",
    description: "The exact post that failed to go out automatically — nothing here has changed since the last attempt.",
    icon: "ti ti-brand-facebook",
    placement: "right",
  },
  {
    target: ".val-details-panel",
    title: "Retry History",
    description: "Retry attempts and the last attempt time appear here alongside the usual submission details, so you can see how many times this has already failed.",
    icon: "ti ti-file-text",
    placement: "left",
  },
  {
    target: ".val-action-bar",
    title: "Retry or Publish Manually",
    description: "Retry sends it back through automated publishing (optionally with a new schedule). If it keeps failing, Start Manual Publish walks you through posting it to Facebook yourself and marking it complete.",
    icon: "ti ti-refresh",
    placement: "top",
  },
];

export const validationEditTourSteps: TourStep[] = [
  {
    target: ".val-edit-tabs",
    title: "Moderator Editorial Control",
    description: "As a moderator or administrator, you can refine post content before approving. Switch between Details, Attached Media, and Schedule settings.",
    icon: "ti ti-pencil",
    placement: "bottom",
  },
  {
    target: "#val-edit-caption-group",
    title: "Refine Title & Caption",
    description: "Polish the caption text, format with bold/italic unicode tools, or generate enhanced variations with AI Caption suggestions.",
    icon: "ti ti-sparkles",
    placement: "left",
  },
  {
    target: ".val-edit-tabs",
    title: "Curate Media & Library Assets",
    description: "Inspect uploaded media files, reorder slides, or attach pre-approved photos directly from your institution's Media Library.",
    icon: "ti ti-photo-plus",
    placement: "bottom",
  },
  {
    target: ".val-edit-tabs",
    title: "Schedule Slot & Privileges",
    description: "Adjust the scheduled publication slot. Administrators also have privileges to toggle Fast-Track Live Events and override schedule slot limits.",
    icon: "ti ti-calendar-clock",
    placement: "bottom",
  },
  {
    target: "#val-btn-save-edit",
    title: "Save Moderator Edits",
    description: "Save your changes. The post stays In Review so you can inspect the updated preview before making your final approval decision.",
    icon: "ti ti-device-floppy",
    placement: "top",
  },
];
