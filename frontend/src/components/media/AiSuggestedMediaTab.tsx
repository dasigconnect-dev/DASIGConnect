import { useEffect, useState } from "react";
import type { SubmissionMediaItem } from "../../types/media";
import type { UseAiMediaSuggestionsReturn } from "../../hooks/useAiMediaSuggestions";
import MediaAssetGrid, { type GridAsset } from "./MediaAssetGrid";

interface AiSuggestedMediaTabProps {
  suggestions: UseAiMediaSuggestionsReturn;
  /** True once at least one image asset is selected — enables image-based "more like these". */
  hasImageItems: boolean;
  alreadyAddedIds: Set<string>;
  eventTitle: string;
  caption: string;
  category: string;
  tags: string[];
  onAddItems: (items: SubmissionMediaItem[]) => void;
  disabled?: boolean;
}

export default function AiSuggestedMediaTab({
  suggestions,
  hasImageItems,
  alreadyAddedIds,
  eventTitle,
  caption,
  category,
  tags,
  onAddItems,
  disabled,
}: AiSuggestedMediaTabProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const { state, results, mode, fetchSimilar, fetchFromDetails, reset } = suggestions;

  useEffect(() => {
    setSelectedIds(new Set());
  }, [mode, results]);

  const hasContext =
    eventTitle.trim().length > 0 ||
    caption.trim().length > 0 ||
    category.trim().length > 0 ||
    tags.length > 0;

  const contextParts: string[] = [];
  if (eventTitle.trim()) contextParts.push(eventTitle.trim());
  if (caption.trim()) {
    const short = caption.trim().slice(0, 60);
    contextParts.push(short.length < caption.trim().length ? `${short}…` : short);
  }
  if (category.trim()) contextParts.push(category.trim());
  if (tags.length > 0) contextParts.push(tags.slice(0, 3).map((t) => `#${t}`).join(" "));

  const gridAssets: GridAsset[] = results.map((r) => ({
    id: r.id,
    storageUrl: r.storageUrl,
    fileName: r.fileName,
    fileType: r.fileType,
    aiCategory: r.aiCategory ?? null,
    similarityScore: r.similarityScore,
    matchReasons: r.matchReasons ?? [],
  }));

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  const pendingSelected = [...selectedIds].filter((id) => !alreadyAddedIds.has(id));
  const selectableIds = results.filter((result) => !alreadyAddedIds.has(result.id)).map((result) => result.id);
  const allSelected = selectableIds.length > 0 && selectableIds.every((id) => selectedIds.has(id));

  function handleAdd() {
    if (disabled || pendingSelected.length === 0) return;
    const toAdd = results.filter((r) => pendingSelected.includes(r.id));
    const items: SubmissionMediaItem[] = toAdd.map((r) => ({
      clientId: `ai-${r.id}`,
      source: "ai" as const,
      assetId: r.id,
      previewUrl: r.storageUrl,
      mediaType: ["mp4", "mov", "webm"].includes(r.fileType.toLowerCase()) ? "video" : "image",
      fileName: r.fileName,
      aiCategory: r.aiCategory ?? undefined,
      similarityScore: r.similarityScore,
    }));
    onAddItems(items);
    setSelectedIds(new Set());
  }

  const regenerate = () => (mode === "details" ? fetchFromDetails() : fetchSimilar());
  const chooseSourceAgain = () => reset();
  const isBusy = state === "loading";

  // The two ways AI can find media. Image-based works from the photos already added (no text);
  // text-based works from the post details. Both auto-save the draft via ensureDraft.
  const actionPicker = (
    <div className="ast-modes">
      <button
        type="button"
        className="ast-mode-btn ast-mode-btn--primary"
        onClick={fetchSimilar}
        disabled={disabled || isBusy || !hasImageItems}
        title={hasImageItems ? undefined : "Add a photo first to find more like it."}
      >
        <i className="ti ti-photo-search" aria-hidden />
        <span className="ast-mode-title">More like your selection</span>
        <span className="ast-mode-sub">{hasImageItems ? "From the photos you added" : "Add a photo to enable"}</span>
      </button>
      <button
        type="button"
        className="ast-mode-btn"
        onClick={fetchFromDetails}
        disabled={disabled || isBusy || !hasContext}
        title={hasContext ? undefined : "Add a title, caption, or tags to enable."}
      >
        <i className="ti ti-sparkles" aria-hidden />
        <span className="ast-mode-title">From your post details</span>
        <span className="ast-mode-sub">
          {hasContext ? (contextParts.join(" · ").slice(0, 48) || "Title, caption & tags") : "Add post details to enable"}
        </span>
      </button>
    </div>
  );

  return (
    <div className="ast-root">
      {!hasImageItems && !hasContext && state === "idle" ? (
        <div className="ast-no-context" role="status">
          <i className="ti ti-bulb" aria-hidden />
          <span>Add a photo above, or fill in your post details — then AI can suggest related media from your library.</span>
        </div>
      ) : (
        state === "idle" && (
          <div className="ast-idle">
            <p className="ast-idle-hint">Let AI surface related assets from your library. Pick a source:</p>
            {actionPicker}
          </div>
        )
      )}

      {state === "loading" && (
        <div className="ast-loading" role="status" aria-live="polite">
          <span className="ast-spinner" aria-hidden />
          <span>{mode === "details" ? "Matching your post details…" : "Finding photos like your selection…"}</span>
        </div>
      )}

      {state === "error" && (
        <div className="ast-error" role="alert">
          <i className="ti ti-alert-circle" aria-hidden />
          <span>Could not generate suggestions. Choose a source and try again.</span>
          <button type="button" className="ast-retry-btn" onClick={chooseSourceAgain} disabled={disabled}>
            Retry
          </button>
        </div>
      )}

      {state === "empty" && (
        <div className="ast-empty" role="status">
          <i className="ti ti-photo-off" aria-hidden />
          <span>
            {mode === "details"
              ? "No closely matching assets found. Try refining your caption or title, or search by your selection."
              : "No similar assets found in your library yet."}
          </span>
          <button type="button" className="ast-retry-btn" onClick={chooseSourceAgain} disabled={disabled}>
            Try again
          </button>
        </div>
      )}

      {state === "ready" && (
        <>
          <div className="ast-results-header">
            <span className="ast-results-label">
              {mode === "details" ? "Ranked by relevance to your post" : "Similar to your selected photos"} — {results.length} match{results.length !== 1 ? "es" : ""}
            </span>
            <button
              type="button"
              className="ast-regenerate-btn"
              onClick={regenerate}
              disabled={disabled}
              title="Regenerate suggestions"
            >
              <i className="ti ti-refresh" aria-hidden />
              Regenerate
            </button>
          </div>

          <MediaAssetGrid
            assets={gridAssets}
            selectedIds={selectedIds}
            alreadyAddedIds={alreadyAddedIds}
            onToggle={toggleSelect}
          />
        </>
      )}

      {pendingSelected.length > 0 && (
        <div className="ast-action-bar" role="status" aria-live="polite">
          <span className="ast-action-count">{pendingSelected.length} selected</span>
          {!allSelected && (
            <button
              type="button"
              className="ast-action-clear"
              disabled={disabled}
              onClick={() => setSelectedIds(new Set(selectableIds))}
            >
              Select all ({selectableIds.length})
            </button>
          )}
          <button
            type="button"
            className="ast-action-clear"
            onClick={() => setSelectedIds(new Set())}
          >
            Clear
          </button>
          <button
            type="button"
            className="ast-action-add"
            onClick={handleAdd}
            disabled={disabled}
          >
            <i className="ti ti-plus" aria-hidden />
            Add Selected ({pendingSelected.length})
          </button>
        </div>
      )}
    </div>
  );
}
