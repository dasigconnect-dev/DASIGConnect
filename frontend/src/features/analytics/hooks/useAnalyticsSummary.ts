import { useCallback, useEffect, useState } from "react";
import {
  getAnalyticsSummary,
  type AnalyticsRange,
  type AnalyticsSummaryDto,
} from "../../../api/analyticsApi";

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
