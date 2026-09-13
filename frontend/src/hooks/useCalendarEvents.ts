import { useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getCalendarEvents, type CalendarEvent } from "../api/calendarApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

export interface UseCalendarEventsResult {
  events: CalendarEvent[];
  loading: boolean;
  error: string;
  refresh: () => void;
}

const CALENDAR_EVENTS_STALE_TIME_MS = 2 * 60_000;

export function useCalendarEvents(user: User): UseCalendarEventsResult {
  const queryClient = useQueryClient();
  const userScope = user.id ?? user.email.trim().toLowerCase();

  const query = useQuery({
    queryKey: queryKeys.calendarEvents.all({
      role: user.role,
      userId: userScope,
      institutionId: user.institutionId ?? null,
    }),
    queryFn: ({ signal }) => getCalendarEvents(signal).then((response) => response.data),
    staleTime: CALENDAR_EVENTS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["calendar-events"] });
  }, [queryClient]);

  return {
    events: query.data ?? [],
    loading: query.isLoading || query.isFetching,
    error: query.error ? "Could not load calendar. Please try again." : "",
    refresh,
  };
}
