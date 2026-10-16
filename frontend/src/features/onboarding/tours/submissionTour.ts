import type { TourStep } from "../types";
import type { ProgressStep } from "../../submission/types";

export const submissionListTourSteps: TourStep[] = [
  {
    target: ".sub-list-new",
    title: "Create a New Submission",
    description: "Click here to start drafting a new post. You can attach media, use AI caption assistance, and set event dates.",
    icon: "ti ti-plus",
    placement: "bottom",
  },
  {
    target: ".sub-status-tabs",
    title: "Filter by Workflow Status",
    description: "Track your posts across every stage: Drafts, Action Needed (revisions), Submitted (pending review), and Published.",
    icon: "ti ti-filter",
    placement: "bottom",
  },
  {
    target: ".sub-search-wrap",
    title: "Quick Search",
    description: "Type any keyword or event title to instantly find specific submissions in your history.",
    icon: "ti ti-search",
    placement: "bottom",
  },
  {
    target: ".sub-list-results",
    title: "Submission Cards & Previews",
    description: "Each card shows the Facebook-style preview, publication date, and status badges. Click any item to view or edit.",
    icon: "ti ti-layout-cards",
    placement: "top",
  },
];

/** A panel the composer guide opens so a step can show it. */
export type ComposerTourPanel = "readiness" | "ai" | "templates" | "fancy";

/**
 * What each composer-guide step needs on screen: the wizard step to show
 * (steps 2-3 are shown even before media is added — the guide is view-only)
 * and, optionally, a panel to open. Keyed by TourStep.id; SubmissionScreen
 * applies it via useScreenTour's onStepChange and restores the user's step
 * when the guide ends.
 */
export const COMPOSER_TOUR_VIEWS: Record<string, { step: ProgressStep; panel?: ComposerTourPanel }> = {
  steps: { step: "media" },
  media: { step: "media" },
  readiness: { step: "media", panel: "readiness" },
  details: { step: "details" },
  "caption-tools": { step: "details" },
  ai: { step: "details", panel: "ai" },
  templates: { step: "details", panel: "templates" },
  fancy: { step: "details", panel: "fancy" },
  album: { step: "schedule" },
  schedule: { step: "schedule" },
  live: { step: "schedule" },
  preview: { step: "schedule" },
  submit: { step: "schedule" },
};

export const submissionComposerTourSteps: TourStep[] = [
  {
    id: "steps",
    target: "#composer-step-nav",
    title: "Three steps to a post",
    description: "Every post goes Add Media → Post Details → Organize & Schedule. This guide walks through each one — just look, nothing you see here is changed.",
    icon: "ti ti-list-numbers",
    placement: "bottom",
  },
  {
    id: "media",
    target: ".mp-root",
    title: "Add your media",
    description: "Upload photos or videos, pick from your institution's library, or let AI suggest matching assets. At least one is required, and you can drag to reorder them.",
    icon: "ti ti-photo-up",
    placement: "top",
  },
  {
    id: "readiness",
    target: ".sub-guard-scroll",
    title: "Readiness checklist",
    description: "Your post's score out of 100. Required items block submitting; recommended ones just improve the post. Tap any item to jump straight to it. On phones, open it from the score chip in the top bar.",
    icon: "ti ti-shield-check",
    placement: "left",
  },
  {
    id: "details",
    target: "#composer-event-fields",
    title: "Event details",
    description: "Step 2 starts with the event title and date. Fields marked * are required before you can submit.",
    icon: "ti ti-edit",
    placement: "bottom",
  },
  {
    id: "caption-tools",
    target: ".sub-caption-actions",
    title: "Caption tools",
    description: "Three helpers for the caption: Suggest Caption (AI), Templates, and Fancy text. When the caption is empty you'll also see quick-start buttons inside the box.",
    icon: "ti ti-tools",
    placement: "bottom",
  },
  {
    id: "ai",
    target: ".ai-prompt-dialog",
    title: "AI caption assistant",
    description: "Pick a tone and, optionally, tell the AI what to focus on. It reads your photos and event details and writes one caption you can insert, edit, or regenerate.",
    icon: "ti ti-sparkles",
    placement: "auto",
  },
  {
    id: "templates",
    target: ".sub-tpl-panel",
    title: "Post templates",
    description: "Start from a proven structure. Pick a built-in or saved template to pre-fill the caption and tags, save your own caption as a template, or generate one from the Page's top posts.",
    icon: "ti ti-template",
    placement: "left",
  },
  {
    id: "fancy",
    target: ".fancy-text-panel",
    title: "Fancy text",
    description: "Style selected caption text (or the whole caption) in bold, italic, or script Unicode — it shows up on Facebook. Choose Plain to undo.",
    icon: "ti ti-typography",
    placement: "auto",
  },
  {
    id: "album",
    target: "#composer-album-tags",
    title: "Album & media tags",
    description: "Step 3: file your media in an album (Auto-Match can pick one for you) and add tags so it's easy to find in the library later.",
    icon: "ti ti-folders",
    placement: "bottom",
  },
  {
    id: "schedule",
    target: "#composer-when-to-post",
    title: "Schedule a time",
    description: "Tap a suggested time — picked from when your Page gets the most engagement — or choose your own date and time. Times that clash with other scheduled posts may be flagged or blocked.",
    icon: "ti ti-calendar-time",
    placement: "top",
  },
  {
    id: "live",
    target: ".sub-mode-toggle",
    title: "Live Event",
    description: "Covering something happening right now? Switch to Live Event to skip the schedule: it's sent as urgent, reviewed first, and published as soon as it's approved.",
    icon: "ti ti-bolt",
    placement: "bottom",
  },
  {
    id: "preview",
    target: ".sub-form-page-actions",
    title: "Preview",
    description: "See exactly how the post will look on Facebook at any step before you send it.",
    icon: "ti ti-brand-facebook",
    placement: "bottom",
  },
  {
    id: "submit",
    target: ".sub-guard-actions",
    title: "Send for approval",
    description: "When every required item is done, submit it for a moderator to review. Once saved, your draft autosaves as you edit, and Save Draft is always available.",
    icon: "ti ti-send",
    placement: "top",
  },
];

export const saveDraftTourSteps: TourStep[] = [
  {
    target: "#btn-save-draft",
    title: "Save Your Progress",
    description: "You have unsaved changes! Click 'Save Draft' at any time to preserve your draft so you can safely leave and resume later.",
    icon: "ti ti-device-floppy",
    placement: "bottom",
  },
];



