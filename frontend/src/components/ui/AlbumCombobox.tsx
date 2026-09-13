import { useState, useRef, useEffect, useMemo } from "react";

export interface AlbumMatchSuggestion {
  albumId: string;
  albumName: string;
  score: number;
  reasons: string[];
}

interface AlbumComboboxProps {
  value: string;
  existingAlbums?: string[];
  /** Optional short label (e.g. institution code) shown as a badge next to each album row. */
  albumBadges?: Record<string, string>;
  readOnly?: boolean;
  placeholder?: string;
  autoMatchLabel?: string;
  /** Shown under the "Create new album" row, e.g. "in CIT-U · top level". */
  createHint?: string;
  onChange: (value: string) => void;
  onAutoMatch: () => void;
  /** Auto-Match request in flight — shows a spinner on the Auto-Match row. */
  matching?: boolean;
  /**
   * Confident Auto-Match already applied to `value` — shown as a small badge
   * under the field with why it matched. Cleared by the caller on the next
   * manual edit.
   */
  matchedBadge?: { albumName: string; reasons: string[] } | null;
  /** Ambiguous Auto-Match: ranked candidates shown above the regular album list. */
  suggestions?: AlbumMatchSuggestion[];
  /** Auto-Match ran but nothing cleared the confidence floor. */
  noMatchNotice?: boolean;
}

export default function AlbumCombobox({
  value,
  existingAlbums = [],
  albumBadges,
  readOnly,
  placeholder,
  autoMatchLabel = "Auto-Match from Event Title",
  createHint,
  onChange,
  onAutoMatch,
  matching = false,
  matchedBadge = null,
  suggestions = [],
  noMatchNotice = false,
}: AlbumComboboxProps) {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("pointerdown", handlePointerDown);
    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [open]);

  const filtered = useMemo(() => {
    if (!value) return existingAlbums;
    const lower = value.toLowerCase();
    return existingAlbums.filter((a) => a.toLowerCase().includes(lower));
  }, [value, existingAlbums]);

  const exactMatch = existingAlbums.some(
    (a) => a.toLowerCase() === value.trim().toLowerCase()
  );

  const showCreate = value.trim().length > 0 && !exactMatch;

  return (
    <div ref={containerRef} style={{ position: "relative", width: "100%" }}>
      <div style={{ position: "relative" }}>
        <input
          type="text"
          className="sub-finput"
          value={value}
          readOnly={readOnly}
          placeholder={placeholder}
          onChange={(e) => {
            onChange(e.target.value);
            if (!open) setOpen(true);
          }}
          onFocus={() => {
            if (!readOnly) setOpen(true);
          }}
          aria-expanded={open}
          role="combobox"
        />
        <i
          className="ti ti-chevron-down"
          style={{ position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)", color: "#6b7280", pointerEvents: "none" }}
        />
      </div>

      {matchedBadge && (
        <div
          style={{
            display: "flex", alignItems: "flex-start", gap: "6px", marginTop: "6px",
            padding: "6px 10px", borderRadius: "6px", background: "#f0f9ff",
            border: "1px solid #bae6fd", fontSize: "12px", color: "#0369a1",
          }}
        >
          <i className="ti ti-sparkles" style={{ marginTop: "1px" }} />
          <span>
            AI-matched to <strong>"{matchedBadge.albumName}"</strong>
            {matchedBadge.reasons.length > 0 && (
              <span style={{ display: "block", color: "#0284c7" }}>
                {matchedBadge.reasons.join(" ")}
              </span>
            )}
          </span>
        </div>
      )}

      {!matchedBadge && noMatchNotice && (
        <div style={{ marginTop: "6px", fontSize: "12px", color: "#6b7280" }}>
          No confident album match — pick an existing album or create a new one.
        </div>
      )}

      {open && !readOnly && (
        <div
          className="sub-dropdown-menu"
          style={{
            position: "absolute", top: "100%", left: 0, right: 0, zIndex: 50,
            background: "#fff", border: "1px solid #e5e7eb", borderRadius: "6px",
            marginTop: "4px", maxHeight: "300px", overflowY: "auto",
            boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)"
          }}
        >
          <button
            type="button"
            disabled={matching}
            style={{ display: "flex", alignItems: "center", gap: "8px", width: "100%", padding: "10px 12px", border: "none", borderBottom: "1px solid #e5e7eb", background: "#f0f9ff", color: "#0284c7", textAlign: "left", cursor: matching ? "default" : "pointer", fontSize: "14px", fontWeight: 500, opacity: matching ? 0.7 : 1 }}
            onClick={() => {
              onAutoMatch();
            }}
          >
            <i className={matching ? "ti ti-loader-2" : "ti ti-sparkles"} />
            {matching ? "Matching against your media library…" : autoMatchLabel}
          </button>

          {suggestions.length > 0 && (
            <div style={{ borderBottom: "1px solid #e5e7eb" }}>
              <div style={{ padding: "6px 12px 2px", fontSize: "11px", fontWeight: 700, letterSpacing: "0.04em", textTransform: "uppercase", color: "#0284c7" }}>
                AI Suggested
              </div>
              {suggestions.map((candidate) => (
                <button
                  key={candidate.albumId}
                  type="button"
                  style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "2px", width: "100%", padding: "8px 12px", border: "none", background: "#fff", color: "#111827", textAlign: "left", cursor: "pointer", fontSize: "14px" }}
                  onClick={() => {
                    onChange(candidate.albumName);
                    setOpen(false);
                  }}
                >
                  <span style={{ display: "flex", alignItems: "center", gap: "8px", width: "100%" }}>
                    <i className="ti ti-folder" style={{ color: "#0284c7" }} />
                    <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{candidate.albumName}</span>
                    <span style={{ flex: "none", fontSize: "11px", color: "#0284c7", fontWeight: 600 }}>
                      {Math.round(candidate.score * 100)}% match
                    </span>
                  </span>
                  {candidate.reasons.length > 0 && (
                    <span style={{ fontSize: "11px", color: "#6b7280", paddingLeft: "24px" }}>
                      {candidate.reasons.join(" ")}
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}

          {showCreate && (
            <button
              type="button"
              style={{ display: "flex", alignItems: "center", gap: "8px", width: "100%", padding: "10px 12px", border: "none", borderBottom: "1px solid #e5e7eb", background: "#fff", color: "#111827", textAlign: "left", cursor: "pointer", fontSize: "14px" }}
              onClick={() => setOpen(false)}
            >
              <i className="ti ti-plus" style={{ color: "#10b981" }} />
              <span>
                Create new album: <strong>"{value.trim()}"</strong>
                {createHint && (
                  <span style={{ display: "block", fontSize: "12px", color: "#6b7280", fontWeight: 400 }}>
                    {createHint}
                  </span>
                )}
              </span>
            </button>
          )}

          {filtered.length > 0 ? (
            filtered.map((album) => (
              <button
                key={album}
                type="button"
                style={{ display: "flex", alignItems: "center", gap: "8px", width: "100%", padding: "10px 12px", border: "none", background: album === value ? "#f3f4f6" : "#fff", color: "#374151", textAlign: "left", cursor: "pointer", fontSize: "14px" }}
                onClick={() => {
                  onChange(album);
                  setOpen(false);
                }}
              >
                <i className="ti ti-folder" style={{ color: "#9ca3af" }} />
                <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{album}</span>
                {albumBadges?.[album] && (
                  <span style={{ flex: "none", padding: "1px 7px", borderRadius: "9999px", background: "#EBF2FF", color: "#2563EB", fontSize: "10px", fontWeight: 700, letterSpacing: "0.04em", textTransform: "uppercase" }}>
                    {albumBadges[album]}
                  </span>
                )}
              </button>
            ))
          ) : (
            !showCreate && suggestions.length === 0 && (
              <div style={{ padding: "10px 12px", color: "#6b7280", fontSize: "14px", textAlign: "center" }}>
                No existing albums match your search.
              </div>
            )
          )}
        </div>
      )}
    </div>
  );
}
