import { useCallback, useEffect, useState } from "react";
import {
  getSubmissionLookups,
  listSubmissions,
  type SubmissionLookups,
  type SubmissionSummary,
} from "../api/submissionApi";

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

let cachedSubmissions: SubmissionSummary[] | null = null;
let cachedLookups: SubmissionLookups | null = null;

export function useSubmissions() {
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>(() => cachedSubmissions ?? []);
  const [loading, setLoading] = useState(() => cachedSubmissions === null);
  const [error, setError] = useState("");

  const refresh = useCallback(async (signal?: AbortSignal) => {
    if (!cachedSubmissions) {
      setLoading(true);
    }
    setError("");
    try {
      const response = await listSubmissions(signal);
      if (!signal?.aborted) {
        cachedSubmissions = response.data;
        setSubmissions(response.data);
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
    void refresh(controller.signal);
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
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  return { lookups, loading, error, refresh };
}
