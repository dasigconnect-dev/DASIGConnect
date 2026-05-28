import type { CaptionVariant, CaptionTone } from "../../../api/aiApi";
import { logCaptionInteraction } from "../../../api/aiApi";
import CaptionVariantCard from "./CaptionVariantCard";

interface Props {
  variants: CaptionVariant[];
  submissionId: string;
  onApplyCaption: (caption: string) => void;
  onDismissAll: () => void;
  onRegenerate: () => void;
}

export default function CaptionSuggestionPanel({
  variants,
  submissionId,
  onApplyCaption,
  onDismissAll,
  onRegenerate,
}: Props) {
  function handleApply(
    caption: string,
    action: "use" | "use_then_edited",
    tone: CaptionTone
  ) {
    logCaptionInteraction(submissionId, action, tone);
    onApplyCaption(caption);
  }

  function handleDismiss(tone: CaptionTone) {
    logCaptionInteraction(submissionId, "dismiss", tone);
  }

  function handleRegenerate() {
    logCaptionInteraction(submissionId, "re_generate");
    onRegenerate();
  }

  function handleDismissAll() {
    logCaptionInteraction(submissionId, "dismiss");
    onDismissAll();
  }

  return (
    <div className="caption-panel">
      <div className="caption-panel-header">
        <span className="caption-panel-disclaimer">
          <i className="ti ti-info-circle" /> AI-generated suggestions — review before applying.
        </span>
        <button
          className="caption-btn caption-btn-ghost caption-dismiss-all"
          onClick={handleDismissAll}
        >
          Dismiss All
        </button>
      </div>

      <div className="caption-cards-grid">
        {variants.map((v) => (
          <CaptionVariantCard
            key={v.tone}
            variant={v}
            submissionId={submissionId}
            onApply={handleApply}
            onRegenerate={handleRegenerate}
            onDismiss={() => handleDismiss(v.tone)}
          />
        ))}
      </div>
    </div>
  );
}
