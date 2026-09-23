import type { TourStep } from "../types";

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

export const submissionComposerTourSteps: TourStep[] = [
  {
    target: "#composer-step-nav",
    title: "3-Step Composer Workflow",
    description: "Follow the structured stages: Add Media, enter Post Details, and organize schedule slots before review.",
    icon: "ti ti-list-numbers",
    placement: "bottom",
  },
  {
    target: ".mp-panel-wrap",
    title: "Upload & Select Media",
    description: "Attach event photos and videos by dragging files here, selecting from your institution library, or choosing AI suggestions.",
    icon: "ti ti-photo-up",
    placement: "top",
  },
  {
    // The tour starts on Add Media, so point at the Post Details step where the
    // caption tools (AI, Templates, Fancy text) live.
    target: "#composer-step-nav .sub-step:nth-of-type(2)",
    title: "Caption Tools & Templates",
    description: "In Post Details, the caption has Suggest with AI, Post Templates (built-in, your saved ones, or generated from top posts), and Fancy text.",
    icon: "ti ti-template",
    placement: "bottom",
  },
  {
    target: ".sub-form-page-actions",
    title: "Preview & Controls",
    description: "Switch to the live Facebook preview anytime to check your post formatting before submitting.",
    icon: "ti ti-brand-facebook",
    placement: "bottom",
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



