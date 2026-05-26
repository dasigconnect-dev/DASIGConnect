import { useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import type { CalendarEvent } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { useCalendarEvents } from "../../hooks/useCalendarEvents";
import CalendarView from "./CalendarView";
import CalendarEventDetailModal from "./CalendarEventDetailModal";
import CalendarLegend from "./CalendarLegend";
import CalendarToolbar, { type CalendarViewMode } from "./CalendarToolbar";
import { CalendarErrorState, CalendarLoadingState } from "./CalendarStates";

interface CalendarScreenProps {
  user: User;
}

export default function CalendarScreen({ user }: CalendarScreenProps) {
  const calendarRef = useRef<FullCalendar>(null);
  const { events, loading, error, refresh } = useCalendarEvents();
  const [selected, setSelected] = useState<CalendarEvent | null>(null);
  const [view, setView] = useState<CalendarViewMode>("dayGridMonth");

  function switchView(nextView: CalendarViewMode) {
    setView(nextView);
    calendarRef.current?.getApi().changeView(nextView);
  }

  return (
    <div className="screen-root">
      <div className="screen-header">
        <div>
          <h1 className="screen-title">Master Calendar</h1>
          <p className="screen-subtitle">
            Scheduled and published content across all institutions
          </p>
        </div>
        <CalendarToolbar
          view={view}
          loading={loading}
          onViewChange={switchView}
          onRefresh={refresh}
        />
      </div>

      {loading && <CalendarLoadingState />}

      {!loading && error && (
        <CalendarErrorState message={error} onRetry={refresh} />
      )}

      {!loading && !error && (
        <>
          <CalendarView
            events={events}
            view={view}
            calendarRef={calendarRef}
            onEventClick={setSelected}
          />
          <CalendarLegend />
        </>
      )}

      <CalendarEventDetailModal
        event={selected}
        role={user.role}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}
