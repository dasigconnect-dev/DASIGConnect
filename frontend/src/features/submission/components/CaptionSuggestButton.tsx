import { useRef, useState } from "react";
import {
  suggestCaption,
  isRateLimitError,
  type CaptionVariant,
} from "../../../api/aiApi";
import CaptionSuggestionPanel from "./CaptionSuggestionPanel";

interface Props {
  submissionId: string | null;
  hasImageAssets: boolean;
  onCaptionApplied: (caption: string) => void;
}

type ButtonState =
  | "idle"
  | "loading"
  | "rate-limited"
  | "error-timeout"
  | "error-unavailable";

export default function CaptionSuggestButton({
  submissionId,
  hasImageAssets,
  onCaptionApplied,
}: Props) {
  const [buttonState, setButtonState] = useState<ButtonState>("idle");
  const [variants, setVariants] = useState<CaptionVariant[] | null>(null);
  const [rateLimitReset, setRateLimitReset] = useState<number | null>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const cooldownRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  if (!hasImageAssets || !submissionId) return null;

  function formatResetTime(epochSeconds: number): string {
    const date = new Date(epochSeconds * 1000);
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  async function handleClick() {
    if (buttonState !== "idle") return;

    // 3-second debounce
    if (debounceRef.current) clearTimeout(debounceRef.current);
    setButtonState("loading");

    debounceRef.current = setTimeout(async () => {
      try {
        const response = await suggestCaption(submissionId!);
        setVariants(response.variants);
        setButtonState("idle");
      } catch (err) {
        if (isRateLimitError(err)) {
          setRateLimitReset(err.rateLimitReset ?? null);
          setButtonState("rate-limited");
          return;
        }
        const msg = err instanceof Error ? err.message : "";
        setButtonState(msg === "timeout" ? "error-timeout" : "error-unavailable");
        cooldownRef.current = setTimeout(() => setButtonState("idle"), 5000);
      }
    }, 3000);
  }

  function handleDismissAll() {
    setVariants(null);
    setButtonState("idle");
  }

  function handleRegenerate() {
    setVariants(null);
    handleClick();
  }

  function handleCaptionApplied(caption: string) {
    setVariants(null);
    onCaptionApplied(caption);
  }

  if (variants) {
    return (
      <CaptionSuggestionPanel
        variants={variants}
        submissionId={submissionId}
        onApplyCaption={handleCaptionApplied}
        onDismissAll={handleDismissAll}
        onRegenerate={handleRegenerate}
      />
    );
  }

  return (
    <div className="sub-ai-assist">
      <div className="sub-ai-icon">
        <i className="ti ti-sparkles" />
      </div>
      <div className="sub-ai-content">
        <div className="sub-ai-title">AI caption assist</div>

        {buttonState === "idle" && (
          <button
            className="caption-suggest-btn"
            onClick={handleClick}
          >
            <i className="ti ti-sparkles" /> Suggest Caption
          </button>
        )}

        {buttonState === "loading" && (
          <div className="caption-loading">
            <span className="caption-spinner" />
            Generating suggestions...
          </div>
        )}

        {buttonState === "rate-limited" && (
          <div className="caption-error">
            <i className="ti ti-clock" />
            Caption suggestion limit reached
            {rateLimitReset != null && (
              <> — available again at {formatResetTime(rateLimitReset)}</>
            )}.
          </div>
        )}

        {buttonState === "error-timeout" && (
          <div className="caption-error">
            <i className="ti ti-alert-circle" />
            Caption suggestion timed out — please try again.
          </div>
        )}

        {buttonState === "error-unavailable" && (
          <div className="caption-error">
            <i className="ti ti-alert-circle" />
            Caption suggestion temporarily unavailable — write caption manually.
          </div>
        )}
      </div>
    </div>
  );
}
