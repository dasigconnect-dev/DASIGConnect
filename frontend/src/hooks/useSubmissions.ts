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

export function useSubmissions() {
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    setError("");
    return listSubmissions(signal)
      .then((response) => setSubmissions(response.data))
      .catch((err: any) => {
        if (err.name === "CanceledError") return;
        setError(
          err.response?.data?.error ||
            err.message ||
            "Unable to load submissions.",
        );
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  return { submissions, setSubmissions, loading, error, refresh };
}

export function useSubmissionLookups() {
  const [lookups, setLookups] = useState<SubmissionLookups>(emptyLookups);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    getSubmissionLookups(controller.signal)
      .then((response) => setLookups(response.data))
      .catch((err: any) => {
        if (err.name === "CanceledError") return;
        setError(
          err.response?.data?.error ||
            err.message ||
            "Unable to load submission options.",
        );
      })
      .finally(() => setLoading(false));

    return () => controller.abort();
  }, []);

  return { lookups, loading, error };
}
