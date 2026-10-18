import type { ProofreadState } from "../../hooks/useProofread";
import "../../styles/proofread.css";

interface CheckWritingButtonProps {
  state: ProofreadState;
  disabled?: boolean;
  isUnavailable?: boolean;
  onCheck: () => void;
}

/** "Check writing" pill for a caption toolbar. */
export default function CheckWritingButton({
  state,
  disabled,
  isUnavailable: isUnavailableProp = false,
  onCheck,
}: CheckWritingButtonProps) {
  const isUnavailable = isUnavailableProp || state === "error";
  if (isUnavailable) {
    // Only one unified "AI service unavailable" pill is rendered across the caption toolbar
    return null;
  }

  const loading = state === "loading";
  return (
    <button
      type="button"
      className="proof-check-btn"
      onClick={onCheck}
      disabled={loading || disabled}
      title="Check spelling and grammar — suggestions only"
    >
      <i className={`ti ${loading ? "ti-loader-2 proof-spin" : "ti-text-spellcheck"}`} aria-hidden="true" />
      <span>{loading ? "Checking…" : "Check writing"}</span>
    </button>
  );
}
