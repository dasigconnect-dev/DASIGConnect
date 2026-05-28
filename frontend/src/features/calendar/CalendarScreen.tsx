import { useEffect, useMemo, useRef, useState } from "react";
import FullCalendar from "@fullcalendar/react";
import type { DatesSetArg } from "@fullcalendar/core";
import { createPortal } from "react-dom";
import type { CalendarEvent } from "../../api/calendarApi";
import { rescheduleSubmission } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { useCalendarEvents } from "../../hooks/useCalendarEvents";
import { useToast } from "../../context/ToastContext";
import BrandedSelect from "../../components/ui/BrandedSelect";
import CalendarView, { type CalendarDropInfo } from "./CalendarView";
import CalendarEventDetailModal from "./CalendarEventDetailModal";
import CalendarRescheduleModal from "./CalendarRescheduleModal";
import CalendarLegend from "./CalendarLegend";
import CalendarToolbar, { type CalendarViewMode } from "./CalendarToolbar";
import { CalendarErrorState } from "./CalendarStates";
import { visibleCalendarStatus } from "./calendarStatus";

interface CalendarScreenProps {
  user: User;
}

export default function CalendarScreen({ user }: CalendarScreenProps) {
  const calendarRef = useRef<FullCalendar>(null);
  const { events, loading, error, refresh } = useCalendarEvents();
  const toast = useToast();
  const [selected, setSelected] = useState<CalendarEvent | null>(null);
  const [pendingReschedule, setPendingReschedule] = useState<CalendarDropInfo | null>(null);
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

  function handleEventDrop(info: CalendarDropInfo) {
    const minAllowed = new Date(Date.now() + 60 * 60 * 1000);
    if (info.newStart <= minAllowed) {
      info.revert();
      toast.error(
        info.newStart <= new Date()
          ? "Cannot reschedule to a time in the past."
          : "Cannot reschedule to a time within the next hour.",
      );
      return;
    }
    setPendingReschedule(info);
  }

  async function handleRescheduleConfirm(reason: string) {
    if (!pendingReschedule) return;
    await rescheduleSubmission(
      pendingReschedule.event.id,
      pendingReschedule.newStart.toISOString(),
      reason,
    );
    setPendingReschedule(null);
    refresh();
  }

  function handleRescheduleCancel() {
    pendingReschedule?.revert();
    setPendingReschedule(null);
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
      user,
    }));
  }, [events, institutionFilter, statusFilter, dateFilter, user]);

  const metrics = useMemo(() => {
    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const endOfToday = new Date(startOfToday.getTime() + 86400000);
    return {
      scheduled: events.filter((event) => {
        const status = visibleEventStatus(event, user);
        return ["scheduled", "direct_post_scheduled"].includes(status);
      }).length,
      published: events.filter((event) => ["published", "published_manual"].includes(visibleEventStatus(event, user))).length,
      failed: events.filter((event) => visibleEventStatus(event, user).includes("failed")).length,
      attention: events.filter((event) => ["pending", "in_review", "needs_revision", "rejected"].includes(visibleEventStatus(event, user))).length,
      today: events.filter((event) => {
        const date = new Date(event.scheduledAt);
        return date >= startOfToday && date < endOfToday;
      }).length,
    };
  }, [events, user]);

  const metricEvents = useMemo(() => {
    if (!activeMetric) return [];
    return events.filter((event) => matchesMetric(event, activeMetric, user));
  }, [activeMetric, events, user]);

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

  useEffect(() => {
    if (user.role === "contributor" && statusFilter === "attention") {
      setStatusFilter("all");
    }
  }, [statusFilter, user.role]);

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
    : "View scheduled and published posts across all institutions";
  const myInstitutionLabel = user.inst ? `My Institution (${user.inst})` : "My Institution";
  const myInstitutionEntry =
    !isAdmin && user.institutionId
      ? [{ value: user.institutionId, label: myInstitutionLabel }]
      : [];
  const institutionOptions = [
    { value: "all", label: "All institutions" },
    ...myInstitutionEntry,
    ...institutions
      .filter(([id]) => isAdmin || id !== user.institutionId)
      .map(([id, name]) => ({ value: id, label: name })),
  ];
  const statusOptions = [
    { value: "all", label: "All statuses" },
    { value: "scheduled", label: "Scheduled" },
    { value: "published", label: "Published" },
    ...(isAdmin
      ? [
          { value: "failed", label: "Failed" },
          { value: "attention", label: "Needs attention" },
        ]
      : []),
  ];
  const dateOptions = [
    { value: "all", label: "All dates" },
    { value: "today", label: "Today" },
    { value: "7d", label: "Next 7 days" },
    { value: "30d", label: "Next 30 days" },
  ];

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

      <section className="cal-overview-grid" aria-label="Publishing metrics">
        <MetricCard metric="scheduled" icon="ti ti-calendar-time" label="Scheduled Posts" value={metrics.scheduled} tone="blue" onOpen={setActiveMetric} />
        <MetricCard metric="published" icon="ti ti-circle-check" label="Published" value={metrics.published} tone="green" onOpen={setActiveMetric} />
        {isAdmin && (
          <MetricCard metric="failed" icon="ti ti-alert-circle" label="Failed" value={metrics.failed} tone="red" onOpen={setActiveMetric} />
        )}
        {isAdmin && (
          <MetricCard metric="attention" icon="ti ti-alert-triangle" label="Needs Attention" value={metrics.attention} tone="orange" onOpen={setActiveMetric} />
        )}
        <MetricCard metric="today" icon="ti ti-sun" label="Upcoming Today" value={metrics.today} tone="purple" onOpen={setActiveMetric} />
      </section>

      <section className="cal-filter-bar" aria-label="Calendar filters">
        <div className="cal-filter-field">
          <span className="cal-filter-label">Institution</span>
          <BrandedSelect
            value={institutionFilter}
            options={institutionOptions}
            onChange={setInstitutionFilter}
            ariaLabel="Filter calendar by institution"
            className="cal-filter-select"
          />
        </div>
        <div className="cal-filter-field">
          <span className="cal-filter-label">Status</span>
          <BrandedSelect
            value={statusFilter}
            options={statusOptions}
            onChange={setStatusFilter}
            ariaLabel="Filter calendar by status"
            className="cal-filter-select"
          />
        </div>
        <div className="cal-filter-field">
          <span className="cal-filter-label">Date Range</span>
          <BrandedSelect
            value={dateFilter}
            options={dateOptions}
            onChange={setDateFilter}
            ariaLabel="Filter calendar by date range"
            className="cal-filter-select"
          />
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
            user={user}
            onEventClick={setSelected}
            onDatesSet={handleDatesSet}
            onEventDrop={isAdmin ? handleEventDrop : undefined}
          />
          <CalendarLegend user={user} />
        </>
      )}

      <CalendarEventDetailModal
        event={selected}
        user={user}
        onClose={() => setSelected(null)}
      />

      {pendingReschedule && (
        <CalendarRescheduleModal
          event={pendingReschedule.event}
          newStart={pendingReschedule.newStart}
          onConfirm={handleRescheduleConfirm}
          onCancel={handleRescheduleCancel}
        />
      )}

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
            user,
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
            user,
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

function visibleEventStatus(event: CalendarEvent, user: User) {
  return visibleCalendarStatus(event.status, user.role, event.institutionId === user.institutionId);
}

function matchesMetric(event: CalendarEvent, metric: MetricKey, user: User) {
  const status = visibleEventStatus(event, user);
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
    user: User;
  },
) {
  const status = visibleEventStatus(event, options.user);
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

function statusFilterForEvent(event: CalendarEvent, user: User) {
  const value = visibleEventStatus(event, user);
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
    user: User;
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
    user: options.user,
  })) {
    nextStatus = statusFilterForEvent(event, options.user);
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
