import { useState } from "react";
import type { SubmissionMediaItem } from "../../types/media";
import type { UseAiMediaSuggestionsReturn } from "../../hooks/useAiMediaSuggestions";
import MediaAssetGrid, { type GridAsset } from "./MediaAssetGrid";

interface AiSuggestedMediaTabProps {
  suggestions: UseAiMediaSuggestionsReturn;
  submissionId: string | null;
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
  submissionId,
  alreadyAddedIds,
  eventTitle,
  caption,
  category,
  tags,
  onAddItems,
  disabled,
}: AiSuggestedMediaTabProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const { state, results, fetch } = suggestions;

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

  return (
    <div className="ast-root">
      {hasContext && contextParts.length > 0 && (
        <div className="ast-context-pill" aria-label="Search context">
          <i className="ti ti-sparkles ast-context-icon" aria-hidden />
          <span className="ast-context-text">{contextParts.join(" · ")}</span>
        </div>
      )}

      {!hasContext && (
        <div className="ast-no-context" role="status">
          <i className="ti ti-info-circle" aria-hidden />
          <span>Add an event title, caption, or tags first so AI can find relevant media.</span>
        </div>
      )}

      {hasContext && !submissionId && (
        <div className="ast-idle" role="status">
          <i className="ti ti-device-floppy" style={{ fontSize: 28, color: "var(--mp-muted)" }} aria-hidden />
          <p className="ast-idle-hint">Save your draft first to enable AI media suggestions.</p>
        </div>
      )}

      {hasContext && submissionId && state === "idle" && (
        <div className="ast-idle">
          <p className="ast-idle-hint">
            AI will scan your media library and surface assets most relevant to your post context.
          </p>
          <button
            type="button"
            className="ast-generate-btn"
            onClick={fetch}
            disabled={disabled}
          >
            <i className="ti ti-sparkles" aria-hidden />
            Generate Suggestions
          </button>
        </div>
      )}

      {state === "loading" && (
        <div className="ast-loading" role="status" aria-live="polite">
          <span className="ast-spinner" aria-hidden />
          <span>Scanning your media library…</span>
        </div>
      )}

      {state === "error" && (
        <div className="ast-error" role="alert">
          <i className="ti ti-alert-circle" aria-hidden />
          <span>Could not generate suggestions. Try again.</span>
          <button type="button" className="ast-retry-btn" onClick={fetch} disabled={disabled}>
            Retry
          </button>
        </div>
      )}

      {state === "empty" && (
        <div className="ast-empty" role="status">
          <i className="ti ti-photo-off" aria-hidden />
          <span>No closely matching assets found in your library. Try refining your caption or title.</span>
          <button type="button" className="ast-retry-btn" onClick={fetch} disabled={disabled}>
            Try again
          </button>
        </div>
      )}

      {state === "ready" && (
        <>
          <div className="ast-results-header">
            <span className="ast-results-label">
              Top {results.length} match{results.length !== 1 ? "es" : ""} — ranked by relevance
            </span>
            <button
              type="button"
              className="ast-regenerate-btn"
              onClick={fetch}
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
