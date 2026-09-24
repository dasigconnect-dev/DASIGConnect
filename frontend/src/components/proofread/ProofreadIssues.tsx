import type { ProofreadIssue } from "../../api/aiApi";
import "../../styles/proofread.css";

const KIND_LABELS: Record<ProofreadIssue["kind"], string> = {
  spelling: "Spelling",
  grammar: "Grammar",
  clarity: "Clarity",
  meaning: "Meaning changed",
};

/**
 * Proofreading findings with one-click fixes. Advisory only: each fix is
 * applied individually by the user, and a "meaning changed" finding has no
 * automatic fix — it's a prompt to double-check, not a correction.
 * Findings whose excerpt is no longer in the text (already fixed or edited
 * away) are hidden.
 */
export default function ProofreadIssues({
  issues,
  text,
  onApply,
  onDismiss,
}: {
  issues: ProofreadIssue[];
  text: string;
  onApply: (issue: ProofreadIssue) => void;
  onDismiss: (issue: ProofreadIssue) => void;
}) {
  const visible = issues.filter((issue) => text.includes(issue.excerpt));
  if (visible.length === 0) return null;
  return (
    <ul className="proof-list">
      {visible.map((issue) => (
        <li key={`${issue.kind}:${issue.excerpt}`} className={`proof-item is-${issue.kind}`}>
          <div className="proof-main">
            <span className="proof-kind">{KIND_LABELS[issue.kind]}</span>
            <p className="proof-change">
              <del>{issue.excerpt}</del>
              {issue.suggestion && (
                <>
                  <i className="ti ti-arrow-right" aria-hidden="true" />
                  <ins>{issue.suggestion}</ins>
                </>
              )}
            </p>
            {issue.explanation && <p className="proof-why">{issue.explanation}</p>}
          </div>
          <div className="proof-actions">
            {issue.suggestion && (
              <button type="button" className="is-apply" onClick={() => onApply(issue)}>
                Apply
              </button>
            )}
            <button type="button" onClick={() => onDismiss(issue)}>
              {issue.kind === "meaning" ? "It's fine" : "Ignore"}
            </button>
          </div>
        </li>
      ))}
    </ul>
  );
}
