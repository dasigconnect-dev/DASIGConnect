import { useEffect, useState } from "react";
import { getContentInsights, type AnalyticsRange, type ContentInsightsDto } from "../../../api/analyticsApi";

/**
 * Shared loader for the Facebook-style insight pages (Views / Engagement / Audience).
 * All three render slices of the same `ContentInsightsDto`, so they share one fetch with
 * a range selector and manual refresh.
 */
export function useContentInsights(initialRange: AnalyticsRange = "30d") {
  const [range, setRange] = useState<AnalyticsRange>(initialRange);
  const [data, setData] = useState<ContentInsightsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    // Defer the setState out of the synchronous effect body (react-hooks/set-state-in-effect).
    queueMicrotask(() => {
      if (!active) return;
      setLoading(true);
      setError("");
      getContentInsights(range, null, controller.signal)
        .then((res) => { if (active) setData(res.data); })
        .catch((err: unknown) => {
          if (active && (err as { name?: string }).name !== "CanceledError") {
            setError("Could not load insights. Check that the backend is running and V41 is migrated.");
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

  return {
    range,
    setRange,
    data,
    loading,
    error,
    refresh: () => setRefreshKey((current) => current + 1),
  };
}

export const INSIGHT_RANGES: Array<{ value: AnalyticsRange; label: string }> = [
  { value: "7d", label: "7D" },
  { value: "30d", label: "30D" },
  { value: "90d", label: "90D" },
  { value: "ytd", label: "YTD" },
];
