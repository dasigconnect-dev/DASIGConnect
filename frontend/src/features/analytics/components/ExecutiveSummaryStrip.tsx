import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport?: (metric: AnalyticsExportMetric) => void;
}

type Pill = { tag: string; tagClass: string };

// "No data" until there is at least one record behind the number, so a pill
// never claims "Healthy" / "Live Sync" on an all-zero dataset.
function ratePill(
  value: number,
  sampleSize: number,
  target: number,
  labels: { good: string; bad: string },
): Pill {
  if (sampleSize <= 0) return { tag: "No data", tagClass: "pill-draft" };
  return value >= target
    ? { tag: labels.good, tagClass: "sp-approved" }
    : { tag: labels.bad, tagClass: "pill-revision" };
}

function volumePill(sampleSize: number, activeLabel: string): Pill {
  return sampleSize > 0
    ? { tag: activeLabel, tagClass: "sp-scheduled" }
    : { tag: "No data", tagClass: "pill-draft" };
}

export default function ExecutiveSummaryStrip({ summary, onOpenReport }: Props) {
  const isContributor = summary.scopeRole === "contributor" || Boolean(summary.contributorAnalytics);
  const fb = summary.facebookEngagement;
  const op = summary.operationalHealth;

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

  const publishSuccessPill: Pill = op
    ? ratePill(op.publishingSuccessRate, op.publicationAttempts, 95, { good: "Healthy", bad: "Below" })
    : { tag: "No data", tagClass: "pill-draft" };
  const firstPassPill = ratePill(firstPassRate, summary.contributorAnalytics?.submittedPosts ?? 0, 85, {
    good: "Healthy",
    bad: "Watch",
  });
  const engagementPill = volumePill(fb.sampleSize, "Live Sync");
  const postingDelayPill = volumePill(summary.averagePostingDelay.sampleSize, "Tracking");

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
      tag: postingDelayPill.tag,
      tagClass: postingDelayPill.tagClass,
      reportMetric: "posting-delay",
    },
    {
      id: "success",
      icon: "ti ti-circle-check",
      label: "PUBLISH SUCCESS",
      value: op ? formatPercent(op.publishingSuccessRate) : "—",
      sub: op
        ? `${op.successfulPublicationAttempts}/${op.publicationAttempts} attempts`
        : "Admin scope only",
      tag: publishSuccessPill.tag,
      tagClass: publishSuccessPill.tagClass,
      reportMetric: "operational-health",
    },
    {
      id: "reactions",
      icon: "ti ti-thumb-up",
      label: "FB REACTIONS",
      value: formatNumber(fb.totalReactions),
      sub: `${formatNumber(fb.totalComments)} comments · ${formatNumber(fb.totalShares)} shares`,
      tag: engagementPill.tag,
      tagClass: engagementPill.tagClass,
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
      tag: postingDelayPill.tag,
      tagClass: postingDelayPill.tagClass,
      reportMetric: "posting-delay",
    },
    {
      // No dedicated "first-pass" report exists; this is derived from the
      // revision rate, so the card is a read-only KPI (no drill-down).
      id: "first-pass-health",
      icon: "ti ti-circle-check",
      label: "FIRST-PASS HEALTH",
      value: `${firstPassRate.toFixed(1)}%`,
      sub: "approved first try",
      tag: firstPassPill.tag,
      tagClass: firstPassPill.tagClass,
    },
    {
      id: "social-reach",
      icon: "ti ti-thumb-up",
      label: "FB REACTIONS",
      value: formatNumber(fb.totalReactions),
      sub: `${formatNumber(fb.totalComments)} comments · ${formatNumber(fb.totalShares)} shares`,
      tag: engagementPill.tag,
      tagClass: engagementPill.tagClass,
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
