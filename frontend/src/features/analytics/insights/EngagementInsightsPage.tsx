import type { User } from "../../../types/auth.types";
import InsightsScaffold from "./components/InsightsScaffold";
import MetricTile from "./components/MetricTile";
import InsightAreaChart from "./components/InsightAreaChart";
import InsightDonut from "./components/InsightDonut";
import BreakdownBars from "./components/BreakdownBars";
import { formatNumber, formatPercent } from "../analyticsUtils";

function shortDate(value: string | number) {
  return new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export default function EngagementInsightsPage({ user }: { user: User }) {
  return (
    <InsightsScaffold
      user={user}
      page="Engagement"
      title="Engagement"
      subtitle="Reactions, comments, and shares your published content earned"
    >
      {(data) => {
        const o = data.overview;
        const trend = data.dailyTrend.map((p) => ({ date: p.date, Engagement: p.engagements }));
        const engagementSeries = data.dailyTrend.map((p) => p.engagements);
        return (
          <>
            <section className="ins-tiles">
              <MetricTile label="Engagement" value={formatNumber(o.totalEngagements)} sublabel={`${formatPercent(o.engagementRate)} of views`} series={engagementSeries} active />
              <MetricTile label="Reactions" value={formatNumber(o.reactions)} />
              <MetricTile label="Comments" value={formatNumber(o.comments)} />
              <MetricTile label="Shares" value={formatNumber(o.shares)} />
            </section>

            <section className="ins-panel ins-panel-wide">
              <div className="ins-panel-head">
                <h2>Engagement over time</h2>
                <p>Daily reactions, comments, and shares combined.</p>
              </div>
              <InsightAreaChart
                data={trend}
                xKey="date"
                formatX={shortDate}
                series={[{ key: "Engagement", name: "Engagement", color: "#7C3AED" }]}
              />
            </section>

            <div className="ins-grid">
              <section className="ins-panel">
                <div className="ins-panel-head">
                  <h2>By interaction type</h2>
                  <p>How your audience chose to engage.</p>
                </div>
                <InsightDonut
                  slices={[
                    { name: "Reactions", value: o.reactions, color: "#155eef" },
                    { name: "Comments", value: o.comments, color: "#12B5A6" },
                    { name: "Shares", value: o.shares, color: "#F79009" },
                  ]}
                />
              </section>

              <section className="ins-panel">
                <div className="ins-panel-head">
                  <h2>Engagement by content type</h2>
                  <p>Which formats spark the most interaction.</p>
                </div>
                <BreakdownBars
                  color="#7C3AED"
                  rows={data.contentTypes.map((c) => ({
                    label: c.contentType,
                    value: c.engagements,
                    display: formatNumber(c.engagements),
                    caption: `${formatPercent(c.engagementRate)} engagement rate`,
                  }))}
                />
              </section>
            </div>
          </>
        );
      }}
    </InsightsScaffold>
  );
}
