import { useState } from "react";
import type { User } from "../../../types/auth.types";
import type { ContentInsightsDto, ContentInsightDailyPointDto, ContentInsightOverviewDto } from "../../../api/analyticsApi";
import InsightsScaffold from "./components/InsightsScaffold";
import MetricTile from "./components/MetricTile";
import InsightAreaChart from "./components/InsightAreaChart";
import InsightDonut from "./components/InsightDonut";
import BreakdownBars from "./components/BreakdownBars";
import { formatNumber, formatPercent } from "../analyticsUtils";

function shortDate(value: string | number) {
  return new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

interface EngagementMetric {
  key: string;
  label: string;
  color: string;
  value: (o: ContentInsightOverviewDto) => string;
  sublabel: (o: ContentInsightOverviewDto) => string;
  daily: (p: ContentInsightDailyPointDto) => number;
}

const ENGAGEMENT_METRICS: EngagementMetric[] = [
  { key: "engagement", label: "Engagement", color: "#7C3AED", value: (o) => formatNumber(o.totalEngagements), sublabel: (o) => `${formatPercent(o.engagementRate)} of views`, daily: (p) => p.engagements },
  { key: "reactions", label: "Reactions", color: "#155eef", value: (o) => formatNumber(o.reactions), sublabel: () => "likes & reactions", daily: (p) => p.reactions },
  { key: "comments", label: "Comments", color: "#12B5A6", value: (o) => formatNumber(o.comments), sublabel: () => "replies", daily: (p) => p.comments },
  { key: "shares", label: "Shares", color: "#F79009", value: (o) => formatNumber(o.shares), sublabel: () => "reshares", daily: (p) => p.shares },
];

function EngagementContent({ data }: { data: ContentInsightsDto }) {
  const [selectedKey, setSelectedKey] = useState("engagement");
  const active = ENGAGEMENT_METRICS.find((m) => m.key === selectedKey) ?? ENGAGEMENT_METRICS[0];
  const o = data.overview;
  const chartData = data.dailyTrend.map((p) => ({ date: p.date, [active.label]: active.daily(p) }));

  return (
    <>
      <section className="ins-tiles" aria-label="Engagement metrics — select one to chart it">
        {ENGAGEMENT_METRICS.map((m) => (
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
          <p>Daily {active.label.toLowerCase()} — tap a metric above to switch the chart.</p>
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
}

export default function EngagementInsightsPage({ user }: { user: User }) {
  return (
    <InsightsScaffold
      user={user}
      page="Engagement"
      title="Engagement"
      subtitle="Reactions, comments, and shares your published content earned"
    >
      {(data) => <EngagementContent data={data} />}
    </InsightsScaffold>
  );
}
