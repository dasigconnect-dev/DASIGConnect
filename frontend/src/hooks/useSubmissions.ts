import { useCallback, useEffect, useState, type Dispatch, type SetStateAction } from "react";
import {
  getSubmissionLookups,
  listSubmissions,
  type SubmissionLookups,
  type SubmissionSummary,
} from "../api/submissionApi";
import { registerAppCacheReset } from "../lib/appCache";

const emptyLookups: SubmissionLookups = {
  allowedFileTypes: [],
  allowedImageTypes: [],
  allowedVideoTypes: [],
  maxFileSizeMb: 50,
  maxMediaAssetsPerSubmission: 10,
  maxTitleLength: 255,
  minScheduleLeadTimeHours: 2,
  maxScheduleDaysAhead: 30,
  categories: [],
  availableTags: [],
};

// Module-scoped caches. The mount effects skip the network call while the cache
// is younger than its TTL; an explicit refresh() always goes to the server.
// Lookups are near-static config, so they get a much longer TTL than the list.
let cachedSubmissions: SubmissionSummary[] | null = null;
let cachedSubmissionsAt = 0;
let cachedLookups: SubmissionLookups | null = null;
let cachedLookupsAt = 0;
const SUBMISSIONS_TTL_MS = 30_000;
const LOOKUPS_TTL_MS = 5 * 60_000;
registerAppCacheReset(() => {
  cachedSubmissions = null;
  cachedSubmissionsAt = 0;
  cachedLookups = null;
  cachedLookupsAt = 0;
});

export function useSubmissions() {
  const [submissions, setSubmissionsState] = useState<SubmissionSummary[]>(() => cachedSubmissions ?? []);
  const [loading, setLoading] = useState(() => cachedSubmissions === null);
  const [error, setError] = useState("");

  // Every local mutation (save / submit / withdraw / delete) flows through here,
  // so mirror it into the module cache — otherwise a remount within the TTL
  // would show a stale list that's missing the just-saved change.
  const setSubmissions = useCallback<Dispatch<SetStateAction<SubmissionSummary[]>>>((action) => {
    setSubmissionsState((prev) => {
      const next =
        typeof action === "function"
          ? (action as (p: SubmissionSummary[]) => SubmissionSummary[])(prev)
          : action;
      cachedSubmissions = next;
      return next;
    });
  }, []);

  const refresh = useCallback(async (signal?: AbortSignal) => {
    if (!cachedSubmissions) {
      setLoading(true);
    }
    setError("");
    try {
      const response = await listSubmissions(signal);
      if (!signal?.aborted) {
        cachedSubmissions = response.data;
        cachedSubmissionsAt = Date.now();
        setSubmissionsState(response.data);
      }
      return response.data;
    } catch (err: any) {
      if (err.name === "CanceledError" || err.name === "AbortError" || err?.code === "ERR_CANCELED") {
        return;
      }
      setError(
        err.response?.data?.error ||
          err.message ||
          "Unable to load submissions.",
      );
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const fresh =
      cachedSubmissions !== null && Date.now() - cachedSubmissionsAt < SUBMISSIONS_TTL_MS;
    if (!fresh) {
      void refresh(controller.signal);
    }
    return () => controller.abort();
  }, [refresh]);

  return { submissions, setSubmissions, loading, error, refresh };
}

export function useSubmissionLookups() {
  const [lookups, setLookups] = useState<SubmissionLookups>(() => cachedLookups ?? emptyLookups);
  const [loading, setLoading] = useState(() => cachedLookups === null);
  const [error, setError] = useState("");

  const refresh = useCallback(async (signal?: AbortSignal) => {
    if (!cachedLookups) {
      setLoading(true);
    }
    setError("");
    try {
      const response = await getSubmissionLookups(signal);
      if (!signal?.aborted) {
        cachedLookups = response.data;
        cachedLookupsAt = Date.now();
        setLookups(response.data);
      }
      return response.data;
    } catch (err: any) {
      if (err.name === "CanceledError" || err.name === "AbortError" || err?.code === "ERR_CANCELED") {
        return;
      }
      setError(
        err.response?.data?.error ||
          err.message ||
          "Unable to load submission settings.",
      );
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const fresh =
      cachedLookups !== null && Date.now() - cachedLookupsAt < LOOKUPS_TTL_MS;
    if (!fresh) {
      void refresh(controller.signal);
    }
    return () => controller.abort();
  }, [refresh]);

  return { lookups, loading, error, refresh };
}
