import { useEffect, useState } from "react";
import type { User } from "../../../types/auth.types";
import {
  getPageAudience,
  getContentInsights,
  type AnalyticsRange,
  type PageAudienceInsightsDto,
  type ContentInsightsDto,
} from "../../../api/analyticsApi";
import AnalyticsLayout from "../components/AnalyticsLayout";
import MetricTile from "./components/MetricTile";
import InsightAreaChart from "./components/InsightAreaChart";
import BreakdownBars from "./components/BreakdownBars";
import { INSIGHT_RANGES } from "./useContentInsights";
import { formatNumber, formatDateTime } from "../analyticsUtils";
import "../../../styles/analytics.css";
import "../../../styles/insights.css";

function shortDate(value: string | number) {
  return new Date(value).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function formatHour(hour: number) {
  const date = new Date();
  date.setHours(hour, 0, 0, 0);
  return date.toLocaleTimeString(undefined, { hour: "numeric" });
}

export default function AudienceInsightsPage({ user }: { user: User }) {
  const [range, setRange] = useState<AnalyticsRange>("30d");
  const [audience, setAudience] = useState<PageAudienceInsightsDto | null>(null);
  const [content, setContent] = useState<ContentInsightsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setLoading(true);
      setError("");
      Promise.allSettled([
        getPageAudience(range, controller.signal),
        getContentInsights(range, null, controller.signal),
      ])
        .then(([audienceRes, contentRes]) => {
          if (!active) return;
          if (audienceRes.status === "fulfilled") setAudience(audienceRes.value.data);
          if (contentRes.status === "fulfilled") setContent(contentRes.value.data);
          if (audienceRes.status === "rejected" && contentRes.status === "rejected") {
            setError("Could not load audience insights. Check that the backend is running and V42 is migrated.");
          }
        })
        .finally(() => {
          if (active && !controller.signal.aborted) setLoading(false);
        });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [range, refreshKey]);

  const followerTrend = (audience?.followerTrend ?? [])
    .filter((p) => p.followers != null)
    .map((p) => ({ date: p.date, Followers: p.followers as number }));

  return (
    <AnalyticsLayout user={user} page="Audience">
      <div className="analytics-page insights-page" data-role={user.role}>
        <div className="screen-header analytics-header">
          <div>
            <h1 className="screen-title">Audience</h1>
            <p className="screen-subtitle">Who follows your page and how your content reaches them</p>
            {audience?.latestFetchedAt && (
              <div className="analytics-meta-row">
                <span className="analytics-scope-badge">Page-level</span>
                <span className="analytics-period">Last synced {formatDateTime(audience.latestFetchedAt)}</span>
              </div>
            )}
          </div>
          <div className="analytics-toolbar">
            <div className="analytics-segmented" aria-label="Audience range">
              {INSIGHT_RANGES.map((item) => (
                <button key={item.value} type="button" className={range === item.value ? "active" : ""} onClick={() => setRange(item.value)}>
                  {item.label}
                </button>
              ))}
            </div>
            <button type="button" className="btn-secondary" onClick={() => setRefreshKey((c) => c + 1)} disabled={loading}>
              <i className="ti ti-refresh" aria-hidden="true" />
              {loading ? "Refreshing…" : "Refresh"}
            </button>
          </div>
        </div>

        {loading && (
          <div className="analytics-loading">
            {Array.from({ length: 5 }).map((_, i) => <div className="analytics-skeleton" key={i} />)}
          </div>
        )}

        {!loading && error && (
          <div className="analytics-state">
            <i className="ti ti-chart-dots-3" aria-hidden="true" />
            <p>{error}</p>
          </div>
        )}

        {!loading && !error && (
          <>
            {/* Followers (page-level — independent of published posts) */}
            <section className="ins-panel ins-panel-wide">
              <div className="ins-panel-head">
                <h2>Followers</h2>
                <p>Total followers of the DASIG Facebook Page and growth over the selected range.</p>
              </div>
              {audience?.available ? (
                <>
                  <section className="ins-tiles">
                    <MetricTile
                      label="Total followers"
                      value={formatNumber(audience.followersCount ?? 0)}
                      sublabel={`${audience.netFollowerChange >= 0 ? "+" : ""}${formatNumber(audience.netFollowerChange)} in range`}
                      active
                    />
                    <MetricTile label="Page likes" value={formatNumber(audience.fanCount ?? 0)} sublabel="lifetime" />
                  </section>
                  {followerTrend.length > 1 ? (
                    <InsightAreaChart
                      data={followerTrend}
                      xKey="date"
                      formatX={shortDate}
                      series={[{ key: "Followers", name: "Followers", color: "#155eef" }]}
                    />
                  ) : (
                    <p className="ins-bar-caption">Follower trend builds as daily snapshots accumulate.</p>
                  )}
                </>
              ) : (
                <div className="ins-soon">
                  <i className="ti ti-users-group" aria-hidden="true" />
                  <p><strong>No page-audience snapshot yet.</strong></p>
                  <p>An administrator can run a sync (the daily job stores one automatically), then follower growth appears here.</p>
                </div>
              )}
            </section>

            {/* Demographics (best-effort — Meta deprecated most page_fans_* metrics) */}
            <section className="ins-panel">
              <div className="ins-panel-head">
                <h2>Demographics</h2>
                <p>Age &amp; gender, country, and cities of your followers.</p>
              </div>
              {audience?.demographicsAvailable ? (
                <div className="ins-grid">
                  {audience.ageGender.length > 0 && (
                    <div>
                      <h3 className="ins-sub-h">Age &amp; gender</h3>
                      <BreakdownBars rows={audience.ageGender.map((d) => ({ label: d.label, value: d.value, display: formatNumber(d.value) }))} />
                    </div>
                  )}
                  {audience.countries.length > 0 && (
                    <div>
                      <h3 className="ins-sub-h">Top countries</h3>
                      <BreakdownBars color="#12B5A6" rows={audience.countries.map((d) => ({ label: d.label, value: d.value, display: formatNumber(d.value) }))} />
                    </div>
                  )}
                  {audience.cities.length > 0 && (
                    <div>
                      <h3 className="ins-sub-h">Top cities</h3>
                      <BreakdownBars color="#7C3AED" rows={audience.cities.map((d) => ({ label: d.label, value: d.value, display: formatNumber(d.value) }))} />
                    </div>
                  )}
                </div>
              ) : (
                <div className="ins-soon">
                  <i className="ti ti-chart-pie" aria-hidden="true" />
                  <p><strong>Demographic breakdowns are unavailable.</strong></p>
                  <p>
                    Meta has deprecated most page follower demographic metrics
                    (<code>page_fans_gender_age</code>, <code>page_fans_country</code>, <code>page_fans_city</code>).
                    They display here automatically if the Graph API returns them for this page.
                  </p>
                </div>
              )}
            </section>

            {/* Reach & behaviour (derived from published-post metrics) */}
            <section className="ins-panel">
              <div className="ins-panel-head">
                <h2>Reach &amp; behaviour</h2>
                <p>How your published content reached people, and the best windows to publish.</p>
              </div>
              {content?.metricsReady ? (
                <>
                  <section className="ins-tiles">
                    <MetricTile label="Avg reach" value={formatNumber(content.overview.averageReach)} sublabel="per synced post" />
                    <MetricTile label="Avg impressions" value={formatNumber(content.overview.averageImpressions)} sublabel="per synced post" />
                    <MetricTile label="Synced posts" value={formatNumber(content.overview.syncedPosts)} sublabel="with current snapshots" />
                  </section>
                  <ul className="ins-window-list">
                    {content.postingWindows.length === 0 && <li className="ins-chart-empty">Not enough synced posts yet.</li>}
                    {content.postingWindows.map((w) => (
                      <li className="ins-window" key={`${w.dayOfWeek}-${w.hourOfDay}`}>
                        <div><strong>{w.dayOfWeek}</strong><span>{formatHour(w.hourOfDay)}</span></div>
                        <div><strong>{w.averageEngagements.toFixed(1)}</strong><span>avg engagement</span></div>
                        <div><strong>{formatNumber(Math.round(w.averageViews))}</strong><span>avg views</span></div>
                      </li>
                    ))}
                  </ul>
                </>
              ) : (
                <p className="ins-bar-caption">Reach &amp; posting windows appear once published posts have synced metrics.</p>
              )}
            </section>
          </>
        )}
      </div>
    </AnalyticsLayout>
  );
}
