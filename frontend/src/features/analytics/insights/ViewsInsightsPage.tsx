import { useState } from "react";
import type { User } from "../../../types/auth.types";
import type { ContentInsightsDto, ContentInsightDailyPointDto, ContentInsightOverviewDto } from "../../../api/analyticsApi";
import InsightsScaffold from "./components/InsightsScaffold";
import MetricTile from "./components/MetricTile";
import InsightAreaChart from "./components/InsightAreaChart";
import BreakdownBars from "./components/BreakdownBars";
import { formatNumber, formatDateTime } from "../analyticsUtils";

function shortDate(value: string | number) {
  return new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

interface ViewsMetric {
  key: string;
  label: string;
  color: string;
  value: (o: ContentInsightOverviewDto) => string;
  sublabel: (o: ContentInsightOverviewDto) => string;
  daily: (p: ContentInsightDailyPointDto) => number;
}

const VIEWS_METRICS: ViewsMetric[] = [
  { key: "views", label: "Views", color: "#155eef", value: (o) => formatNumber(o.totalViews), sublabel: (o) => `${formatNumber(o.uniqueViews)} unique`, daily: (p) => p.views },
  { key: "threeSec", label: "3-second views", color: "#12B5A6", value: (o) => formatNumber(o.fifteenSecondViews), sublabel: () => "short retention", daily: (p) => p.fifteenSecondViews },
  { key: "oneMin", label: "1-minute views", color: "#7C3AED", value: (o) => formatNumber(o.sixtySecondViews), sublabel: () => "deep retention", daily: (p) => p.sixtySecondViews },
  { key: "watch", label: "Watch time", color: "#F79009", value: (o) => `${o.averageWatchTimeSeconds.toFixed(0)}s`, sublabel: (o) => `${formatNumber(o.totalWatchTimeSeconds)}s total`, daily: (p) => p.watchTimeSeconds },
];

function ViewsContent({ data }: { data: ContentInsightsDto }) {
  const [selectedKey, setSelectedKey] = useState("views");
  const active = VIEWS_METRICS.find((m) => m.key === selectedKey) ?? VIEWS_METRICS[0];
  const o = data.overview;
  const chartData = data.dailyTrend.map((p) => ({ date: p.date, [active.label]: active.daily(p) }));

  return (
    <>
      <section className="ins-tiles" aria-label="Views metrics — select one to chart it">
        {VIEWS_METRICS.map((m) => (
          <MetricTile
            key={m.key}
            label={m.label}
            value={m.value(o)}
            sublabel={m.sublabel(o)}
            series={data.dailyTrend.map(m.daily)}
            active={m.key === selectedKey}
            onClick={() => setSelectedKey(m.key)}
          />
        ))}
      </section>

      <section className="ins-panel ins-panel-wide">
        <div className="ins-panel-head">
          <h2>{active.label} over time</h2>
          <p>Daily {active.label.toLowerCase()} from the latest Facebook snapshots — tap a metric above to switch.</p>
        </div>
        <InsightAreaChart
          data={chartData}
          xKey="date"
          formatX={shortDate}
          series={[{ key: active.label, name: active.label, color: active.color }]}
        />
      </section>

      <div className="ins-grid">
        <section className="ins-panel">
          <div className="ins-panel-head">
            <h2>Seen &amp; read by content type</h2>
            <p>Impressions (times shown) per format — with reads (clicks / "See more" / photo opens). Covers text and photos, not just video.</p>
          </div>
          <BreakdownBars
            rows={data.contentTypes.map((c) => ({
              label: c.contentType,
              value: c.impressions,
              display: `${formatNumber(c.impressions)} seen`,
              caption: `${formatNumber(c.postCount)} posts · ${formatNumber(c.reach)} reach · ${formatNumber(c.postClicks)} reads`,
            }))}
          />
        </section>

        <section className="ins-panel">
          <div className="ins-panel-head">
            <h2>Recent posts</h2>
            <p>Views on your most recently published content.</p>
          </div>
          <ul className="ins-recent">
            {data.recentPosts.length === 0 && <li className="ins-chart-empty">No synced posts in this range.</li>}
            {data.recentPosts.map((post) => (
              <li className="ins-recent-row" key={post.submissionId}>
                <div className="ins-recent-main">
                  <strong>{post.eventTitle || "Untitled post"}</strong>
                  <span>{post.contentType} · {post.publishedAt ? formatDateTime(post.publishedAt) : "No publish time"}</span>
                </div>
                <span className="ins-recent-metric">{formatNumber(post.views)}<small>views</small></span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}

export default function ViewsInsightsPage({ user }: { user: User }) {
  return (
    <InsightsScaffold
      user={user}
      page="Views"
      title="Views"
      subtitle="How often your published content was seen, and which formats earn attention"
    >
      {(data) => <ViewsContent data={data} />}
    </InsightsScaffold>
  );
}
