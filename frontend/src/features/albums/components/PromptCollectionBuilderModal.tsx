import type {
  PromptCollectionSuggestionResponse,
  PromptCollectionSuggestionCandidate,
} from "../../../api/albumApi";
import { isImage } from "../mediaType";

interface PromptCollectionBuilderModalProps {
  prompt: string;
  name: string;
  description: string;
  loading: boolean;
  creating: boolean;
  result: PromptCollectionSuggestionResponse | null;
  selected: Set<string>;
  onPromptChange: (value: string) => void;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onFind: () => void;
  onToggle: (assetId: string) => void;
  onSelectAll: () => void;
  onClearSelection: () => void;
  onClose: () => void;
  onCreate: () => void;
}

export default function PromptCollectionBuilderModal({
  prompt,
  name,
  description,
  loading,
  creating,
  result,
  selected,
  onPromptChange,
  onNameChange,
  onDescriptionChange,
  onFind,
  onToggle,
  onSelectAll,
  onClearSelection,
  onClose,
  onCreate,
}: PromptCollectionBuilderModalProps) {
  const hasResults = !!result && result.candidates.length > 0;

  return (
    <div className="alb-modal-overlay" role="dialog" aria-modal="true" aria-label="AI collection builder" onClick={onClose}>
      <div className="alb-modal alb-modal-wide alb-ai-modal" onClick={(event) => event.stopPropagation()}>
        <div className="alb-ai-head">
          <div>
            <h2 className="alb-modal-title">AI Collection Builder</h2>
            <p className="alb-ai-kicker">Describe the media set, then review the matches.</p>
          </div>
          {result && <span className="alb-ai-count">{result.totalCandidates} matched</span>}
        </div>

        <label className="alb-field">
          <span>Prompt</span>
          <textarea
            rows={3}
            value={prompt}
            disabled={loading || creating}
            onChange={(event) => onPromptChange(event.target.value)}
            placeholder="Robotics seminar photos with students, speakers, and awarding moments"
          />
        </label>

        <div className="alb-ai-search-row">
          <button
            className="med-btn med-btn-primary med-btn-sm"
            type="button"
            disabled={loading || creating || !prompt.trim()}
            onClick={onFind}
          >
            {loading ? "Finding..." : "Find Matches"}
          </button>
        </div>

        {result && (
          <>
            <div className="alb-ai-form-grid">
              <label className="alb-field">
                <span>Collection name</span>
                <input
                  value={name}
                  disabled={creating}
                  onChange={(event) => onNameChange(event.target.value)}
                />
              </label>
              <label className="alb-field">
                <span>Description <em>optional</em></span>
                <input
                  value={description}
                  disabled={creating}
                  onChange={(event) => onDescriptionChange(event.target.value)}
                />
              </label>
            </div>

            {hasResults ? (
              <>
                {selected.size > 0 && (
                  <div className="alb-picker-toolbar">
                    <span className="alb-picker-count">
                      {selected.size} of {result.candidates.length} selected
                    </span>
                    <div className="alb-picker-toolbar-actions">
                      {selected.size < result.candidates.length && (
                        <button className="alb-text-btn" type="button" disabled={creating} onClick={onSelectAll}>
                          Select all
                        </button>
                      )}
                      <button className="alb-text-btn" type="button" disabled={creating} onClick={onClearSelection}>
                        Clear
                      </button>
                    </div>
                  </div>
                )}
                <div className="alb-ai-results" aria-label="Suggested media assets">
                  {result.candidates.map((candidate) => (
                    <CandidateItem
                      key={candidate.asset.id}
                      candidate={candidate}
                      checked={selected.has(candidate.asset.id)}
                      disabled={creating}
                      onToggle={onToggle}
                    />
                  ))}
                </div>
              </>
            ) : (
              <div className="alb-picker-empty">No matching media found.</div>
            )}
          </>
        )}

        <div className="alb-modal-actions">
          <button className="med-btn med-btn-ghost med-btn-sm" type="button" disabled={creating} onClick={onClose}>
            Cancel
          </button>
          <button
            className="med-btn med-btn-primary med-btn-sm"
            type="button"
            disabled={creating || !hasResults || selected.size === 0 || !name.trim()}
            onClick={onCreate}
          >
            {creating ? "Creating..." : `Create with ${selected.size}`}
          </button>
        </div>
      </div>
    </div>
  );
}

function CandidateItem({
  candidate,
  checked,
  disabled,
  onToggle,
}: {
  candidate: PromptCollectionSuggestionCandidate;
  checked: boolean;
  disabled: boolean;
  onToggle: (assetId: string) => void;
}) {
  const asset = candidate.asset;
  const label = asset.title || asset.fileName;

  return (
    <label className={`alb-ai-item${checked ? " selected" : ""}`}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={() => onToggle(asset.id)}
      />
      <span className="alb-ai-thumb">
        {isImage(asset.fileType) ? (
          <img src={asset.storageUrl} alt={label} loading="lazy" />
        ) : (
          <span className="alb-asset-file">{asset.fileType.toUpperCase()}</span>
        )}
      </span>
      <span className="alb-ai-meta">
        <span className="alb-ai-name">{label}</span>
        <span className={`alb-ai-confidence ${candidate.confidence}`}>{candidate.confidence}</span>
        {candidate.matchReasons.length > 0 && (
          <span className="alb-ai-reasons">{candidate.matchReasons.join(", ")}</span>
        )}
      </span>
    </label>
  );
}
