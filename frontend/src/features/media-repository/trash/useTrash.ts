import { useCallback, useEffect, useState } from "react";
import { getTrash, purgeMediaAsset, restoreMediaAsset, type TrashItem } from "../../../api/mediaApi";
import { useToast } from "../../../context/ToastContext";

interface ApiError {
  name?: string;
  message?: string;
  response?: { data?: { error?: string; message?: string } };
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isApiError(error)) return fallback;
  return error.response?.data?.error || error.response?.data?.message || error.message || fallback;
}

function isCanceledError(error: unknown) {
  return isApiError(error) && error.name === "CanceledError";
}

/**
 * UC-2.2 Trash: loads soft-deleted (not yet purged) media for the current scope and
 * owns the restore / delete (purge) actions — single and bulk — so rows stay presentational.
 */
export function useTrash(institutionId: string | null, enabled = true) {
  const toast = useToast();
  const [items, setItems] = useState<TrashItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      if (!enabled) {
        setItems([]);
        setLoading(false);
        setError("");
        return Promise.resolve();
      }
      setLoading(true);
      setError("");
      return getTrash(institutionId, signal)
        .then((data) => setItems(Array.isArray(data) ? data : []))
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load the trash."));
        })
        .finally(() => setLoading(false));
    },
    [institutionId, enabled],
  );

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    queueMicrotask(() => {
      if (active) void refresh(controller.signal);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [refresh]);

  const setBusy = useCallback((ids: string[], busy: boolean) => {
    setBusyIds((prev) => {
      const next = new Set(prev);
      ids.forEach((id) => (busy ? next.add(id) : next.delete(id)));
      return next;
    });
  }, []);

  const removeItems = useCallback((ids: string[]) => {
    const drop = new Set(ids);
    setItems((prev) => prev.filter((i) => !drop.has(i.asset.id)));
  }, []);

  const restore = useCallback(
    async (assetId: string) => {
      setBusy([assetId], true);
      try {
        await restoreMediaAsset(assetId);
        toast.success("Asset restored to the library.");
        removeItems([assetId]);
        return true;
      } catch {
        toast.error("Could not restore this asset.");
        setBusy([assetId], false);
        return false;
      }
    },
    [toast, setBusy, removeItems],
  );

  const purge = useCallback(
    async (assetId: string) => {
      setBusy([assetId], true);
      try {
        await purgeMediaAsset(assetId);
        toast.success("Asset permanently deleted.");
        removeItems([assetId]);
        return true;
      } catch {
        toast.error("Could not delete this asset.");
        setBusy([assetId], false);
        return false;
      }
    },
    [toast, setBusy, removeItems],
  );

  /**
   * Permanently delete several trashed assets. Purge touches Supabase Storage, so we run
   * sequentially (HikariCP is capped at 5) and report an aggregate result. Successes are
   * removed from the list; failures stay so the user can retry.
   */
  const purgeMany = useCallback(
    async (assetIds: string[]) => {
      const ids = Array.from(new Set(assetIds));
      if (ids.length === 0) return { succeeded: [] as string[], failed: [] as string[] };
      setBusy(ids, true);
      const succeeded: string[] = [];
      const failed: string[] = [];
      for (const id of ids) {
        try {
          await purgeMediaAsset(id);
          succeeded.push(id);
        } catch {
          failed.push(id);
        }
      }
      if (succeeded.length > 0) removeItems(succeeded);
      if (failed.length > 0) setBusy(failed, false);

      if (failed.length === 0) {
        toast.success(`${succeeded.length} item${succeeded.length === 1 ? "" : "s"} permanently deleted.`);
      } else if (succeeded.length === 0) {
        toast.error("Could not delete the selected items.");
      } else {
        toast.error(`Deleted ${succeeded.length}; ${failed.length} could not be deleted.`);
      }
      return { succeeded, failed };
    },
    [toast, setBusy, removeItems],
  );

  return { items, loading, error, refresh, busyIds, restore, purge, purgeMany };
}
