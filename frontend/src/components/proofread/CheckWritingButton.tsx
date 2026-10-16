import type { ProofreadState } from "../../hooks/useProofread";
import "../../styles/proofread.css";

/** "Check writing" pill for a caption toolbar. */
export default function CheckWritingButton({
  state,
  disabled,
  onCheck,
}: {
  state: ProofreadState;
  disabled?: boolean;
  onCheck: () => void;
}) {
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
