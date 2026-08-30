import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport?: (metric: AnalyticsExportMetric) => void;
}

export default function ExecutiveSummaryStrip({ summary, onOpenReport }: Props) {
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

  const adminCards: Array<{
    id: string;
    icon: string;
    label: string;
    value: string;
    sub: string;
    tag: string;
    tagClass: string;
    reportMetric?: AnalyticsExportMetric;
  }> = [
    {
      id: "top-inst",
      icon: "ti ti-building",
      label: "TOP INSTITUTION",
      value: topInstName,
      sub: topInstVolume,
      tag: "Rank #1",
      tagClass: "sp-scheduled",
      reportMetric: "posts-by-institution",
    },
    {
      id: "published",
      icon: "ti ti-speakerphone",
      label: "TOTAL PUBLISHED",
      value: formatNumber(summary.totalPostsPublished.value),
      sub: `${summary.totalPostsPublished.sampleSize} posts recorded`,
      tag: summary.totalPostsPublished.targetMet ? "On Target" : "Tracking",
      tagClass: summary.totalPostsPublished.targetMet ? "sp-approved" : "sp-pending",
      reportMetric: "posts-by-institution",
    },
    {
      id: "completeness",
      icon: "ti ti-checklist",
      label: "COMPLETENESS",
      value: formatPercent(summary.contentCompleteness.value),
      sub: "Target: 95.0%",
      tag: summary.contentCompleteness.targetMet ? "On Target" : "Below",
      tagClass: summary.contentCompleteness.targetMet ? "sp-approved" : "pill-revision",
      reportMetric: "content-completeness",
    },
    {
      id: "delay",
      icon: "ti ti-clock-hour-4",
      label: "AVG POSTING DELAY",
      value: `${summary.averagePostingDelay.value.toFixed(1)}d`,
      sub: `${summary.averagePostingDelay.sampleSize} posts measured`,
      tag: "Tracking",
      tagClass: "sp-pending",
      reportMetric: "posting-delay",
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
      reportMetric: "operational-health",
    },
    {
      id: "reactions",
      icon: "ti ti-thumb-up",
      label: "FB REACTIONS",
      value: formatNumber(summary.facebookEngagement.totalReactions),
      sub: `${formatNumber(Math.round(summary.facebookEngagement.averageReach))} avg reach`,
      tag: "Live Sync",
      tagClass: "sp-scheduled",
      reportMetric: "facebook-engagement",
    },
  ];

  const contributorCards: Array<{
    id: string;
    icon: string;
    label: string;
    value: string;
    sub: string;
    tag: string;
    tagClass: string;
    reportMetric?: AnalyticsExportMetric;
  }> = [
    {
      id: "total-submitted",
      icon: "ti ti-file-upload",
      label: "TOTAL SUBMISSIONS",
      value: formatNumber(summary.contributorAnalytics?.submittedPosts ?? summary.totalPostsPublished.sampleSize),
      sub: "posts drafted & sent",
      tag: "Active",
      tagClass: "sp-scheduled",
      reportMetric: "posts-by-institution",
    },
    {
      id: "total-published",
      icon: "ti ti-speakerphone",
      label: "TOTAL PUBLISHED",
      value: formatNumber(summary.totalPostsPublished.value),
      sub: "live on Facebook",
      tag: summary.totalPostsPublished.targetMet ? "On Target" : "Tracking",
      tagClass: summary.totalPostsPublished.targetMet ? "sp-approved" : "sp-pending",
      reportMetric: "posts-by-institution",
    },
    {
      id: "completeness",
      icon: "ti ti-checklist",
      label: "COMPLETENESS",
      value: formatPercent(summary.contentCompleteness.value),
      sub: "Target: 95.0%",
      tag: summary.contentCompleteness.targetMet ? "On Target" : "Below",
      tagClass: summary.contentCompleteness.targetMet ? "sp-approved" : "pill-revision",
      reportMetric: "content-completeness",
    },
    {
      id: "delay",
      icon: "ti ti-clock-hour-4",
      label: "AVG POSTING DELAY",
      value: `${summary.averagePostingDelay.value.toFixed(1)}d`,
      sub: `${summary.averagePostingDelay.sampleSize} posts measured`,
      tag: "Tracking",
      tagClass: "sp-pending",
      reportMetric: "posting-delay",
    },
    {
      id: "first-pass-health",
      icon: "ti ti-circle-check",
      label: "FIRST-PASS HEALTH",
      value: `${firstPassRate.toFixed(1)}%`,
      sub: "approved first try",
      tag: "Healthy",
      tagClass: "sp-approved",
      reportMetric: "content-completeness",
    },
    {
      id: "social-reach",
      icon: "ti ti-thumb-up",
      label: "SOCIAL REACH",
      value: formatNumber(Math.round(summary.facebookEngagement.averageReach)),
      sub: `${formatNumber(summary.facebookEngagement.totalReactions)} total reactions`,
      tag: "Live Sync",
      tagClass: "sp-scheduled",
      reportMetric: "facebook-engagement",
    },
  ];

  const cards = isContributor ? contributorCards : adminCards;

  return (
    <div className="analytics-kpi-strip-wrap">
      {cards.map((c) => (
        <div
          className={`analytics-kpi-strip-card${c.reportMetric && onOpenReport ? " is-interactive" : ""}`}
          key={c.id}
          onClick={() => {
            if (c.reportMetric && onOpenReport) {
              onOpenReport(c.reportMetric);
            }
          }}
          role={c.reportMetric && onOpenReport ? "button" : undefined}
          tabIndex={c.reportMetric && onOpenReport ? 0 : undefined}
          onKeyDown={(e) => {
            if (c.reportMetric && onOpenReport && (e.key === "Enter" || e.key === " ")) {
              e.preventDefault();
              onOpenReport(c.reportMetric);
            }
          }}
        >
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

          {c.reportMetric && onOpenReport && (
            <div className="analytics-kpi-strip-footer">
              <span className="analytics-kpi-strip-action">
                View Report <i className="ti ti-arrow-right" />
              </span>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
