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

export interface CalendarQueryRange {
  start: Date;
  end: Date;
}

const CALENDAR_EVENTS_STALE_TIME_MS = 2 * 60_000;

export function useCalendarEvents(user: User, range: CalendarQueryRange | null): UseCalendarEventsResult {
  const queryClient = useQueryClient();
  const userScope = user.id ?? user.email.trim().toLowerCase();
  const startDate = range ? toDateKey(range.start) : "pending";
  const endDate = range ? toDateKey(range.end) : "pending";

  const query = useQuery({
    queryKey: queryKeys.calendarEvents.range({
      role: user.role,
      userId: userScope,
      institutionId: user.institutionId ?? null,
      startDate,
      endDate,
    }),
    queryFn: ({ signal }) => getCalendarEvents(signal).then((response) => response.data),
    enabled: Boolean(range),
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

function toDateKey(date: Date) {
  return date.toISOString().slice(0, 10);
}
