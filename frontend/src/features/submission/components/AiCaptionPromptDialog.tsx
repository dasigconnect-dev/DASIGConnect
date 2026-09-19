import { useEffect, useId, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { AI_CAPTION_PROMPT_MAX_LENGTH } from "../../../api/aiApi";
import type { CaptionTone } from "../../../api/aiApi";
import type { AiCaptionState } from "../../../hooks/useAiCaptionAssist";
import "./AiCaptionPromptDialog.css";

interface Props {
  open: boolean;
  state: AiCaptionState;
  hasImageAssets: boolean;
  existingCaption: string;
  isUnsaved?: boolean;
  onClose: () => void;
  onSubmit: (prompt: string, tone: CaptionTone) => Promise<any> | any;
  onApprove: (caption: string, tone: CaptionTone) => void;
}

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
  isUnsaved = false,
  onClose,
  onSubmit,
  onApprove,
}: Props) {
  const [selectedTone, setSelectedTone] = useState<CaptionTone | null>(null);
  const [prompt, setPrompt] = useState("");
  const [captionResult, setCaptionResult] = useState("");
  const [copied, setCopied] = useState(false);
  const titleId = useId();

  const isLoading = state === "loading";
  const promptLength = prompt.length;
  const isOverLimit = promptLength > AI_CAPTION_PROMPT_MAX_LENGTH;

  const contextLabel = useMemo(() => {
    if (hasImageAssets && existingCaption.trim().length > 0) {
      return "Using selected media and the current caption draft.";
    }
    if (hasImageAssets) {
      return "Using selected media to shape a new caption draft.";
    }
    if (existingCaption.trim().length > 0) {
      return "Using the current caption draft as reference.";
    }
    return "Generating a new caption draft from scratch.";
  }, [existingCaption, hasImageAssets]);

  useEffect(() => {
    if (!open) {
      setPrompt("");
      setCaptionResult("");
      setSelectedTone(null);
      setCopied(false);
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !isLoading) {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isLoading, onClose, open]);

  const handleGenerate = async (instructionPrompt: string) => {
    if (isOverLimit || isLoading) return;
    const toneToUse: CaptionTone = selectedTone || "professional";
    const res = await onSubmit(instructionPrompt.trim(), toneToUse);
    if (res && typeof res === "object" && "caption" in res && typeof res.caption === "string") {
      setCaptionResult(res.caption);
      setPrompt("");
    } else if (typeof res === "string") {
      setCaptionResult(res);
      setPrompt("");
    }
  };

  const handleCopyCaption = async () => {
    if (!captionResult) return;
    try {
      await navigator.clipboard.writeText(captionResult);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback if clipboard API unavailable
    }
  };

  const handleApprove = () => {
    if (!captionResult.trim() || isLoading) return;
    onApprove(captionResult.trim(), selectedTone || "professional");
  };

  if (!open) return null;

  return createPortal(
    <div
      className="submission-screen ai-prompt-overlay"
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
      >
        <div className="ai-prompt-head">
          <div>
            <span className="ai-prompt-kicker">AI Caption Assistant</span>
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

        {isUnsaved && !captionResult && (
          <div className="ai-prompt-save-notice" role="note">
            <i className="ti ti-info-circle" aria-hidden="true" />
            <div>
              <strong>Behind the scenes:</strong> Generating this caption will automatically save your draft and upload your event photos first so the vision AI model can inspect the image details and event context.
            </div>
          </div>
        )}

        {/* Tone Selector - Not auto-selected */}
        <div className="ai-prompt-tone-group" role="radiogroup" aria-label="Caption tone">
          {TONE_OPTIONS.map((option) => {
            const isSelected = selectedTone === option.tone;
            return (
              <button
                key={option.tone}
                type="button"
                className={`ai-prompt-tone-option${isSelected ? " active" : ""}`}
                role="radio"
                aria-checked={isSelected}
                onClick={() => setSelectedTone((curr) => (curr === option.tone ? null : option.tone))}
                disabled={isLoading}
              >
                <i className={`ti ${option.icon}`} aria-hidden />
                <span>
                  <strong>{option.label}</strong>
                  <small>{option.description}</small>
                </span>
              </button>
            );
          })}
        </div>

        {/* Auto-generated Caption Box - Not clickable or editable */}
        <div className="ai-caption-result-container">
          <div className="ai-caption-result-header">
            <span className="ai-caption-result-label">
              <i className="ti ti-sparkles" aria-hidden="true" />
              <span>Auto-generated Caption</span>
            </span>
            {captionResult && (
              <div className="ai-caption-result-meta-tags">
                {selectedTone && <span className="ai-caption-tone-pill">{selectedTone}</span>}
                <button
                  type="button"
                  className="ai-caption-copy-btn"
                  onClick={handleCopyCaption}
                  title="Copy caption to clipboard"
                >
                  <i className={copied ? "ti ti-check" : "ti ti-copy"} aria-hidden="true" />
                  {copied ? "Copied" : "Copy"}
                </button>
              </div>
            )}
          </div>

          <div className="ai-caption-result-box-wrapper">
            {isLoading && (
              <div className="ai-caption-generating-overlay">
                <span className="ai-caption-spinner-lg" aria-hidden="true" />
                <p>AI is generating your caption...</p>
              </div>
            )}
            <div className="ai-caption-result-display" role="region" aria-label="Generated caption preview">
              {captionResult ? (
                <div className="ai-caption-result-text">{captionResult}</div>
              ) : (
                <div className="ai-caption-result-placeholder">
                  Your auto-generated caption will appear here once you send a prompt below...
                </div>
              )}
            </div>
            <div className="ai-caption-result-footer">
              <span className="ai-caption-result-hint">
                {captionResult
                  ? "Prompt AI in the chat box below to refine, or click Approve Caption."
                  : "Optionally pick a tone above, then type instructions in the chat box below."}
              </span>
              <span className="ai-caption-result-count">
                {captionResult.length} / 3000
              </span>
            </div>
          </div>
        </div>

        {/* Oval-shaped prompt chat bar */}
        <div className="ai-prompt-chat-section">
          <form
            className="ai-prompt-chat-bar"
            onSubmit={(e) => {
              e.preventDefault();
              void handleGenerate(prompt);
            }}
          >
            <i className="ti ti-message-circle-sparkle ai-prompt-chat-icon" aria-hidden="true" />
            <input
              type="text"
              className="ai-prompt-chat-input"
              value={prompt}
              maxLength={AI_CAPTION_PROMPT_MAX_LENGTH}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder={
                captionResult
                  ? "Prompt again to refine (e.g. make it punchy, add tags, shorter)..."
                  : "Enter instructions (e.g. focus on youth participation) or leave blank..."
              }
              disabled={isLoading}
            />
            <button
              type="submit"
              className="ai-prompt-chat-send-btn"
              disabled={isLoading || isOverLimit}
              title={captionResult ? "Prompt again" : "Generate caption"}
            >
              {isLoading ? (
                <span className="ai-caption-spinner" aria-hidden="true" />
              ) : captionResult ? (
                <>
                  <i className="ti ti-refresh" aria-hidden="true" />
                  <span>Prompt Again</span>
                </>
              ) : (
                <>
                  <i className="ti ti-sparkles" aria-hidden="true" />
                  <span>Generate</span>
                </>
              )}
            </button>
          </form>
        </div>

        {/* Modal Actions */}
        <div className="ai-prompt-actions">
          <button
            type="button"
            className="ai-prompt-secondary"
            onClick={onClose}
            disabled={isLoading}
          >
            Cancel
          </button>

          {captionResult ? (
            <button
              type="button"
              className="ai-prompt-primary ai-prompt-approve-btn"
              onClick={handleApprove}
              disabled={isLoading || !captionResult.trim()}
            >
              <i className="ti ti-circle-check" aria-hidden="true" />
              Approve Caption
            </button>
          ) : (
            <button
              type="button"
              className="ai-prompt-primary"
              onClick={() => void handleGenerate(prompt)}
              disabled={isLoading || isOverLimit}
            >
              {isLoading ? (
                <>
                  <span className="ai-caption-spinner" aria-hidden="true" />
                  {isUnsaved ? "Saving & Generating..." : "Generating..."}
                </>
              ) : (
                <>
                  <i className={isUnsaved ? "ti ti-device-floppy" : "ti ti-sparkles"} aria-hidden="true" />
                  {isUnsaved ? "Save Draft & Generate Caption" : "Generate Caption"}
                </>
              )}
            </button>
          )}
        </div>
      </section>
    </div>,
    document.body,
  );
}
