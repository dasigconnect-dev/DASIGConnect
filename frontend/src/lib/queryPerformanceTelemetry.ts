import { isCancelledError, type Query, type QueryClient, type QueryKey } from "@tanstack/react-query";
import {
  recordQueryCacheAccess,
  recordQueryFetchEnd,
  recordQueryFetchStart,
  recordQueryRetry,
  type FetchClassification,
  type PerformanceOutcome,
} from "./performanceTelemetry";

interface ActiveFetch {
  startedAt: number;
  resource: string;
  classification: FetchClassification;
}

function queryResource(queryKey: QueryKey): string {
  const root = queryKey[0];
  if (typeof root !== "string") return "unknown";
  const normalized = root.toLowerCase().replace(/[^a-z0-9-]/g, "");
  return normalized || "unknown";
}

function cacheClassification(query: Query): string {
  if (query.state.dataUpdatedAt === 0) return "miss";
  return query.isStale() ? "hit-stale" : "hit-fresh";
}

export function installQueryPerformanceInstrumentation(queryClient: QueryClient): () => void {
  const activeFetches = new Map<string, ActiveFetch>();

  const beginFetch = (query: Query) => {
    if (activeFetches.has(query.queryHash)) return;
    const resource = queryResource(query.queryKey);
    const classification: FetchClassification =
      query.state.dataUpdatedAt > 0 ? "background" : "initial";
    activeFetches.set(query.queryHash, {
      resource,
      classification,
      startedAt: recordQueryFetchStart(query.queryHash, resource, classification),
    });
  };

  for (const query of queryClient.getQueryCache().getAll()) {
    if (query.getObserversCount() > 0) {
      recordQueryCacheAccess(queryResource(query.queryKey), cacheClassification(query));
    }
    if (query.state.fetchStatus === "fetching") beginFetch(query);
  }

  return queryClient.getQueryCache().subscribe((event) => {
    const { query } = event;
    const resource = queryResource(query.queryKey);

    if (event.type === "observerAdded") {
      recordQueryCacheAccess(resource, cacheClassification(query));
      return;
    }
    if (event.type !== "updated") return;
    if (event.action.type === "fetch") {
      beginFetch(query);
      return;
    }
    if (event.action.type === "failed") {
      recordQueryRetry(resource, event.action.failureCount);
      return;
    }
    if (event.action.type !== "success" && event.action.type !== "error") return;

    const fetch = activeFetches.get(query.queryHash);
    activeFetches.delete(query.queryHash);
    if (!fetch) return;
    const outcome: PerformanceOutcome =
      event.action.type === "success"
        ? "success"
        : isCancelledError(event.action.error)
          ? "cancel"
          : "error";
    recordQueryFetchEnd(
      query.queryHash,
      fetch.resource,
      fetch.classification,
      fetch.startedAt,
      outcome,
    );
  });
}
