import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import {
  attachAsset,
  detachAsset,
  getSubmission,
  reorderSubmissionMedia,
  uploadSubmissionMedia,
  type SubmissionSummary,
} from "../../../api/submissionApi";
import type { SubmissionMediaItem } from "../../../types/media";
import { isConflictError, pickerMediaKey, savedAssetToPickerItem } from "../utils";

interface MediaAutosaveSnapshot {
  summary: SubmissionSummary;
  items: SubmissionMediaItem[];
  uploadAssetIds: ReadonlyMap<string, string>;
}

interface SubmissionMediaAutosaveOptions {
  submissionId: string | null;
  enabled: boolean;
  items: SubmissionMediaItem[];
  removedAssetIds: string[];
  mediaCaptions: Record<string, string>;
  skipWatermarks: Record<string, boolean>;
  onReconciled: (snapshot: MediaAutosaveSnapshot) => void;
  onError: (error: unknown) => void;
}

function requestStatus(error: unknown) {
  return (error as { response?: { status?: number } }).response?.status;
}

function isCanceledRequest(error: unknown, signal: AbortSignal) {
  if (signal.aborted) return true;
  const value = error as { code?: string; name?: string };
  return value.code === "ERR_CANCELED" || value.name === "CanceledError" || value.name === "AbortError";
}

function itemFingerprint(item: SubmissionMediaItem) {
  const file = item.file;
  return [
    item.clientId,
    item.assetId ?? "",
    file ? `${file.name}:${file.size}:${file.lastModified}` : "",
  ].join("|");
}

/**
 * Serializes narrow media mutations for one saved submission. It deliberately
 * does not create or update drafts: first-save validation and non-media fields
 * remain owned by SubmissionScreen's established saveDraft path.
 */
export function useSubmissionMediaAutosave({
  submissionId,
  enabled,
  items,
  removedAssetIds,
  mediaCaptions,
  skipWatermarks,
  onReconciled,
  onError,
}: SubmissionMediaAutosaveOptions) {
  const [syncing, setSyncing] = useState(false);
  const [failedFingerprint, setFailedFingerprint] = useState<string | null>(null);
  const desiredRef = useRef(items);
  const removedRef = useRef(removedAssetIds);
  const captionsRef = useRef(mediaCaptions);
  const skipWatermarksRef = useRef(skipWatermarks);
  const onReconciledRef = useRef(onReconciled);
  const onErrorRef = useRef(onError);
  const generationRef = useRef(0);
  const processedGenerationRef = useRef(0);
  const runningRef = useRef(false);
  const mountedRef = useRef(true);
  const controllerRef = useRef<AbortController | null>(null);
  const uploadAssetIdsRef = useRef(new Map<string, string>());
  const activeSubmissionRef = useRef<string | null>(null);
  const enabledRef = useRef(enabled);

  const fingerprint = useMemo(
    () => [
      submissionId ?? "",
      ...items.map(itemFingerprint),
      `removed:${removedAssetIds.join(",")}`,
    ].join(";"),
    [items, removedAssetIds, submissionId],
  );
  const fingerprintRef = useRef(fingerprint);

  async function reconcileLatest(id: string, signal: AbortSignal) {
    let summary = (await getSubmission(id, signal)).data;
    let serverIds = new Set((summary.mediaAssets ?? []).map((asset) => asset.id));

    for (const item of desiredRef.current) {
      if (!item.assetId || serverIds.has(item.assetId)) continue;
      try {
        summary = (await attachAsset(id, item.assetId, signal)).data;
        serverIds = new Set((summary.mediaAssets ?? []).map((asset) => asset.id));
      } catch (error) {
        if (!isConflictError(error)) throw error;
        summary = (await getSubmission(id, signal)).data;
        serverIds = new Set((summary.mediaAssets ?? []).map((asset) => asset.id));
      }
    }

    for (const item of desiredRef.current) {
      if (!item.file || item.assetId || uploadAssetIdsRef.current.has(item.clientId)) continue;
      if (!desiredRef.current.some((current) => current.clientId === item.clientId)) continue;
      const beforeIds = new Set(serverIds);
      let uploadError: unknown;
      try {
        const response = await uploadSubmissionMedia(id, [item.file], signal);
        if (response) summary = response.data as SubmissionSummary;
      } catch (error) {
        if (isCanceledRequest(error, signal)) throw error;
        uploadError = error;
      }
      let resolved = (summary.mediaAssets ?? []).find(
        (asset) =>
          !beforeIds.has(asset.id) &&
          asset.fileName === item.file?.name &&
          asset.fileSizeBytes === item.file.size,
      );
      if (!resolved) {
        summary = (await getSubmission(id, signal)).data;
        resolved = (summary.mediaAssets ?? []).find(
          (asset) =>
            !beforeIds.has(asset.id) &&
            asset.fileName === item.file?.name &&
            asset.fileSizeBytes === item.file.size,
        );
      }
      if (!resolved && uploadError) throw uploadError;
      if (!resolved) throw new Error(`Uploaded media ${item.fileName} could not be reconciled.`);
      uploadAssetIdsRef.current.set(item.clientId, resolved.id);
      serverIds = new Set((summary.mediaAssets ?? []).map((asset) => asset.id));
    }

    const latestItems = desiredRef.current;
    const latestClientIds = new Set(latestItems.map((item) => item.clientId));
    const requestedDetachIds = new Set(removedRef.current);
    uploadAssetIdsRef.current.forEach((assetId, clientId) => {
      if (!latestClientIds.has(clientId)) requestedDetachIds.add(assetId);
    });
    for (const assetId of requestedDetachIds) {
      if (!serverIds.has(assetId)) continue;
      try {
        await detachAsset(id, assetId, signal);
      } catch (error) {
        if (requestStatus(error) !== 404) throw error;
      }
      serverIds.delete(assetId);
    }

    summary = (await getSubmission(id, signal)).data;
    serverIds = new Set((summary.mediaAssets ?? []).map((asset) => asset.id));
    const resolvedDesiredIds = latestItems
      .map((item) => item.assetId ?? uploadAssetIdsRef.current.get(item.clientId))
      .filter((assetId): assetId is string => Boolean(assetId));
    const allDesiredResolved = resolvedDesiredIds.length === latestItems.length;
    const exactServerSet = allDesiredResolved &&
      resolvedDesiredIds.length === serverIds.size &&
      resolvedDesiredIds.every((assetId) => serverIds.has(assetId));

    if (exactServerSet && resolvedDesiredIds.length > 0) {
      const captions: Record<string, string> = {};
      const flags: Record<string, boolean> = {};
      latestItems.forEach((item, index) => {
        const assetId = resolvedDesiredIds[index];
        const currentKey = pickerMediaKey(item);
        captions[assetId] = captionsRef.current[currentKey] ?? captionsRef.current[`saved:${assetId}`] ?? "";
        flags[assetId] = Boolean(
          skipWatermarksRef.current[currentKey] ?? skipWatermarksRef.current[`saved:${assetId}`],
        );
      });
      summary = (await reorderSubmissionMedia(id, resolvedDesiredIds, captions, flags, signal)).data;
    }

    const assetsById = new Map((summary.mediaAssets ?? []).map((asset) => [asset.id, asset]));
    const reconciledItems = latestItems.map((item) => {
      const assetId = item.assetId ?? uploadAssetIdsRef.current.get(item.clientId);
      const asset = assetId ? assetsById.get(assetId) : undefined;
      if (!asset) return item;
      return {
        ...savedAssetToPickerItem(asset),
        clientId: item.clientId,
        source: item.source,
      };
    });
    const representedIds = new Set(
      reconciledItems.map((item) => item.assetId).filter((assetId): assetId is string => Boolean(assetId)),
    );
    for (const asset of summary.mediaAssets ?? []) {
      if (!representedIds.has(asset.id)) reconciledItems.push(savedAssetToPickerItem(asset));
    }

    if (!signal.aborted && mountedRef.current && desiredRef.current === latestItems) {
      onReconciledRef.current({
        summary,
        items: reconciledItems,
        uploadAssetIds: new Map(uploadAssetIdsRef.current),
      });
    }
  }

  async function drainQueue(expectedSubmissionId: string) {
    if (runningRef.current) return;
    runningRef.current = true;
    if (mountedRef.current) setSyncing(true);

    try {
      while (
        mountedRef.current &&
        enabledRef.current &&
        activeSubmissionRef.current === expectedSubmissionId &&
        processedGenerationRef.current < generationRef.current
      ) {
        const generation = generationRef.current;
        const controller = new AbortController();
        controllerRef.current = controller;
        try {
          await reconcileLatest(expectedSubmissionId, controller.signal);
        } catch (error) {
          if (!isCanceledRequest(error, controller.signal)) {
            if (mountedRef.current) setFailedFingerprint(fingerprintRef.current);
            onErrorRef.current(error);
          }
        } finally {
          processedGenerationRef.current = generation;
          if (controllerRef.current === controller) controllerRef.current = null;
        }
      }
    } finally {
      runningRef.current = false;
      if (mountedRef.current) setSyncing(false);
      if (
        mountedRef.current &&
        enabledRef.current &&
        activeSubmissionRef.current === expectedSubmissionId &&
        processedGenerationRef.current < generationRef.current
      ) {
        void drainQueue(expectedSubmissionId);
      }
    }
  }

  useLayoutEffect(() => {
    desiredRef.current = items;
    removedRef.current = removedAssetIds;
    captionsRef.current = mediaCaptions;
    skipWatermarksRef.current = skipWatermarks;
    onReconciledRef.current = onReconciled;
    onErrorRef.current = onError;
    enabledRef.current = enabled;
    fingerprintRef.current = fingerprint;
  }, [enabled, fingerprint, items, mediaCaptions, onError, onReconciled, removedAssetIds, skipWatermarks]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (!enabled || !submissionId) return;
    if (activeSubmissionRef.current !== submissionId) {
      activeSubmissionRef.current = submissionId;
      uploadAssetIdsRef.current.clear();
    }

    generationRef.current += 1;
    if (!runningRef.current) void drainQueue(submissionId);
    // The fingerprint is intentionally the trigger. Callback and metadata
    // objects are read from refs so rerenders cannot enqueue duplicate work.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, fingerprint, submissionId]);

  function updateDesiredItems(nextItems: SubmissionMediaItem[]) {
    desiredRef.current = nextItems;
  }

  return {
    syncing,
    blocking: enabled && failedFingerprint !== fingerprint,
    updateDesiredItems,
  };
}
