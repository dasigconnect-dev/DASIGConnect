const EVENT_NAME = "dasigconnect:performance";
const MARK_PREFIX = "dasigconnect";
const UUID_SEGMENT = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const OPAQUE_SEGMENT = /^[A-Za-z0-9_-]{24,}$/;

export type PerformanceOutcome = "success" | "error" | "timeout" | "cancel" | "retry";
export type FetchClassification = "initial" | "background";

export interface PerformanceTelemetryDetail {
  metric: string;
  timestamp: number;
  durationMs?: number;
  route?: string;
  endpoint?: string;
  method?: string;
  resource?: string;
  classification?: string;
  outcome?: PerformanceOutcome;
  status?: number;
  attempt?: number;
  source?: string;
}

interface RouteSession {
  id: number;
  route: string;
  activatedAt: number;
  pendingQueries: Set<string>;
  dataReady: boolean;
  settleTimer?: number;
}

export interface PerformanceTimer {
  startedAt: number;
  endpoint: string;
  method: string;
}

export interface LoadingTimer {
  startedAt: number;
  route: string;
  source: string;
}

let navigationInstalled = false;
let pendingNavigation: { route: string; startedAt: number; source: string } | null = null;
let activeRouteSession: RouteSession | null = null;
let routeSessionId = 0;

function now(): number {
  return typeof performance === "undefined" ? Date.now() : performance.now();
}

function safePath(value: string | URL | null | undefined, externalLabel = "/external"): string {
  if (!value) return "/unknown";
  try {
    const url = new URL(String(value), window.location.origin);
    if (url.origin !== window.location.origin) return externalLabel;
    const segments = url.pathname.split("/").map((segment) => {
      const decoded = decodeURIComponent(segment);
      if (
        UUID_SEGMENT.test(decoded) ||
        /^\d+$/.test(decoded) ||
        decoded.includes("@") ||
        OPAQUE_SEGMENT.test(decoded)
      ) {
        return ":id";
      }
      return segment;
    });
    return segments.join("/") || "/";
  } catch {
    return "/unknown";
  }
}

function emit(detail: Omit<PerformanceTelemetryDetail, "timestamp">): void {
  const payload: PerformanceTelemetryDetail = {
    ...detail,
    timestamp: Date.now(),
    ...(detail.durationMs === undefined
      ? {}
      : { durationMs: Math.max(0, Math.round(detail.durationMs * 100) / 100) }),
  };
  if (typeof performance !== "undefined") {
    const entryName = `${MARK_PREFIX}:${detail.metric}`;
    if (detail.durationMs === undefined) {
      performance.clearMarks(entryName);
      performance.mark(entryName, { detail: payload });
    } else {
      performance.clearMeasures(entryName);
      performance.measure(entryName, {
        start: Math.max(0, performance.now() - detail.durationMs),
        duration: Math.max(0, detail.durationMs),
        detail: payload,
      });
    }
  }
  window.dispatchEvent(new CustomEvent<PerformanceTelemetryDetail>(EVENT_NAME, { detail: payload }));
}

function scheduleRouteDataReady(session: RouteSession): void {
  if (session.dataReady || session.pendingQueries.size > 0 || session.settleTimer !== undefined) return;
  session.settleTimer = window.setTimeout(() => {
    session.settleTimer = undefined;
    if (activeRouteSession !== session || session.dataReady || session.pendingQueries.size > 0) return;
    session.dataReady = true;
    const durationMs = now() - session.activatedAt;
    emit({ metric: "route-data-ready", route: session.route, durationMs });
    emit({ metric: "route-loading-interval", route: session.route, durationMs });
  }, 0);
}

export function installNavigationStartInstrumentation(): void {
  if (navigationInstalled) return;
  navigationInstalled = true;

  const markNavigation = (value: string | URL | null | undefined, source: string) => {
    const route = safePath(value ?? window.location.pathname);
    pendingNavigation = { route, startedAt: now(), source };
    emit({ metric: "route-navigation-start", route, source });
  };

  const originalPushState = window.history.pushState.bind(window.history);
  const originalReplaceState = window.history.replaceState.bind(window.history);
  window.history.pushState = (data, unused, url) => {
    markNavigation(url, "push");
    return originalPushState(data, unused, url);
  };
  window.history.replaceState = (data, unused, url) => {
    markNavigation(url, "replace");
    return originalReplaceState(data, unused, url);
  };
  window.addEventListener("popstate", () => markNavigation(window.location.pathname, "pop"));
  markNavigation(window.location.pathname, "document");
}

export function activateRoute(pathname: string, fetchingQueryHashes: string[]): number {
  const route = safePath(pathname);
  const activatedAt = now();
  const navigation = pendingNavigation?.route === route ? pendingNavigation : null;
  pendingNavigation = null;

  emit({ metric: "route-activated", route });
  if (navigation) {
    emit({
      metric: "route-navigation-duration",
      route,
      source: navigation.source,
      durationMs: activatedAt - navigation.startedAt,
    });
  }

  if (activeRouteSession?.settleTimer !== undefined) {
    window.clearTimeout(activeRouteSession.settleTimer);
  }
  const session: RouteSession = {
    id: ++routeSessionId,
    route,
    activatedAt,
    pendingQueries: new Set(fetchingQueryHashes),
    dataReady: false,
  };
  activeRouteSession = session;
  scheduleRouteDataReady(session);
  return session.id;
}

export function markRouteCriticalUiReady(sessionId: number): void {
  const session = activeRouteSession;
  if (!session || session.id !== sessionId) return;
  emit({
    metric: "route-critical-ui-ready",
    route: session.route,
    durationMs: now() - session.activatedAt,
  });
}

export function beginLoadingInterval(pathname: string, source: string): LoadingTimer {
  const timer = { startedAt: now(), route: safePath(pathname), source };
  emit({ metric: "loading-start", route: timer.route, source });
  return timer;
}

export function endLoadingInterval(timer: LoadingTimer): void {
  emit({
    metric: "loading-duration",
    route: timer.route,
    source: timer.source,
    durationMs: now() - timer.startedAt,
  });
}

export function beginApiRequest(url: string | undefined, method: string | undefined): PerformanceTimer {
  return {
    startedAt: now(),
    endpoint: safePath(url, "/external-transfer"),
    method: (method ?? "get").toUpperCase(),
  };
}

export function beginExternalTransfer(method: string | undefined): PerformanceTimer {
  return {
    startedAt: now(),
    endpoint: "/external-transfer",
    method: (method ?? "get").toUpperCase(),
  };
}

export function finishApiRequest(
  timer: PerformanceTimer | undefined,
  outcome: Exclude<PerformanceOutcome, "retry">,
  status?: number,
): void {
  if (!timer) return;
  emit({
    metric: "api-request",
    endpoint: timer.endpoint,
    method: timer.method,
    outcome,
    status,
    durationMs: now() - timer.startedAt,
  });
}

export function recordQueryCacheAccess(resource: string, classification: string): void {
  emit({ metric: "query-cache-access", resource, classification });
}

export function recordQueryFetchStart(
  queryHash: string,
  resource: string,
  classification: FetchClassification,
): number {
  activeRouteSession?.pendingQueries.add(queryHash);
  emit({ metric: "query-fetch-start", resource, classification });
  return now();
}

export function recordQueryRetry(resource: string, attempt: number): void {
  emit({ metric: "query-retry", resource, outcome: "retry", attempt });
}

export function recordQueryFetchEnd(
  queryHash: string,
  resource: string,
  classification: FetchClassification,
  startedAt: number,
  outcome: PerformanceOutcome,
): void {
  emit({
    metric: "query-fetch",
    resource,
    classification,
    outcome,
    durationMs: now() - startedAt,
  });
  const session = activeRouteSession;
  if (session) {
    session.pendingQueries.delete(queryHash);
    scheduleRouteDataReady(session);
  }
}
