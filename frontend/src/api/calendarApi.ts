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
  /** UC-3.1: the slot as of approval -- the anchor for the Moderator reschedule window. */
  originalScheduledAt: string | null;
  /** UC-3.1: how many times a Moderator has rescheduled this post (capped at 2; Admin doesn't count). */
  moderatorRescheduleCount: number;
}

/** UC-3.1: mirrors the backend's Moderator reschedule cap, so the UI can block a drag before opening the confirm modal instead of round-tripping a 403. */
export const MODERATOR_MAX_RESCHEDULES = 2;
export const MODERATOR_RESCHEDULE_WINDOW_MS = 24 * 60 * 60 * 1000;

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
