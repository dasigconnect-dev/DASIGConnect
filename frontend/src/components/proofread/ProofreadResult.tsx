import type { ProofreadIssue } from "../../api/aiApi";
import type { ProofreadState } from "../../hooks/useProofread";
import ProofreadIssues from "./ProofreadIssues";
import "../../styles/proofread.css";

/** What a finished "Check writing" found, below the caption. */
export default function ProofreadResult({
  state,
  issues,
  text,
  onApply,
  onDismiss,
}: {
  state: ProofreadState;
  issues: ProofreadIssue[];
  text: string;
  onApply: (issue: ProofreadIssue) => void;
  onDismiss: (issue: ProofreadIssue) => void;
}) {
  if (state !== "done") return null;
  const open = issues.some((issue) => text.includes(issue.excerpt));
  return (
    <div className="proof-result" aria-live="polite">
      {open ? (
        <ProofreadIssues issues={issues} text={text} onApply={onApply} onDismiss={onDismiss} />
      ) : (
        <p className="proof-status is-ok">
          <i className="ti ti-circle-check" aria-hidden="true" /> No spelling or grammar issues found.
        </p>
      )}
      <p className="proof-note">
        AI suggestions can be wrong about names and local words — nothing changes unless you apply it.
      </p>
    </div>
  );
}
