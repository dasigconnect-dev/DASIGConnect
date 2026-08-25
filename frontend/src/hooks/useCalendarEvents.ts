import { useCallback, useEffect, useState } from "react";
import { getCalendarEvents, type CalendarEvent } from "../api/calendarApi";

export interface UseCalendarEventsResult {
  events: CalendarEvent[];
  loading: boolean;
  error: string;
  refresh: () => void;
}

let cachedCalendarEvents: CalendarEvent[] | null = null;

export function useCalendarEvents(): UseCalendarEventsResult {
  const [events, setEvents] = useState<CalendarEvent[]>(() => cachedCalendarEvents ?? []);
  const [loading, setLoading] = useState(() => cachedCalendarEvents === null);
  const [error, setError] = useState("");
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    if (!cachedCalendarEvents) {
      setLoading(true);
    }
    setError("");
    getCalendarEvents(controller.signal)
      .then((res) => {
        cachedCalendarEvents = res.data;
        setEvents(res.data);
      })
      .catch((err: unknown) => {
        if ((err as { name?: string }).name === "CanceledError") return;
        setError("Could not load calendar. Please try again.");
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [tick]);

  const refresh = useCallback(() => setTick((n) => n + 1), []);

  return { events, loading, error, refresh };
}
