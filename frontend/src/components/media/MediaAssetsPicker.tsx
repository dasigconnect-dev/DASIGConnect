import { useState } from "react";
import type { SubmissionMediaItem } from "../../types/media";
import { useAiMediaSuggestions } from "../../hooks/useAiMediaSuggestions";
import SelectedMediaStrip from "./SelectedMediaStrip";
import UploadMediaTab from "./UploadMediaTab";
import MediaLibraryTab from "./MediaLibraryTab";
import AiSuggestedMediaTab from "./AiSuggestedMediaTab";

type PickerTab = "upload" | "library" | "ai";

interface MediaAssetsPickerProps {
  items: SubmissionMediaItem[];
  onItemsChange: (items: SubmissionMediaItem[]) => void;
  submissionId: string | null;
  eventTitle: string;
  caption: string;
  category: string;
  tags: string[];
  disabled?: boolean;
}

export default function MediaAssetsPicker({
  items,
  onItemsChange,
  submissionId,
  eventTitle,
  caption,
  category,
  tags,
  disabled,
}: MediaAssetsPickerProps) {
  const [activeTab, setActiveTab] = useState<PickerTab>("upload");

  const aiSuggestions = useAiMediaSuggestions(submissionId, eventTitle, caption, category, tags);

  const alreadyAddedIds = new Set(items.filter((i) => i.assetId).map((i) => i.assetId!));

  function deduplicatedAdd(incoming: SubmissionMediaItem[]): SubmissionMediaItem[] {
    const existingClientIds = new Set(items.map((i) => i.clientId));
    const existingAssetIds = new Set(items.filter((i) => i.assetId).map((i) => i.assetId!));
    return incoming.filter(
      (i) => !existingClientIds.has(i.clientId) && (i.assetId == null || !existingAssetIds.has(i.assetId))
    );
  }

  function handleAddItems(incoming: SubmissionMediaItem[]) {
    if (disabled) return;
    const novel = deduplicatedAdd(incoming);
    if (novel.length > 0) onItemsChange([...items, ...novel]);
  }

  function handleRemove(clientId: string) {
    if (disabled) return;
    onItemsChange(items.filter((i) => i.clientId !== clientId));
  }

  function handleReorder(fromIndex: number, toIndex: number) {
    if (disabled) return;
    const next = [...items];
    const [moved] = next.splice(fromIndex, 1);
    next.splice(toIndex, 0, moved);
    onItemsChange(next);
  }

  const tabs: { id: PickerTab; label: string; icon: string }[] = [
    { id: "upload", label: "Upload Files", icon: "ti-cloud-upload" },
    { id: "library", label: "My Library", icon: "ti-photo" },
    { id: "ai", label: "AI Suggestions", icon: "ti-sparkles" },
  ];

  return (
    <div className="mp-root">
      <SelectedMediaStrip
        items={items}
        disabled={disabled}
        onRemove={handleRemove}
        onReorder={handleReorder}
      />

      <div className="mp-tabs" role="tablist" aria-label="Media source">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            aria-controls={`mp-panel-${tab.id}`}
            className={`mp-tab${activeTab === tab.id ? " mp-tab--active" : ""}`}
            onClick={() => setActiveTab(tab.id)}
            disabled={disabled}
          >
            <i className={`ti ${tab.icon}`} aria-hidden />
            <span>{tab.label}</span>
            {tab.id === "ai" && aiSuggestions.state === "ready" && (
              <span className="mp-tab-badge" aria-label={`${aiSuggestions.results.length} suggestions`}>
                {aiSuggestions.results.length}
              </span>
            )}
          </button>
        ))}
      </div>

      <div className="mp-panel-wrap">
        <div
          id="mp-panel-upload"
          role="tabpanel"
          aria-labelledby="mp-tab-upload"
          hidden={activeTab !== "upload"}
        >
          {activeTab === "upload" && (
            <UploadMediaTab onFilesAdded={handleAddItems} disabled={disabled} />
          )}
        </div>

        <div
          id="mp-panel-library"
          role="tabpanel"
          aria-labelledby="mp-tab-library"
          hidden={activeTab !== "library"}
        >
          {activeTab === "library" && (
            <MediaLibraryTab
              alreadyAddedIds={alreadyAddedIds}
              onAddItems={handleAddItems}
              disabled={disabled}
            />
          )}
        </div>

        <div
          id="mp-panel-ai"
          role="tabpanel"
          aria-labelledby="mp-tab-ai"
          hidden={activeTab !== "ai"}
        >
          {activeTab === "ai" && (
            <AiSuggestedMediaTab
              suggestions={aiSuggestions}
              submissionId={submissionId}
              alreadyAddedIds={alreadyAddedIds}
              eventTitle={eventTitle}
              caption={caption}
              category={category}
              tags={tags}
              onAddItems={handleAddItems}
              disabled={disabled}
            />
          )}
        </div>
      </div>
    </div>
  );
}
