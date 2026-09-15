import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useLocation } from "react-router-dom";
import { activateRoute, markRouteCriticalUiReady } from "../../lib/performanceTelemetry";

export default function PerformanceRouteObserver() {
  const location = useLocation();
  const queryClient = useQueryClient();

  useEffect(() => {
    const fetchingQueryHashes = queryClient
      .getQueryCache()
      .findAll({ fetchStatus: "fetching" })
      .map((query) => query.queryHash);
    const sessionId = activateRoute(location.pathname, fetchingQueryHashes);
    const frame = window.requestAnimationFrame(() => markRouteCriticalUiReady(sessionId));
    return () => window.cancelAnimationFrame(frame);
  }, [location.key, location.pathname, queryClient]);

  return null;
}
