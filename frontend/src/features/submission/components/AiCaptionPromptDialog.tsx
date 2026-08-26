import { useEffect, useId, useMemo, useState } from "react";
import { AI_CAPTION_PROMPT_MAX_LENGTH } from "../../../api/aiApi";
import type { CaptionTone } from "../../../api/aiApi";
import type { AiCaptionState } from "../../../hooks/useAiCaptionAssist";

interface Props {
  open: boolean;
  state: AiCaptionState;
  hasImageAssets: boolean;
  existingCaption: string;
  onClose: () => void;
  onSubmit: (prompt: string, tone: CaptionTone) => void;
}

const DEFAULT_PROMPT_PLACEHOLDER =
  "Example: Make it warm and concise, focus on student participation, include DOST Region 7.";

const TONE_OPTIONS: Array<{
  tone: CaptionTone;
  label: string;
  description: string;
  icon: string;
}> = [
  {
    tone: "professional",
    label: "Professional",
    description: "Official, clear, and polished.",
    icon: "ti-briefcase",
  },
  {
    tone: "community",
    label: "Community",
    description: "Warm, inclusive, and student-facing.",
    icon: "ti-users",
  },
  {
    tone: "energetic",
    label: "Energetic",
    description: "Action-driven and promotional.",
    icon: "ti-bolt",
  },
];

export default function AiCaptionPromptDialog({
  open,
  state,
  hasImageAssets,
  existingCaption,
  onClose,
  onSubmit,
}: Props) {
  const [prompt, setPrompt] = useState("");
  const [selectedTone, setSelectedTone] = useState<CaptionTone>("professional");
  const titleId = useId();
  const promptId = useId();
  const errorId = useId();
  const isLoading = state === "loading";
  const isOverLimit = prompt.length > AI_CAPTION_PROMPT_MAX_LENGTH;
  const hasContext =
    hasImageAssets || existingCaption.trim().length > 0 || prompt.trim().length > 0;
  const contextLabel = useMemo(() => {
    if (hasImageAssets && existingCaption.trim()) {
      return "Using selected media and the current caption draft.";
    }
    if (hasImageAssets) return "Using selected media for visual context.";
    if (existingCaption.trim()) return "Using the current caption draft as text context.";
    return "Add optional instructions, or generate a general DASIG caption.";
  }, [existingCaption, hasImageAssets]);

  useEffect(() => {
    if (!open) {
      setPrompt("");
      setSelectedTone("professional");
      return;
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape" && !isLoading) onClose();
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isLoading, onClose, open]);

  if (!open) return null;

  return (
    <div
      className="ai-prompt-overlay"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isLoading) onClose();
      }}
    >
      <section
        className="ai-prompt-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={isOverLimit ? errorId : undefined}
      >
        <div className="ai-prompt-head">
          <div>
            <span className="ai-prompt-kicker">AI Caption</span>
            <h2 id={titleId}>Suggest Caption</h2>
          </div>
          <button
            type="button"
            className="ai-prompt-icon-btn"
            onClick={onClose}
            disabled={isLoading}
            title="Close prompt"
          >
            <i className="ti ti-x" aria-hidden />
          </button>
        </div>

        <p className="ai-prompt-context">{contextLabel}</p>

        <div className="ai-prompt-tone-group" role="radiogroup" aria-label="Caption variant">
          {TONE_OPTIONS.map((option) => (
            <button
              key={option.tone}
              type="button"
              className={`ai-prompt-tone-option${selectedTone === option.tone ? " active" : ""}`}
              role="radio"
              aria-checked={selectedTone === option.tone}
              onClick={() => setSelectedTone(option.tone)}
              disabled={isLoading}
            >
              <i className={`ti ${option.icon}`} aria-hidden />
              <span>
                <strong>{option.label}</strong>
                <small>{option.description}</small>
              </span>
            </button>
          ))}
        </div>

        <label className="ai-prompt-label" htmlFor={promptId}>
          Prompt instructions <span>optional</span>
        </label>
        <textarea
          id={promptId}
          className={`ai-prompt-input${isOverLimit ? " ai-prompt-input--error" : ""}`}
          value={prompt}
          maxLength={AI_CAPTION_PROMPT_MAX_LENGTH + 40}
          onChange={(event) => setPrompt(event.target.value)}
          placeholder={DEFAULT_PROMPT_PLACEHOLDER}
          rows={4}
          autoFocus
        />
        <div className="ai-prompt-meta">
          <span id={errorId} className={isOverLimit ? "ai-prompt-error" : ""}>
            {isOverLimit
              ? `Keep instructions within ${AI_CAPTION_PROMPT_MAX_LENGTH} characters.`
              : hasContext
                ? "Leave blank for a default caption in the selected variant."
                : "No media is required; text-only suggestions are supported."}
          </span>
          <span
            className={isOverLimit ? "ai-prompt-count ai-prompt-count--error" : "ai-prompt-count"}
          >
            {prompt.length} / {AI_CAPTION_PROMPT_MAX_LENGTH}
          </span>
        </div>

        <div className="ai-prompt-actions">
          <button
            type="button"
            className="ai-prompt-secondary"
            onClick={onClose}
            disabled={isLoading}
          >
            Cancel
          </button>
          <button
            type="button"
            className="ai-prompt-primary"
            onClick={() => {
              if (isOverLimit || isLoading) return;
              onSubmit(prompt.trim(), selectedTone);
            }}
            disabled={isOverLimit || isLoading}
          >
            {isLoading ? (
              <>
                <span className="ai-caption-spinner" aria-hidden />
                Generating...
              </>
            ) : (
              <>
                <i className="ti ti-sparkles" aria-hidden />
                Generate
              </>
            )}
          </button>
        </div>
      </section>
    </div>
  );
}
