import { useState, useRef, useEffect, useMemo } from "react";

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

      {open && !readOnly && (
        <div 
          className="sub-dropdown-menu" 
          style={{ 
            position: "absolute", top: "100%", left: 0, right: 0, zIndex: 50, 
            background: "#fff", border: "1px solid #e5e7eb", borderRadius: "6px", 
            marginTop: "4px", maxHeight: "250px", overflowY: "auto", 
            boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.1)" 
          }}
        >
          <button
            type="button"
            style={{ display: "flex", alignItems: "center", gap: "8px", width: "100%", padding: "10px 12px", border: "none", borderBottom: "1px solid #e5e7eb", background: "#f0f9ff", color: "#0284c7", textAlign: "left", cursor: "pointer", fontSize: "14px", fontWeight: 500 }}
            onClick={() => {
              onAutoMatch();
              setOpen(false);
            }}
          >
            <i className="ti ti-sparkles" /> {autoMatchLabel}
          </button>

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
            !showCreate && (
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