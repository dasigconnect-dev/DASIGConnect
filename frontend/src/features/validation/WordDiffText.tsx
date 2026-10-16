import type { DiffSegment } from "./editSafeguards";

/**
 * A word-level diff as one line of text: removed words struck through, added
 * words highlighted, long unchanged stretches shown as "…". Used by Review &
 * Save and by Review History so an edit reads the same in both.
 */
export default function WordDiffText({ segments }: { segments: DiffSegment[] }) {
  return (
    <p className="val-change-diff">
      {segments.map((segment, index) =>
        segment.kind === "del" ? (
          <del key={index}>{segment.text}</del>
        ) : segment.kind === "ins" ? (
          <ins key={index}>{segment.text}</ins>
        ) : segment.kind === "gap" ? (
          <span key={index} className="val-change-gap" aria-label="unchanged text">
            {segment.text}
          </span>
        ) : (
          <span key={index}>{segment.text}</span>
        ),
      )}
    </p>
  );
}
