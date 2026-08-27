import { api } from "./authApi";

export interface CalendarEvent {
  id: string;
  title: string | null;
  institutionId: string;
  institutionName: string;
  institutionCode: string;
  status: string;
  scheduledAt: string;
  publishedAt: string | null;
  caption?: string | null;
  description?: string | null;
  contributorName?: string | null;
  locked: boolean;
  /** True when this event is the viewer's own authored submission (own-workflow bucket). */
  mine: boolean;
}

export function getCalendarEvents(signal?: AbortSignal) {
  return api.get<CalendarEvent[]>("/calendar", { signal });
}

export function rescheduleSubmission(
  id: string,
  scheduledAt: string,
  overrideReason?: string,
  signal?: AbortSignal,
) {
  return api.patch<{ id: string }>(
    `/submissions/${id}/reschedule`,
    { scheduledAt, overrideReason },
    { signal },
  );
}
