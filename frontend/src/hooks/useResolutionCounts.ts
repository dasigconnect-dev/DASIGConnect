import { useCallback, useEffect, useState } from "react";
import { getResolutionCounts, type ResolutionCounts } from "../api/resolutionApi";

export function useResolutionCounts(refreshSignal: number) {
  const [counts, setCounts] = useState<ResolutionCounts>({ failures: 0, timeouts: 0, overrides: 0 });

  const fetch = useCallback((signal?: AbortSignal) => {
    getResolutionCounts(signal)
      .then((res) => setCounts(res.data))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetch(controller.signal);
    const interval = setInterval(() => fetch(), 60_000);
    return () => {
      controller.abort();
      clearInterval(interval);
    };
  }, [fetch, refreshSignal]);

  return counts;
}
