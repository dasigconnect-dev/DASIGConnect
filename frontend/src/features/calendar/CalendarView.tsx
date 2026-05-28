import type { RefObject } from "react";
import { useEffect, useRef } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { EventClickArg, EventContentArg, EventMountArg, EventDropArg } from "@fullcalendar/core";
import type { DatesSetArg } from "@fullcalendar/core";
import type { CalendarEvent } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { visibleStatusColor, visibleStatusLabel } from "./calendarStatus";
import { CalendarEmptyOverlay, CalendarLoadingOverlay } from "./CalendarStates";
import type { CalendarViewMode } from "./CalendarToolbar";

const DRAGGABLE_STATUSES = ["scheduled", "direct_post_scheduled"];

export interface CalendarDropInfo {
  event: CalendarEvent;
  newStart: Date;
  revert: () => void;
}

interface CalendarViewProps {
  events: CalendarEvent[];
  initialView: CalendarViewMode;
  calendarRef: RefObject<FullCalendar | null>;
  showFullDay: boolean;
  highlightedEventId: string | null;
  scrollToEventId: string | null;
  onScrollComplete: () => void;
  isBusy: boolean;
  user: User;
  onEventClick: (event: CalendarEvent) => void;
  onDatesSet: (arg: DatesSetArg) => void;
  /** Admin-only: if provided, events with a reschedulable status become draggable. */
  onEventDrop?: (info: CalendarDropInfo) => void;
}

function eventTitle(e: CalendarEvent) {
  return e.title?.trim() || "Reserved publishing slot";
}

function eventInstitution(e: CalendarEvent) {
  return e.institutionName?.trim() || e.institutionCode || "Institution";
}

function eventTime(iso: string) {
  return new Date(iso).toLocaleTimeString("en-PH", {
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function isOwnInstitution(e: CalendarEvent, user: User) {
  return Boolean(user.institutionId && e.institutionId && user.institutionId === e.institutionId);
}

function toFcEvents(events: CalendarEvent[], user: User, draggable: boolean) {
  return events.map((e) => {
    const color = visibleStatusColor(e.status, user.role, isOwnInstitution(e, user));
    return {
      id: e.id,
      title: eventTitle(e),
      start: e.scheduledAt,
      backgroundColor: color.bg,
      borderColor: color.bg,
      textColor: color.text,
      editable: draggable && DRAGGABLE_STATUSES.includes(e.status),
      extendedProps: { event: e },
    };
  });
}

function renderEventContent(arg: EventContentArg, user: User, draggable: boolean) {
  const e = arg.event.extendedProps.event as CalendarEvent;
  const isOwn = isOwnInstitution(e, user);
  const color = visibleStatusColor(e.status, user.role, isOwn);
  const isDraggable = draggable && DRAGGABLE_STATUSES.includes(e.status);
  return (
    <div
      className={`cal-event-pill${isDraggable ? " cal-event-draggable" : ""}`}
      title={`${eventInstitution(e)} - ${eventTitle(e)}${isDraggable ? " (drag to reschedule)" : ""}`}
    >
      {isDraggable && (
        <span className="cal-event-drag-handle" aria-hidden="true">
          <i className="ti ti-grip-vertical" />
        </span>
      )}
      <span className="cal-event-dot" style={{ background: color.text }} />
      <span className="cal-event-main">
        <span className="cal-event-title">{eventTitle(e)}</span>
        <span className="cal-event-meta">
          {eventInstitution(e)} · {eventTime(e.scheduledAt)} · {visibleStatusLabel(e.status, user.role, isOwn)}
        </span>
      </span>
    </div>
  );
}

export default function CalendarView({
  events,
  initialView,
  calendarRef,
  showFullDay,
  highlightedEventId,
  scrollToEventId,
  onScrollComplete,
  isBusy,
  user,
  onEventClick,
  onDatesSet,
  onEventDrop,
}: CalendarViewProps) {
  const eventElsRef = useRef(new Map<string, HTMLElement>());
  const draggable = Boolean(onEventDrop);

  function handleEventClick(arg: EventClickArg) {
    onEventClick(arg.event.extendedProps.event as CalendarEvent);
  }

  function handleEventMount(info: EventMountArg) {
    eventElsRef.current.set(info.event.id, info.el as HTMLElement);
  }

  function handleEventUnmount(info: EventMountArg) {
    eventElsRef.current.delete(info.event.id);
  }

  function handleEventDrop(arg: EventDropArg) {
    if (!onEventDrop || !arg.event.start) {
      arg.revert();
      return;
    }
    onEventDrop({
      event: (arg.event.extendedProps as { event: CalendarEvent }).event,
      newStart: arg.event.start,
      revert: arg.revert,
    });
  }

  useEffect(() => {
    if (!scrollToEventId) return;
    let frame = 0;
    const maxFrames = 12;
    const attemptScroll = () => {
      const target = eventElsRef.current.get(scrollToEventId);
      if (target) {
        target.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
        onScrollComplete();
        return;
      }
      frame += 1;
      if (frame < maxFrames) {
        requestAnimationFrame(attemptScroll);
      } else {
        onScrollComplete();
      }
    };
    requestAnimationFrame(attemptScroll);
  }, [scrollToEventId, onScrollComplete]);

  return (
    <div className={`cal-container${isBusy ? " cal-container-busy" : ""}`}>
      {isBusy && <CalendarLoadingOverlay />}
      {!isBusy && events.length === 0 && <CalendarEmptyOverlay />}
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView={initialView}
        events={toFcEvents(events, user, draggable)}
        eventClick={handleEventClick}
        eventContent={(arg) => renderEventContent(arg, user, draggable)}
        eventDidMount={handleEventMount}
        eventWillUnmount={handleEventUnmount}
        eventClassNames={(arg) =>
          arg.event.id === highlightedEventId ? ["cal-event-highlighted"] : []
        }
        datesSet={onDatesSet}
        headerToolbar={false}
        height="auto"
        dayMaxEvents={3}
        moreLinkContent={(arg) => `+ ${arg.num} more`}
        slotMinTime={showFullDay ? "00:00:00" : "06:00:00"}
        slotMaxTime={showFullDay ? "24:00:00" : "22:00:00"}
        scrollTime={`${Math.max(new Date().getHours() - 1, showFullDay ? 0 : 6).toString().padStart(2, "0")}:00:00`}
        nowIndicator
        editable={draggable}
        eventDrop={handleEventDrop}
      />
    </div>
  );
}
