import { useCallback, useEffect, useState } from "react";
import {
  getAnalyticsSummary,
  type AiPerformanceDto,
  type AnalyticsRange,
  type AnalyticsSummaryDto,
  type FacebookEngagementDto,
  type KpiMetricDto,
} from "../../../api/analyticsApi";

const EMPTY_KPI: KpiMetricDto = {
  id: "empty",
  label: "No data",
  value: 0,
  unit: "count",
  sampleSize: 0,
  target: null,
  targetMet: true,
  deltaPercent: null,
  sparkline: [],
  secondaryLabel: null,
  secondaryValue: null,
};

const EMPTY_AI_PERFORMANCE: AiPerformanceDto = {
  captionSuggestionEvents: 0,
  captionAcceptedEvents: 0,
  captionAcceptanceRate: 0,
  tagClassificationEvents: 0,
  tagCorrectionEvents: 0,
  tagCorrectionRate: 0,
  mediaRecommendationEvents: 0,
  mediaRecommendationRelevantEvents: 0,
  mediaRecommendationRelevanceRate: 0,
  insufficientData: true,
};

const EMPTY_FACEBOOK_ENGAGEMENT: FacebookEngagementDto = {
  syncedPosts: 0,
  reactions: 0,
  comments: 0,
  shares: 0,
  reachSampleSize: 0,
  averageReach: 0,
  averageImpressions: 0,
  latestFetchedAt: null,
};

export function useAnalyticsSummary(initialRange: AnalyticsRange = "30d") {
  const [range, setRangeValue] = useState<AnalyticsRange>(initialRange);
  const [institutionId, setInstitutionIdValue] = useState<string | null>(null);
  const [summary, setSummary] = useState<AnalyticsSummaryDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getAnalyticsSummary(range, institutionId, controller.signal)
      .then((res) => {
        setSummary(normalizeAnalyticsSummary(res.data));
        setError(null);
      })
      .catch((err: { code?: string }) => {
        if (err?.code !== "ERR_CANCELED") {
          setError("Could not load analytics. Check that the backend is running.");
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, [range, institutionId, refreshKey]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setRefreshKey((value) => value + 1);
    }, 60_000);
    return () => window.clearInterval(intervalId);
  }, []);

  const setRange = useCallback((nextRange: AnalyticsRange) => {
    setLoading(true);
    setError(null);
    setRangeValue(nextRange);
  }, []);

  const setInstitutionId = useCallback((nextInstitutionId: string | null) => {
    setLoading(true);
    setError(null);
    setInstitutionIdValue(nextInstitutionId);
  }, []);

  const refresh = useCallback(() => {
    setLoading(true);
    setError(null);
    setRefreshKey((value) => value + 1);
  }, []);

  return { range, setRange, institutionId, setInstitutionId, summary, loading, error, refresh };
}

function normalizeAnalyticsSummary(summary: AnalyticsSummaryDto): AnalyticsSummaryDto {
  return {
    ...summary,
    averagePostingDelay: summary.averagePostingDelay ?? { ...EMPTY_KPI, id: "averagePostingDelay", label: "AVG Posting Delay", unit: "days" },
    contentCompleteness: summary.contentCompleteness ?? { ...EMPTY_KPI, id: "contentCompleteness", label: "Content Completeness", unit: "percent" },
    totalPostsPublished: summary.totalPostsPublished ?? { ...EMPTY_KPI, id: "totalPostsPublished", label: "Total Posts Published", unit: "posts" },
    institutionFilterOptions: summary.institutionFilterOptions ?? [],
    postsByInstitution: summary.postsByInstitution ?? [],
    contributorBreakdown: summary.contributorBreakdown ?? [],
    statusBreakdown: summary.statusBreakdown ?? [],
    contentIssues: summary.contentIssues ?? [],
    topCategories: summary.topCategories ?? [],
    aiPerformance: summary.aiPerformance ?? EMPTY_AI_PERFORMANCE,
    facebookEngagement: summary.facebookEngagement ?? EMPTY_FACEBOOK_ENGAGEMENT,
  };
}
