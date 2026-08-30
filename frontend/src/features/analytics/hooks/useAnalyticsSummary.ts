import { useCallback, useEffect, useState } from "react";
import {
  getAnalyticsSummary,
  type AnalyticsRange,
  type AnalyticsSummaryDto,
} from "../../../api/analyticsApi";

export function useAnalyticsSummary(initialRange: AnalyticsRange = "30d") {
  const [range, setRangeValue] = useState<AnalyticsRange>(initialRange);
  const [institutionId, setInstitutionIdValue] = useState<string | null>(null);
  const [category, setCategoryValue] = useState<string | null>(null);
  const [summary, setSummary] = useState<AnalyticsSummaryDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    getAnalyticsSummary(range, institutionId, category, controller.signal)
      .then((res) => {
        setSummary(res.data);
        setError(null);
      })
      .catch((err: { code?: string }) => {
        if (err?.code !== "ERR_CANCELED") {
          setError("Could not load analytics. Check that the backend is running.");
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [range, institutionId, category, refreshKey]);

  useEffect(() => {
    // Background refresh. Kept long and skipped while the tab is hidden — each
    // tick runs a batch of aggregation queries server-side.
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        setRefreshKey((value) => value + 1);
      }
    }, 5 * 60_000);
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

  const setCategory = useCallback((nextCategory: string | null) => {
    setLoading(true);
    setError(null);
    setCategoryValue(nextCategory);
  }, []);

  const refresh = useCallback(() => {
    setLoading(true);
    setError(null);
    setRefreshKey((value) => value + 1);
  }, []);

  return {
    range,
    setRange,
    institutionId,
    setInstitutionId,
    category,
    setCategory,
    summary,
    loading,
    error,
    refresh,
  };
}
