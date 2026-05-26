import { useCallback, useEffect, useState } from "react";
import {
  getAnalyticsSummary,
  type AnalyticsRange,
  type AnalyticsSummaryDto,
} from "../../../api/analyticsApi";

export function useAnalyticsSummary(initialRange: AnalyticsRange = "30d") {
  const [range, setRange] = useState<AnalyticsRange>(initialRange);
  const [summary, setSummary] = useState<AnalyticsSummaryDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getAnalyticsSummary(range, controller.signal)
      .then((res) => setSummary(res.data))
      .catch((err: { code?: string }) => {
        if (err?.code !== "ERR_CANCELED") {
          setError("Could not load analytics. Check that the backend is running.");
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [range, refreshKey]);

  const refresh = useCallback(() => {
    setRefreshKey((value) => value + 1);
  }, []);

  return { range, setRange, summary, loading, error, refresh };
}
