import type { TourStep } from "../types";

export const validationQueueTourSteps: TourStep[] = [
  {
    target: ".val-tabs",
    title: "Queue stages",
    description: "Filter by stage — Pending (waiting for a moderator), In Review (locked by someone), Needs Revision, Scheduled, and more. The number on each tab is how many posts are in it.",
    icon: "ti ti-filter",
    placement: "bottom",
  },
  {
    target: ".val-toolbar-controls",
    title: "Search & sort",
    description: "Search by title, contributor, or tags. Sort by Publish Slot to see the most urgent posts first, or by Submitted to review in arrival order.",
    icon: "ti ti-search",
    placement: "bottom",
  },
  {
    target: ".val-queue-item",
    title: "Reading a queue card",
    description: "Each card shows the title, institution, and status. A red Live Event chip means it publishes the moment it's approved; otherwise you'll see its scheduled slot. Live Events are sorted to the top so they're reviewed first.",
    icon: "ti ti-id-badge-2",
    placement: "bottom",
  },
  {
    target: ".val-queue-list",
    title: "Open a submission",
    description: "Tap any card to open its Facebook preview and details. Opening a post doesn't lock it — you choose when to start reviewing.",
    icon: "ti ti-list-check",
    placement: "right",
  },
];

/** A view a Review Queue guide step needs on screen. */
export interface ValidationTourView {
  /** Show the Submission Details panel (desktop can hide it). */
  details?: boolean;
  /** Open the Review History dialog. */
  history?: boolean;
  /**
   * Show the decision buttons (Approve, Request Revision, Reject, Edit,
   * Unlock) as a preview. They only exist once a review lock is held, and
   * the guide never takes one.
   */
  decisions?: boolean;
  /** Switch the moderator edit form to this tab. */
  editTab?: "details" | "media" | "schedule";
}

/**
 * What each Review Queue guide step needs on screen, keyed by TourStep.id.
 * ValidationQueueScreen applies it via useScreenTour's onStepChange and puts
 * the user's own view back when the guide ends. The guides are view-only, so
 * opening panels here never changes the submission.
 */
export const VALIDATION_TOUR_VIEWS: Record<string, ValidationTourView> = {
  preview: {},
  details: { details: true },
  "history-entry": { details: true },
  history: { details: true, history: true },
  "start-review": {},
  decisions: { decisions: true },
  "failed-preview": {},
  "failed-details": { details: true },
  "failed-history": { details: true, history: true },
  "failed-actions": {},
  "edit-tabs": { editTab: "details" },
  "edit-details": { editTab: "details" },
  "edit-media": { editTab: "media" },
  "edit-schedule": { editTab: "schedule" },
  "edit-save": {},
};

export const validationReviewTourSteps: TourStep[] = [
  {
    id: "preview",
    target: ".val-fb-card",
    title: "Facebook preview",
    description: "This is the post exactly as followers will see it — caption, hashtags, and media in order. Swipe through the media, and toggle the watermark overlay to check how it will look once published.",
    icon: "ti ti-brand-facebook",
    placement: "right",
  },
  {
    id: "details",
    target: ".val-details-panel",
    title: "Submission details",
    description: "Everything about the post in one place: who submitted it, its status, institution, event date, and when it publishes. A Live Event publishes immediately on approval; a Scheduled post goes out at its slot. Anyone who edits it during review is listed here too.",
    icon: "ti ti-file-text",
    placement: "left",
  },
  {
    id: "history-entry",
    target: ".val-details-history-btn",
    title: "Review history",
    description: "Every decision on this post — approvals, revision requests, rejections, edits — is kept in its review history. The number shows how many actions have been recorded.",
    icon: "ti ti-history",
    placement: "top",
  },
  {
    id: "history",
    target: ".val-history-modal",
    title: "The audit trail",
    description: "Each entry shows who acted, when, and why — including reviewer remarks and a before/after view of any edits.",
    icon: "ti ti-timeline",
    placement: "left",
  },
  {
    id: "start-review",
    target: ".val-action-bar",
    title: "Start Review",
    description: "Start Review locks the post to you for 15 minutes so two moderators can't decide on it at once. You can't review your own submission — another moderator has to.",
    icon: "ti ti-lock",
    placement: "top",
  },
  {
    id: "decisions",
    target: ".val-action-bar",
    title: "Make a decision",
    description: "After Start Review, these replace it: Approve, Request Revision (sends it back with your comments), Reject (with a reason), or Edit to fix it yourself first. Unlock hands it back to the queue without deciding.",
    icon: "ti ti-gavel",
    placement: "top",
  },
];

export const validationFailedTourSteps: TourStep[] = [
  {
    id: "failed-preview",
    target: ".val-fb-card",
    title: "What was supposed to publish",
    description: "The exact post that failed to go out automatically — nothing here has changed since the last attempt.",
    icon: "ti ti-brand-facebook",
    placement: "right",
  },
  {
    id: "failed-details",
    target: ".val-details-panel",
    title: "Why it failed",
    description: "Alongside the usual details you'll see how many times publishing was retried, when the last attempt ran, and the error Facebook returned.",
    icon: "ti ti-file-text",
    placement: "left",
  },
  {
    id: "failed-history",
    target: ".val-history-modal",
    title: "Review history",
    description: "The full audit trail of how this post was reviewed before it failed — opened from Review history at the bottom of the details panel.",
    icon: "ti ti-history",
    placement: "left",
  },
  {
    id: "failed-actions",
    target: ".val-action-bar",
    title: "Retry or publish manually",
    description: "Retry sends it back through automated publishing, optionally with a new schedule. If it keeps failing, Start Manual Publish walks you through posting it to Facebook yourself and marking it complete.",
    icon: "ti ti-refresh",
    placement: "top",
  },
];

export const validationEditTourSteps: TourStep[] = [
  {
    id: "edit-tabs",
    target: ".val-edit-tabs",
    title: "Editing a submission",
    description: "Fix the post yourself instead of sending it back. Edits are split into Details, Media, and Schedule — this guide shows each one, and nothing is changed while you look.",
    icon: "ti ti-pencil",
    placement: "bottom",
  },
  {
    id: "edit-details",
    target: "#val-edit-caption-group",
    title: "Title & caption",
    description: "Correct the event title, date, and caption. Fancy text and AI caption suggestions work here just like in the composer.",
    icon: "ti ti-sparkles",
    placement: "left",
  },
  {
    id: "edit-media",
    target: ".val-edit-grid-panel",
    title: "Media",
    description: "Reorder, remove, or add media from the Media Library. Uploading new files from your device isn't available during review.",
    icon: "ti ti-photo-plus",
    placement: "left",
  },
  {
    id: "edit-schedule",
    target: ".val-edit-grid-panel",
    title: "Schedule",
    description: "Move a Scheduled post to a different slot. Only an Administrator can switch a post between Scheduled and Live Event, or override a blocked slot.",
    icon: "ti ti-calendar-clock",
    placement: "left",
  },
  {
    id: "edit-save",
    target: "#val-edit-actions",
    title: "Review & save",
    description: "Before anything is saved you see every change side by side, can undo any of them, and get an AI check of your caption edit for new typos or changed names and dates. Saving keeps the post In Review; the contributor is told what you changed. \"Restore original\" beside a field puts back what the contributor submitted.",
    icon: "ti ti-device-floppy",
    placement: "top",
  },
];
