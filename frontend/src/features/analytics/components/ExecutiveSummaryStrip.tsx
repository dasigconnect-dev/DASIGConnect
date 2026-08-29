import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
}

export default function ExecutiveSummaryStrip({ summary }: Props) {
  const isContributor = summary.scopeRole === "contributor" || Boolean(summary.contributorAnalytics);

  const topInstitution =
    summary.postsByInstitution.length > 0
      ? [...summary.postsByInstitution].sort((a, b) => b.totalPublished - a.totalPublished)[0]
      : null;

  const topInstName = topInstitution ? topInstitution.institutionName : "All Network";
  const topInstVolume = topInstitution ? `${topInstitution.totalPublished} posts` : "0 posts";

  const firstPassRate = Math.max(
    0,
    100 - (summary.contributorAnalytics?.rejectedOrNeedsRevisionRate ? summary.contributorAnalytics.rejectedOrNeedsRevisionRate * 100 : 0)
  );

  const adminCards = [
    {
      id: "top-inst",
      icon: "ti ti-building",
      label: "TOP INSTITUTION",
      value: topInstName,
      sub: topInstVolume,
      tag: "Rank #1",
      tagClass: "sp-scheduled",
    },
    {
      id: "published",
      icon: "ti ti-speakerphone",
      label: "TOTAL PUBLISHED",
      value: formatNumber(summary.totalPostsPublished.value),
      sub: `${summary.totalPostsPublished.sampleSize} posts recorded`,
      tag: summary.totalPostsPublished.targetMet ? "On Target" : "Tracking",
      tagClass: summary.totalPostsPublished.targetMet ? "sp-approved" : "sp-pending",
    },
    {
      id: "completeness",
      icon: "ti ti-checklist",
      label: "COMPLETENESS",
      value: formatPercent(summary.contentCompleteness.value),
      sub: "Target: 95.0%",
      tag: summary.contentCompleteness.targetMet ? "On Target" : "Below",
      tagClass: summary.contentCompleteness.targetMet ? "sp-approved" : "pill-revision",
    },
    {
      id: "delay",
      icon: "ti ti-clock-hour-4",
      label: "AVG POSTING DELAY",
      value: `${summary.averagePostingDelay.value.toFixed(1)}d`,
      sub: `${summary.averagePostingDelay.sampleSize} posts measured`,
      tag: "Tracking",
      tagClass: "sp-pending",
    },
    {
      id: "success",
      icon: "ti ti-circle-check",
      label: "PUBLISH SUCCESS",
      value: summary.operationalHealth
        ? formatPercent(summary.operationalHealth.publishingSuccessRate)
        : "100%",
      sub: summary.operationalHealth
        ? `${summary.operationalHealth.successfulPublicationAttempts}/${summary.operationalHealth.publicationAttempts} attempts`
        : "Direct & Auto",
      tag: "Healthy",
      tagClass: "sp-approved",
    },
    {
      id: "reactions",
      icon: "ti ti-thumb-up",
      label: "FB REACTIONS",
      value: formatNumber(summary.facebookEngagement.totalReactions),
      sub: `${formatNumber(Math.round(summary.facebookEngagement.averageReach))} avg reach`,
      tag: "Live Sync",
      tagClass: "sp-scheduled",
    },
  ];

  const contributorCards = [
    {
      id: "total-submitted",
      icon: "ti ti-file-upload",
      label: "TOTAL SUBMISSIONS",
      value: formatNumber(summary.contributorAnalytics?.submittedPosts ?? summary.totalPostsPublished.sampleSize),
      sub: "posts drafted & sent",
      tag: "Active",
      tagClass: "sp-scheduled",
    },
    {
      id: "total-published",
      icon: "ti ti-speakerphone",
      label: "TOTAL PUBLISHED",
      value: formatNumber(summary.totalPostsPublished.value),
      sub: "live on Facebook",
      tag: summary.totalPostsPublished.targetMet ? "On Target" : "Tracking",
      tagClass: summary.totalPostsPublished.targetMet ? "sp-approved" : "sp-pending",
    },
    {
      id: "completeness",
      icon: "ti ti-checklist",
      label: "COMPLETENESS",
      value: formatPercent(summary.contentCompleteness.value),
      sub: "Target: 95.0%",
      tag: summary.contentCompleteness.targetMet ? "On Target" : "Below",
      tagClass: summary.contentCompleteness.targetMet ? "sp-approved" : "pill-revision",
    },
    {
      id: "delay",
      icon: "ti ti-clock-hour-4",
      label: "AVG POSTING DELAY",
      value: `${summary.averagePostingDelay.value.toFixed(1)}d`,
      sub: `${summary.averagePostingDelay.sampleSize} posts measured`,
      tag: "Tracking",
      tagClass: "sp-pending",
    },
    {
      id: "first-pass-health",
      icon: "ti ti-circle-check",
      label: "FIRST-PASS HEALTH",
      value: `${firstPassRate.toFixed(1)}%`,
      sub: "approved first try",
      tag: "Healthy",
      tagClass: "sp-approved",
    },
    {
      id: "social-reach",
      icon: "ti ti-thumb-up",
      label: "SOCIAL REACH",
      value: formatNumber(Math.round(summary.facebookEngagement.averageReach)),
      sub: `${formatNumber(summary.facebookEngagement.totalReactions)} total reactions`,
      tag: "Live Sync",
      tagClass: "sp-scheduled",
    },
  ];

  const cards = isContributor ? contributorCards : adminCards;

  return (
    <div className="analytics-kpi-strip-wrap">
      {cards.map((c) => (
        <div className="analytics-kpi-strip-card" key={c.id}>
          <div className="analytics-kpi-strip-top">
            <div className="analytics-kpi-strip-icon">
              <i className={c.icon} />
            </div>
            <span className={`status-pill ${c.tagClass}`} style={{ fontSize: "10px", padding: "2px 7px" }}>
              {c.tag}
            </span>
          </div>

          <div className="analytics-kpi-strip-body">
            <span className="analytics-kpi-strip-label">{c.label}</span>
            <div className="analytics-kpi-strip-value" title={c.value}>
              {c.value}
            </div>
            <span className="analytics-kpi-strip-sub" title={c.sub}>
              {c.sub}
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}
