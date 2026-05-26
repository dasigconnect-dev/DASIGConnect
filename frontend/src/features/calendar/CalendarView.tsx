import type { RefObject } from "react";
import FullCalendar from "@fullcalendar/react";
import dayGridPlugin from "@fullcalendar/daygrid";
import timeGridPlugin from "@fullcalendar/timegrid";
import interactionPlugin from "@fullcalendar/interaction";
import type { EventClickArg, EventContentArg } from "@fullcalendar/core";
import type { CalendarEvent } from "../../api/calendarApi";
import { statusColor } from "./calendarStatus";
import { CalendarEmptyOverlay } from "./CalendarStates";
import type { CalendarViewMode } from "./CalendarToolbar";

interface CalendarViewProps {
  events: CalendarEvent[];
  view: CalendarViewMode;
  calendarRef: RefObject<FullCalendar | null>;
  onEventClick: (event: CalendarEvent) => void;
}

function toFcEvents(events: CalendarEvent[]) {
  return events.map((e) => {
    const color = statusColor(e.status);
    return {
      id: e.id,
      title: e.title ?? `[${e.institutionCode}] Slot`,
      start: e.scheduledAt,
      backgroundColor: color.bg,
      borderColor: color.bg,
      textColor: color.text,
      extendedProps: { event: e },
    };
  });
}

function renderEventContent(arg: EventContentArg) {
  const e = arg.event.extendedProps.event as CalendarEvent;
  return (
    <div className="cal-event-pill" title={arg.event.title}>
      <span className="cal-event-code">[{e.institutionCode}]</span>
      <span className="cal-event-title">{arg.event.title}</span>
    </div>
  );
}

export default function CalendarView({
  events,
  view,
  calendarRef,
  onEventClick,
}: CalendarViewProps) {
  function handleEventClick(arg: EventClickArg) {
    onEventClick(arg.event.extendedProps.event as CalendarEvent);
  }

  return (
    <div className="cal-container">
      {events.length === 0 && <CalendarEmptyOverlay />}
      <FullCalendar
        ref={calendarRef}
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView={view}
        events={toFcEvents(events)}
        eventClick={handleEventClick}
        eventContent={renderEventContent}
        headerToolbar={{
          left: "prev,next today",
          center: "title",
          right: "",
        }}
        height="auto"
        dayMaxEvents={3}
        nowIndicator
      />
    </div>
  );
}
