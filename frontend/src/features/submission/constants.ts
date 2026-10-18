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
  { label: string; meaning: string; whoActsNext: string; actionLabel: string }
> = {
  draft: {
    label: "Draft",
    meaning: "Draft — not yet submitted.",
    whoActsNext: "Complete all required items and submit for Moderator review.",
    actionLabel: "Continue Draft",
  },
  pending: {
    label: "Pending Review",
    meaning: "Awaiting review — submitted for approval.",
    whoActsNext: "A Moderator will review this post next.",
    actionLabel: "View Status",
  },
  in_review: {
    label: "In Review",
    meaning: "Being reviewed now.",
    whoActsNext: "A Moderator is currently reviewing this submission.",
    actionLabel: "View Details",
  },
  needs_revision: {
    label: "Needs Revision",
    meaning: "Needs revision — the Moderator asked for changes.",
    whoActsNext: "Edit and resubmit.",
    actionLabel: "Edit & Resubmit",
  },
  missed_review: {
    label: "Missed Schedule",
    meaning: "Missed review — the scheduled time passed before review.",
    whoActsNext: "A Moderator or Administrator will assign a new schedule.",
    actionLabel: "View Details",
  },
  scheduled: {
    label: "Scheduled",
    meaning: "Approved and scheduled.",
    whoActsNext: "The system will publish this automatically at the scheduled time.",
    actionLabel: "View Schedule",
  },
  publishing: {
    label: "Publishing",
    meaning: "Publishing now.",
    whoActsNext: "The system is posting this to the DASIG Facebook Page.",
    actionLabel: "View Progress",
  },
  publish_failed: {
    label: "Publish Failed",
    meaning: "Publish failed.",
    whoActsNext: "An Administrator or Moderator needs to retry or publish manually.",
    actionLabel: "Resolve Issue",
  },
  published: {
    label: "Published",
    meaning: "Published — this post is live on the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
    actionLabel: "View Post",
  },
  published_manual: {
    label: "Published",
    meaning: "Published manually — this post is live on the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
    actionLabel: "View Post",
  },
  admin_direct_post: {
    label: "Direct Post",
    meaning: "Published — posted directly by an Administrator to the DASIG Facebook Page.",
    whoActsNext: "No action needed.",
    actionLabel: "View Post",
  },
  direct_post_scheduled: {
    label: "Scheduled",
    meaning: "Scheduled — will publish automatically to the DASIG Facebook Page.",
    whoActsNext: "The system will publish this automatically at the scheduled time.",
    actionLabel: "View Schedule",
  },
  direct_post_publishing: {
    label: "Publishing",
    meaning: "Publishing now — posting to the DASIG Facebook Page.",
    whoActsNext: "The system is posting this to the DASIG Facebook Page.",
    actionLabel: "View Progress",
  },
  direct_post_failed: {
    label: "Publish Failed",
    meaning: "Publish failed.",
    whoActsNext: "An Administrator needs to retry or publish manually.",
    actionLabel: "Resolve Issue",
  },
  rejected: {
    label: "Declined",
    meaning: "Declined — the Moderator declined this post.",
    whoActsNext: "Review the reason to edit and resubmit.",
    actionLabel: "View Reason",
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

export const statusActionLabels: Record<SubmissionStatus, string> = {
  draft: statusCopy.draft.actionLabel,
  pending: statusCopy.pending.actionLabel,
  in_review: statusCopy.in_review.actionLabel,
  needs_revision: statusCopy.needs_revision.actionLabel,
  missed_review: statusCopy.missed_review.actionLabel,
  scheduled: statusCopy.scheduled.actionLabel,
  publishing: statusCopy.publishing.actionLabel,
  publish_failed: statusCopy.publish_failed.actionLabel,
  published: statusCopy.published.actionLabel,
  published_manual: statusCopy.published_manual.actionLabel,
  admin_direct_post: statusCopy.admin_direct_post.actionLabel,
  direct_post_scheduled: statusCopy.direct_post_scheduled.actionLabel,
  direct_post_publishing: statusCopy.direct_post_publishing.actionLabel,
  direct_post_failed: statusCopy.direct_post_failed.actionLabel,
  rejected: statusCopy.rejected.actionLabel,
};

export const statusGuidance: Record<SubmissionStatus, string> = {
  draft: "Draft not yet submitted. Complete required items and submit for review.",
  pending: "Awaiting review — a Moderator will review this submission next.",
  in_review: "Under review — a Moderator is currently evaluating this submission.",
  needs_revision: "Needs revision — the Moderator asked for changes. Edit and resubmit.",
  missed_review: "Missed schedule — scheduled time passed. A Moderator will reschedule.",
  scheduled: "Approved — will publish automatically at the scheduled time.",
  publishing: "Publishing now to the DASIG Facebook Page.",
  publish_failed: "Publish failed — an Administrator or Moderator must retry or publish manually.",
  published: "Published — live on the DASIG Facebook Page.",
  published_manual: "Published manually — live on the DASIG Facebook Page.",
  admin_direct_post: "Published directly to the DASIG Facebook Page.",
  direct_post_scheduled: "Direct post scheduled to publish automatically.",
  direct_post_publishing: "Direct post is publishing now to Facebook.",
  direct_post_failed: "Direct publish failed — an Administrator must retry.",
  rejected: "Declined by Moderator. Review the reason to edit and resubmit.",
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
