import type { User } from "../../../types/auth.types";
import InsightsScaffold from "./components/InsightsScaffold";
import MetricTile from "./components/MetricTile";
import InsightAreaChart from "./components/InsightAreaChart";
import BreakdownBars from "./components/BreakdownBars";
import { formatNumber, formatPercent, formatDateTime } from "../analyticsUtils";

function shortDate(value: string | number) {
  return new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function ViewsInsightsPage({ user }: { user: User }) {
  return (
    <InsightsScaffold
      user={user}
      page="Views"
      title="Views"
      subtitle="How often your published content was seen, and which formats earn attention"
    >
      {(data) => {
        const o = data.overview;
        const trend = data.dailyTrend.map((p) => ({ date: p.date, Views: p.views, Reach: p.reach }));
        const viewsSeries = data.dailyTrend.map((p) => p.views);
        const watchSeries = data.dailyTrend.map((p) => p.watchTimeSeconds);
        return (
          <>
            <section className="ins-tiles">
              <MetricTile label="Views" value={formatNumber(o.totalViews)} sublabel={`${formatNumber(o.uniqueViews)} unique`} series={viewsSeries} active />
              <MetricTile label="3-second views" value={formatNumber(o.fifteenSecondViews)} sublabel="short retention" />
              <MetricTile label="1-minute views" value={formatNumber(o.sixtySecondViews)} sublabel="deep retention" />
              <MetricTile label="Watch time" value={`${o.averageWatchTimeSeconds.toFixed(0)}s`} sublabel={`${formatNumber(o.totalWatchTimeSeconds)}s total`} series={watchSeries} />
            </section>

            <section className="ins-panel ins-panel-wide">
              <div className="ins-panel-head">
                <h2>Views &amp; reach over time</h2>
                <p>Daily views and reach from the latest Facebook snapshots.</p>
              </div>
              <InsightAreaChart
                data={trend}
                xKey="date"
                formatX={shortDate}
                series={[
                  { key: "Views", name: "Views", color: "#155eef" },
                  { key: "Reach", name: "Reach", color: "#12B5A6" },
                ]}
              />
            </section>

            <div className="ins-grid">
              <section className="ins-panel">
                <div className="ins-panel-head">
                  <h2>Views by content type</h2>
                  <p>Which formats currently earn the strongest attention.</p>
                </div>
                <BreakdownBars
                  rows={data.contentTypes.map((c) => ({
                    label: c.contentType,
                    value: c.views,
                    display: formatNumber(c.views),
                    caption: `${formatNumber(c.postCount)} posts · ${formatPercent(c.engagementRate)} engagement`,
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
      }}
    </InsightsScaffold>
  );
}
