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
