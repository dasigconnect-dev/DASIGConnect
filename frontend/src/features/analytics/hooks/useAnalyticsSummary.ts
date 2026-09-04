import { useCallback, useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  getAnalyticsSummary,
  type AnalyticsRange,
  type AnalyticsSummaryDto,
} from "../../../api/analyticsApi";
import type { User } from "../../../types/auth.types";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";

const ANALYTICS_STALE_TIME_MS = 60_000;
const BACKGROUND_REFRESH_MS = 5 * 60_000;

function getUserCacheScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

function getInstitutionCacheScope(user: User, selectedInstitutionId: string | null) {
  return selectedInstitutionId ?? user.institutionId ?? null;
}

export function useAnalyticsSummary(user: User, initialRange: AnalyticsRange = "30d") {
  const [range, setRangeValue] = useState<AnalyticsRange>(initialRange);
  const [institutionId, setInstitutionIdValue] = useState<string | null>(null);

  const analyticsQuery = useQuery<AnalyticsSummaryDto>({
    queryKey: queryKeys.analytics.summary({
      role: user.role,
      userId: getUserCacheScope(user),
      institutionId: getInstitutionCacheScope(user, institutionId),
      range,
    }),
    queryFn: ({ signal }) => getAnalyticsSummary(range, institutionId, signal).then((res) => res.data),
    meta: authenticatedQueryMeta,
    staleTime: ANALYTICS_STALE_TIME_MS,
  });

  useEffect(() => {
    // Background refresh. Kept long and skipped while the tab is hidden; each
    // tick runs a batch of aggregation queries server-side.
    const intervalId = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        void analyticsQuery.refetch();
      }
    }, BACKGROUND_REFRESH_MS);
    return () => window.clearInterval(intervalId);
  }, [analyticsQuery.refetch]);

  const setRange = useCallback((nextRange: AnalyticsRange) => {
    setRangeValue(nextRange);
  }, []);

  const setInstitutionId = useCallback((nextInstitutionId: string | null) => {
    setInstitutionIdValue(nextInstitutionId);
  }, []);

  const refresh = useCallback(() => {
    void analyticsQuery.refetch();
  }, [analyticsQuery]);

  return {
    range,
    setRange,
    institutionId,
    setInstitutionId,
    summary: analyticsQuery.data ?? null,
    loading: analyticsQuery.isPending || (analyticsQuery.isFetching && !analyticsQuery.data),
    refreshing: analyticsQuery.isFetching,
    error:
      analyticsQuery.isError && !analyticsQuery.data
        ? "Could not load analytics. Check that the backend is running."
        : null,
    refresh,
  };
}
