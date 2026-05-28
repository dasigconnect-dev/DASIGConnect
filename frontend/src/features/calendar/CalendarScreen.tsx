import { useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import type { DatesSetArg } from "@fullcalendar/core";
import { createPortal } from "react-dom";
import type { CalendarEvent } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { useCalendarEvents } from "../../hooks/useCalendarEvents";
import CalendarView from "./CalendarView";
import CalendarEventDetailModal from "./CalendarEventDetailModal";
import CalendarLegend from "./CalendarLegend";
import CalendarToolbar, { type CalendarViewMode } from "./CalendarToolbar";
import { CalendarErrorState } from "./CalendarStates";

interface CalendarScreenProps {
  user: User;
}

export default function CalendarScreen({ user }: CalendarScreenProps) {
  const calendarRef = useRef<FullCalendar>(null);
  const { events, loading, error, refresh } = useCalendarEvents();
  const [selected, setSelected] = useState<CalendarEvent | null>(null);
  const [calendarView, setCalendarView] = useState<CalendarViewMode>("dayGridMonth");
  const [calendarRange, setCalendarRange] = useState<{
    start: Date;
    end: Date;
  } | null>(null);
  const [showFullDay, setShowFullDay] = useState(false);
  const [institutionFilter, setInstitutionFilter] = useState("all");
  const [statusFilter, setStatusFilter] = useState("all");
  const [dateFilter, setDateFilter] = useState("all");
  const [activeMetric, setActiveMetric] = useState<MetricKey | null>(null);
  const [highlightedEventId, setHighlightedEventId] = useState<string | null>(null);
  const [pendingNavigation, setPendingNavigation] = useState<{
    date: Date;
    highlightId?: string;
  } | null>(null);
  const [pendingFilterNavigation, setPendingFilterNavigation] = useState(false);
  const [scrollTargetId, setScrollTargetId] = useState<string | null>(null);
  const [isTransitioning, setIsTransitioning] = useState(false);
  const transitionTimeoutRef = useRef<number | null>(null);

  const isCalendarBusy = loading || isTransitioning;

  const beginCalendarTransition = () => {
    setIsTransitioning(true);
    if (transitionTimeoutRef.current) {
      window.clearTimeout(transitionTimeoutRef.current);
    }
  };

  const endCalendarTransition = () => {
    if (transitionTimeoutRef.current) {
      window.clearTimeout(transitionTimeoutRef.current);
    }
    transitionTimeoutRef.current = window.setTimeout(() => {
      setIsTransitioning(false);
    }, 180);
  };

  function switchView(nextView: CalendarViewMode) {
    beginCalendarTransition();
    calendarRef.current?.getApi().changeView(nextView);
  }

  function navigateCalendar(action: "prev" | "today" | "next") {
    const api = calendarRef.current?.getApi();
    if (!api) return;
    beginCalendarTransition();
    api[action]();
  }

  function handleDatesSet(arg: DatesSetArg) {
    const viewType = arg.view.type === "timeGridWeek" ? "timeGridWeek" : "dayGridMonth";
    setCalendarView(viewType);
    setCalendarRange({
      start: new Date(arg.view.currentStart),
      end: new Date(arg.view.currentEnd),
    });
    endCalendarTransition();
  }

  const institutions = useMemo(() => {
    const seen = new Map<string, string>();
    events.forEach((event) => {
      if (event.institutionId) seen.set(event.institutionId, event.institutionName || event.institutionCode);
    });
    return Array.from(seen.entries()).sort((a, b) => a[1].localeCompare(b[1]));
  }, [events]);

  const filteredEvents = useMemo(() => {
    const now = new Date();
    return events.filter((event) => matchesFilters(event, {
      institutionFilter,
      statusFilter,
      dateFilter,
      now,
    }));
  }, [events, institutionFilter, statusFilter, dateFilter]);

  const metrics = useMemo(() => {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfToday = new Date(startOfToday.getTime() + 86400000);
    return {
      scheduled: events.filter((event) => ["scheduled", "direct_post_scheduled"].includes(event.status.toLowerCase())).length,
      published: events.filter((event) => ["published", "published_manual"].includes(event.status.toLowerCase())).length,
      failed: events.filter((event) => event.status.toLowerCase().includes("failed")).length,
      attention: events.filter((event) => ["pending", "in_review", "needs_revision", "rejected"].includes(event.status.toLowerCase())).length,
      today: events.filter((event) => {
        const date = new Date(event.scheduledAt);
        return date >= startOfToday && date < endOfToday;
      }).length,
    };
  }, [events]);

  const metricEvents = useMemo(() => {
    if (!activeMetric) return [];
    return events.filter((event) => matchesMetric(event, activeMetric));
  }, [activeMetric, events]);

  useEffect(() => {
    if (!activeMetric) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setActiveMetric(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [activeMetric]);

  useEffect(() => {
    if (!pendingFilterNavigation) return;
    beginCalendarTransition();
    if (filteredEvents.length === 0) {
      setPendingFilterNavigation(false);
      endCalendarTransition();
      return;
    }
    const earliest = findEarliestEventDate(filteredEvents);
    if (earliest) {
      setPendingNavigation({ date: earliest });
    }
    setPendingFilterNavigation(false);
  }, [filteredEvents, pendingFilterNavigation]);

  useEffect(() => {
    if (!pendingNavigation) return;
    beginCalendarTransition();
    if (pendingNavigation.highlightId && !filteredEvents.some((event) => event.id === pendingNavigation.highlightId)) {
      endCalendarTransition();
      setPendingNavigation(null);
      return;
    }
    const api = calendarRef.current?.getApi();
    if (!api) {
      endCalendarTransition();
      setPendingNavigation(null);
      return;
    }
    api.gotoDate(pendingNavigation.date);
    if (pendingNavigation.highlightId) {
      window.setTimeout(() => {
        setHighlightedEventId(pendingNavigation.highlightId ?? null);
        window.setTimeout(() => setHighlightedEventId(null), 2400);
      }, 0);
    }
    setPendingNavigation(null);
  }, [pendingNavigation, filteredEvents]);

  useEffect(() => {
    beginCalendarTransition();
    endCalendarTransition();
  }, [institutionFilter, statusFilter, dateFilter]);

  useEffect(() => () => {
    if (transitionTimeoutRef.current) {
      window.clearTimeout(transitionTimeoutRef.current);
    }
  }, []);

  const isAdmin = user.role === "admin";
  const rangeLabel = useMemo(() => {
    if (!calendarRange) return "Calendar";
    const fmt = new Intl.DateTimeFormat("en-PH", { month: "short", day: "numeric", year: "numeric" });
    if (calendarView === "dayGridMonth") {
      return new Intl.DateTimeFormat("en-PH", { month: "long", year: "numeric" }).format(calendarRange.start);
    }
    const end = new Date(calendarRange.end.getTime() - 86400000);
    return `${fmt.format(calendarRange.start)} - ${fmt.format(end)}`;
  }, [calendarRange, calendarView]);
  const contextLabel = isAdmin
    ? "System-wide publishing visibility across institutions"
    : "Institution publishing schedule and contributor workflow updates";

  return (
    <div className="screen-root">
      <div className="screen-header">
        <div>
          <h1 className="screen-title">Master Calendar</h1>
          <p className="screen-subtitle">
            {contextLabel}
          </p>
        </div>
      </div>

      <div className="cal-toolbar-row">
        <CalendarToolbar
          view={calendarView}
          loading={loading}
          rangeLabel={rangeLabel}
          showFullDay={showFullDay}
          onViewChange={switchView}
          onNavigate={navigateCalendar}
          onToggleFullDay={() => setShowFullDay((value) => !value)}
          onRefresh={() => {
            beginCalendarTransition();
            refresh();
          }}
        />
      </div>

      <section className="cal-overview-grid" aria-label="Publishing metrics">
        <MetricCard metric="scheduled" icon="ti ti-calendar-time" label="Scheduled Posts" value={metrics.scheduled} tone="blue" onOpen={setActiveMetric} />
        <MetricCard metric="published" icon="ti ti-circle-check" label="Published" value={metrics.published} tone="green" onOpen={setActiveMetric} />
        <MetricCard metric="failed" icon="ti ti-alert-circle" label="Failed" value={metrics.failed} tone="red" onOpen={setActiveMetric} />
        <MetricCard metric="attention" icon="ti ti-alert-triangle" label="Needs Attention" value={metrics.attention} tone="orange" onOpen={setActiveMetric} />
        <MetricCard metric="today" icon="ti ti-sun" label="Upcoming Today" value={metrics.today} tone="purple" onOpen={setActiveMetric} />
      </section>

      <section className="cal-filter-bar" aria-label="Calendar filters">
        <div className="cal-filter-field">
          <label htmlFor="cal-institution-filter">Institution</label>
          <select
            id="cal-institution-filter"
            value={institutionFilter}
            onChange={(event) => setInstitutionFilter(event.target.value)}
          >
            <option value="all">{isAdmin ? "All institutions" : "My institution"}</option>
            {institutions.map(([id, name]) => (
              <option key={id} value={id}>{name}</option>
            ))}
          </select>
        </div>
        <div className="cal-filter-field">
          <label htmlFor="cal-status-filter">Status</label>
          <select
            id="cal-status-filter"
            value={statusFilter}
            onChange={(event) => setStatusFilter(event.target.value)}
          >
            <option value="all">All statuses</option>
            <option value="scheduled">Scheduled</option>
            <option value="published">Published</option>
            <option value="failed">Failed</option>
            <option value="attention">Needs attention</option>
          </select>
        </div>
        <div className="cal-filter-field">
          <label htmlFor="cal-date-filter">Date Range</label>
          <select
            id="cal-date-filter"
            value={dateFilter}
            onChange={(event) => setDateFilter(event.target.value)}
          >
            <option value="all">All dates</option>
            <option value="today">Today</option>
            <option value="7d">Next 7 days</option>
            <option value="30d">Next 30 days</option>
          </select>
        </div>
        <button
          type="button"
          className="btn-secondary btn-sm"
          onClick={() => {
            setInstitutionFilter("all");
            setStatusFilter("all");
            setDateFilter("all");
          }}
        >
          Clear filters
        </button>
      </section>

      {!loading && error && (
        <CalendarErrorState message={error} onRetry={refresh} />
      )}

      {!error && (
        <>
          <CalendarView
            events={filteredEvents}
            initialView={calendarView}
            calendarRef={calendarRef}
            showFullDay={showFullDay}
            highlightedEventId={highlightedEventId}
            scrollToEventId={scrollTargetId}
            onScrollComplete={() => setScrollTargetId(null)}
            isBusy={isCalendarBusy}
            onEventClick={setSelected}
            onDatesSet={handleDatesSet}
          />
          <CalendarLegend />
        </>
      )}

      <CalendarEventDetailModal
        event={selected}
        role={user.role}
        onClose={() => setSelected(null)}
      />

      <CalendarMetricResultsPanel
        metric={activeMetric}
        events={metricEvents}
        onClose={() => setActiveMetric(null)}
        onApplyFilter={(metric) => {
          if (metric === "today") {
            setDateFilter("today");
            setStatusFilter("all");
          } else {
            setStatusFilter(metric);
            setDateFilter("all");
          }
          setPendingFilterNavigation(true);
          setActiveMetric(null);
        }}
        onOpenEvent={(event) => {
          ensureEventVisible(event, {
            institutionFilter,
            statusFilter,
            dateFilter,
            setInstitutionFilter,
            setStatusFilter,
            setDateFilter,
          });
          setPendingNavigation({ date: new Date(event.scheduledAt), highlightId: event.id });
          setScrollTargetId(event.id);
          setSelected(event);
          setActiveMetric(null);
        }}
        onJumpToEvent={(event) => {
          ensureEventVisible(event, {
            institutionFilter,
            statusFilter,
            dateFilter,
            setInstitutionFilter,
            setStatusFilter,
            setDateFilter,
          });
          setPendingNavigation({ date: new Date(event.scheduledAt), highlightId: event.id });
          setScrollTargetId(event.id);
          setActiveMetric(null);
        }}
      />
    </div>
  );
}

type MetricKey = "scheduled" | "published" | "failed" | "attention" | "today";

function MetricCard({
  metric,
  icon,
  label,
  value,
  tone,
  onOpen,
}: {
  metric: MetricKey;
  icon: string;
  label: string;
  value: number;
  tone: "blue" | "green" | "red" | "orange" | "purple";
  onOpen: (metric: MetricKey) => void;
}) {
  return (
    <button
      type="button"
      className={`cal-metric-card cal-metric-${tone}`}
      onClick={() => onOpen(metric)}
    >
      <div className="cal-metric-icon">
        <i className={icon} aria-hidden="true" />
      </div>
      <div>
        <div className="cal-metric-value">{value}</div>
        <div className="cal-metric-label">{label}</div>
      </div>
      <span className="cal-metric-view">
        View <i className="ti ti-arrow-right" aria-hidden="true" />
      </span>
    </button>
  );
}

function CalendarMetricResultsPanel({
  metric,
  events,
  onClose,
  onApplyFilter,
  onOpenEvent,
  onJumpToEvent,
}: {
  metric: MetricKey | null;
  events: CalendarEvent[];
  onClose: () => void;
  onApplyFilter: (metric: MetricKey) => void;
  onOpenEvent: (event: CalendarEvent) => void;
  onJumpToEvent: (event: CalendarEvent) => void;
}) {
  if (!metric) return null;
  const title = METRIC_LABELS[metric];
  return createPortal(
    <div
      className="cal-results-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label={`${title} related schedules`}
      onClick={onClose}
    >
      <section className="cal-results-panel" onClick={(event) => event.stopPropagation()}>
        <div className="cal-results-header">
          <div className="cal-results-heading">
            <div className="cal-results-icon">
              <i className="ti ti-calendar-search" aria-hidden="true" />
            </div>
            <p className="cal-detail-kicker">Workflow drill-down</p>
            <h2>{title}</h2>
            <span>{events.length} related schedule{events.length === 1 ? "" : "s"}</span>
          </div>
          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close">
            <i className="ti ti-x" />
          </button>
        </div>
        <div className="cal-results-actions">
          <button type="button" className="btn-secondary btn-sm" onClick={() => onApplyFilter(metric)}>
            Filter calendar
          </button>
        </div>
        <div className="cal-results-list">
          {events.length === 0 ? (
            <div className="cal-results-empty">No matching schedules right now.</div>
          ) : (
            events.slice(0, 12).map((event) => (
              <article className="cal-result-item" key={event.id}>
                <div>
                  <h3>{event.title ?? "Reserved publishing slot"}</h3>
                  <p>{event.institutionName} · {formatShortDate(event.scheduledAt)}</p>
                </div>
                <div className="cal-result-actions">
                  <button type="button" className="cal-action cal-action-jump" onClick={() => onJumpToEvent(event)}>
                    <i className="ti ti-arrow-right" aria-hidden="true" />
                    Jump
                  </button>
                  <button type="button" className="cal-action cal-action-open" onClick={() => onOpenEvent(event)}>
                    <i className="ti ti-eye" aria-hidden="true" />
                    Open
                  </button>
                </div>
              </article>
            ))
          )}
        </div>
      </section>
    </div>,
    document.body,
  );
}

const METRIC_LABELS: Record<MetricKey, string> = {
  scheduled: "Scheduled Posts",
  published: "Published",
  failed: "Failed",
  attention: "Needs Attention",
  today: "Upcoming Today",
};

function matchesMetric(event: CalendarEvent, metric: MetricKey) {
  const status = event.status.toLowerCase();
  if (metric === "scheduled") return ["scheduled", "direct_post_scheduled"].includes(status);
  if (metric === "published") return ["published", "published_manual"].includes(status);
  if (metric === "failed") return status.includes("failed");
  if (metric === "attention") return ["pending", "in_review", "needs_revision", "rejected"].includes(status);
  const date = new Date(event.scheduledAt);
  const now = new Date();
  return date >= new Date(now.getFullYear(), now.getMonth(), now.getDate())
    && date < new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
}

function matchesFilters(
  event: CalendarEvent,
  options: {
    institutionFilter: string;
    statusFilter: string;
    dateFilter: string;
    now: Date;
  },
) {
  const status = event.status.toLowerCase();
  const scheduledAt = new Date(event.scheduledAt);
  if (options.institutionFilter !== "all" && event.institutionId !== options.institutionFilter) return false;
  if (options.statusFilter === "scheduled" && !["scheduled", "direct_post_scheduled"].includes(status)) return false;
  if (options.statusFilter === "published" && !["published", "published_manual"].includes(status)) return false;
  if (options.statusFilter === "failed" && !status.includes("failed")) return false;
  if (options.statusFilter === "attention" && !["pending", "in_review", "needs_revision", "rejected"].includes(status)) return false;
  if (!matchesDateFilter(scheduledAt, options.dateFilter, options.now)) return false;
  return true;
}

function matchesDateFilter(date: Date, filter: string, now: Date) {
  if (filter === "all") return true;
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const endOfToday = new Date(startOfToday.getTime() + 86400000);
  if (filter === "today") return date >= startOfToday && date < endOfToday;
  if (filter === "7d") return date >= startOfToday && date < new Date(startOfToday.getTime() + 7 * 86400000);
  if (filter === "30d") return date >= startOfToday && date < new Date(startOfToday.getTime() + 30 * 86400000);
  return true;
}

function statusFilterForEvent(status: string) {
  const value = status.toLowerCase();
  if (["scheduled", "direct_post_scheduled"].includes(value)) return "scheduled";
  if (["published", "published_manual"].includes(value)) return "published";
  if (value.includes("failed")) return "failed";
  if (["pending", "in_review", "needs_revision", "rejected"].includes(value)) return "attention";
  return "all";
}

function ensureEventVisible(
  event: CalendarEvent,
  options: {
    institutionFilter: string;
    statusFilter: string;
    dateFilter: string;
    setInstitutionFilter: (value: string) => void;
    setStatusFilter: (value: string) => void;
    setDateFilter: (value: string) => void;
  },
) {
  const now = new Date();
  let nextInstitution = options.institutionFilter;
  if (event.institutionId && options.institutionFilter !== "all" && event.institutionId !== options.institutionFilter) {
    nextInstitution = event.institutionId;
  }

  let nextStatus = options.statusFilter;
  if (!matchesFilters(event, {
    institutionFilter: nextInstitution,
    statusFilter: options.statusFilter,
    dateFilter: "all",
    now,
  })) {
    nextStatus = statusFilterForEvent(event.status);
  }

  let nextDate = options.dateFilter;
  if (!matchesDateFilter(new Date(event.scheduledAt), options.dateFilter, now)) {
    nextDate = "all";
  }

  if (nextInstitution !== options.institutionFilter) options.setInstitutionFilter(nextInstitution);
  if (nextStatus !== options.statusFilter) options.setStatusFilter(nextStatus);
  if (nextDate !== options.dateFilter) options.setDateFilter(nextDate);
}

function findEarliestEventDate(events: CalendarEvent[]) {
  if (events.length === 0) return null;
  return events
    .map((event) => new Date(event.scheduledAt))
    .sort((a, b) => a.getTime() - b.getTime())[0];
}

function formatShortDate(iso: string) {
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}
