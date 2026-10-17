import type { SubmissionStatus } from "../../api/submissionApi";
import type { FormState } from "./types";

export const initialForm: FormState = {
  id: null,
  status: "draft",
  institutionId: "",
  selectedTemplateId: null,
  fastTrack: false,
  liveEventName: "",
  eventTitle: "",
  eventDate: "",
  caption: "",
  description: "",

  scheduledDate: "",
  scheduledTime: "",
  tags: [],
  albumName: "",
  mediaTags: [],
  files: [],
  savedAssets: [],
  mediaOrder: [],
  mediaCaptions: {},
  mediaSkipWatermark: {},
  pendingAssetIds: [],
  removedAssetIds: [],
};

export const statusCopy: Record<
  SubmissionStatus,
  { label: string; meaning: string; whoActsNext: string }
> = {
  draft: {
    label: "Draft — Finish & Submit",
    meaning: "Draft — not yet submitted.",
    whoActsNext: "Complete all required items and submit for Moderator review.",
  },
  pending: {
    label: "Awaiting Moderator Review",
    meaning: "Awaiting review — submitted for approval.",
    whoActsNext: "A Moderator will review this post next.",
  },
  in_review: {
    label: "Being Reviewed by Moderator",
    meaning: "Being reviewed now.",
    whoActsNext: "A Moderator is currently reviewing this submission.",
  },
  needs_revision: {
    label: "Needs Revision — Edit & Resubmit",
    meaning: "Needs revision — the Moderator asked for changes.",
    whoActsNext: "Edit and resubmit.",
  },
  missed_review: {
    label: "Missed Schedule — Moderator Reschedules",
    meaning: "Missed review — the scheduled time passed before review.",
    whoActsNext: "A Moderator or Administrator will assign a new schedule.",
  },
  scheduled: {
    label: "Approved & Scheduled",
    meaning: "Approved and scheduled.",
    whoActsNext: "The system will publish this automatically at the scheduled time.",
  },
  publishing: {
    label: "Publishing to Facebook",
    meaning: "Publishing now.",
    whoActsNext: "The system is posting this to the DASIG Facebook Page.",
  },
  publish_failed: {
    label: "Publish Failed — Admin Action Needed",
    meaning: "Publish failed.",
    whoActsNext: "An Administrator or Moderator needs to retry or publish manually.",
  },
  published: {
    label: "Published on Facebook",
    meaning: "Published — this post is live on the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
  },
  published_manual: {
    label: "Published on Facebook",
    meaning: "Published manually — this post is live on the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
  },
  admin_direct_post: {
    label: "Published (Direct Post)",
    meaning: "Published — posted directly by an Administrator to the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
  },
  direct_post_scheduled: {
    label: "Scheduled (Direct Post)",
    meaning: "Scheduled — will publish automatically to the DASIG Facebook Page.",
    whoActsNext: "The system will publish this automatically at the scheduled time.",
  },
  direct_post_publishing: {
    label: "Publishing (Direct Post)",
    meaning: "Publishing now — posting to the DASIG Facebook Page.",
    whoActsNext: "The system is posting this to the DASIG Facebook Page.",
  },
  direct_post_failed: {
    label: "Publish Failed — Admin Action Needed",
    meaning: "Publish failed.",
    whoActsNext: "An Administrator needs to retry or publish manually.",
  },
  rejected: {
    label: "Declined — Edit & Resubmit",
    meaning: "Declined — the Moderator declined this post.",
    whoActsNext: "Review the reason to edit and resubmit.",
  },
};

export const statusLabels: Record<SubmissionStatus, string> = {
  draft: statusCopy.draft.label,
  pending: statusCopy.pending.label,
  in_review: statusCopy.in_review.label,
  needs_revision: statusCopy.needs_revision.label,
  missed_review: statusCopy.missed_review.label,
  scheduled: statusCopy.scheduled.label,
  publishing: statusCopy.publishing.label,
  publish_failed: statusCopy.publish_failed.label,
  published: statusCopy.published.label,
  published_manual: statusCopy.published_manual.label,
  admin_direct_post: statusCopy.admin_direct_post.label,
  direct_post_scheduled: statusCopy.direct_post_scheduled.label,
  direct_post_publishing: statusCopy.direct_post_publishing.label,
  direct_post_failed: statusCopy.direct_post_failed.label,
  rejected: statusCopy.rejected.label,
};

export const statusGuidance: Record<SubmissionStatus, string> = {
  draft: "Draft — not yet submitted. Complete all required items and submit for Moderator review.",
  pending: "Awaiting review — a Moderator will review this post next.",
  in_review: "Being reviewed now — a Moderator is currently reviewing this submission.",
  needs_revision: "Needs revision — the Moderator asked for changes. Edit and resubmit.",
  missed_review: "Missed review — the scheduled time passed before review. A Moderator or Administrator will assign a new schedule.",
  scheduled: "Approved and scheduled — the system will publish this automatically at the scheduled time.",
  publishing: "Publishing now — the system is posting this to the DASIG Facebook Page.",
  publish_failed: "Publish failed — an Administrator or Moderator needs to retry or publish manually.",
  published: "Published — this post is live on the DASIG Facebook Page.",
  published_manual: "Published manually — this post is live on the DASIG Facebook Page.",
  admin_direct_post: "Published — posted directly by an Administrator to the DASIG Facebook Page.",
  direct_post_scheduled: "Scheduled — will publish automatically to the DASIG Facebook Page.",
  direct_post_publishing: "Publishing now — posting to the DASIG Facebook Page.",
  direct_post_failed: "Publish failed — an Administrator needs to retry or publish manually.",
  rejected: "Declined — the Moderator declined this post. Review the reason to edit and resubmit.",
};

export const postTemplates = [
  {
    id: "event-announcement",
    name: "Event Announcement",
    target: "Upcoming seminars, workshops, summits",
    tags: ["DASIG", "DOST", "Innovation"],
    caption: [
      "[EVENT TITLE]",
      "",
      "Date:",
      "Venue or Platform:",
      "Registration Link:",
      "",
      "[Brief description / Call to action]",
      "",
      "#DASIGCentralVisayas #DOST7 #InnovationEvent",
    ].join("\n"),
  },
  {
    id: "event-recap",
    name: "Event Recap / Milestone",
    target: "Post-activity highlights, achievements",
    tags: ["DASIG", "DOST"],
    caption: [
      "HISTORY HAS BEEN MADE",
      "EVENT RECAP",
      "",
      "[Summary of accomplishments / key takeaways]",
      "",
      "[Acknowledged partners and attendees]",
      "",
      "#DASIGCentralVisayas #HistoryMadeHere #DOST7",
    ].join("\n"),
  },
  {
    id: "competition-call",
    name: "Competition / Pitching Call",
    target: "Hackathons, reverse pitching challenges",
    tags: ["DASIG", "Innovation"],
    caption: [
      "CALL FOR INNOVATORS / PARTICIPANTS",
      "",
      "[Challenge Theme / Problem Statement]",
      "Prizes or Opportunities:",
      "Deadline for Submission:",
      "Apply here:",
      "",
      "#FlipTheScript #ReversePitching #DASIG",
    ].join("\n"),
  },
  {
    id: "partner-spotlight",
    name: "Partner Feature / Spotlight",
    target: "Member university/HEI spotlights",
    tags: ["DASIG", "Innovation", "Partnership"],
    caption: [
      "INSTITUTIONAL SPOTLIGHT: [University Name]",
      "",
      "[Feature on student research, lab innovation, or award]",
      "",
      "#ConnectedInnovation #CentralVisayas #[UniversityTag]",
    ].join("\n"),
  },
];

export const DEFAULT_INSTITUTION_NAME = "dasig central visayas";
export const DEFAULT_INSTITUTION_CODE = "dasig-cv";
