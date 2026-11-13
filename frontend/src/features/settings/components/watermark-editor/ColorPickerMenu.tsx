import { useEffect, useRef, useState } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";

interface ColorPickerMenuProps {
  title: string;
  color: string;
  presets: readonly string[];
  onChange: (color: string) => void;
  onSelectPreset?: (color: string) => void;
}

interface HsvColor {
  h: number; // 0..360
  s: number; // 0..100
  v: number; // 0..100
}

function normalizeHex(raw: string, fallback = "#1877f2"): string {
  const trimmed = raw.trim();
  if (/^#[0-9a-f]{6}$/i.test(trimmed)) return trimmed.toLowerCase();
  if (/^#[0-9a-f]{3}$/i.test(trimmed)) {
    const [, r, g, b] = trimmed;
    return `#${r}${r}${g}${g}${b}${b}`.toLowerCase();
  }
  const rgbaMatch = trimmed.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/i);
  if (rgbaMatch) {
    const toHex = (n: string) => Math.max(0, Math.min(255, Number(n))).toString(16).padStart(2, "0");
    return `#${toHex(rgbaMatch[1])}${toHex(rgbaMatch[2])}${toHex(rgbaMatch[3])}`;
  }
  return fallback;
}

function hexToHsv(hex: string): HsvColor {
  const clean = normalizeHex(hex).slice(1);
  const r = parseInt(clean.slice(0, 2), 16) / 255;
  const g = parseInt(clean.slice(2, 4), 16) / 255;
  const b = parseInt(clean.slice(4, 6), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const d = max - min;
  const v = max * 100;
  const s = max === 0 ? 0 : (d / max) * 100;

  let h = 0;
  if (d !== 0) {
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
    else if (max === g) h = ((b - r) / d + 2) * 60;
    else h = ((r - g) / d + 4) * 60;
  }
  return { h: Math.round(h), s: Math.round(s), v: Math.round(v) };
}

function hsvToHex({ h, s, v }: HsvColor): string {
  const sNorm = Math.max(0, Math.min(100, s)) / 100;
  const vNorm = Math.max(0, Math.min(100, v)) / 100;
  const c = vNorm * sNorm;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = vNorm - c;

  let r = 0;
  let g = 0;
  let b = 0;
  if (h < 60) [r, g, b] = [c, x, 0];
  else if (h < 120) [r, g, b] = [x, c, 0];
  else if (h < 180) [r, g, b] = [0, c, x];
  else if (h < 240) [r, g, b] = [0, x, c];
  else if (h < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];

  const toHex = (n: number) => Math.round((n + m) * 255).toString(16).padStart(2, "0");
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

/**
 * Custom color picker popover with preset swatches, a 2D saturation/brightness
 * spectrum, a hue slider, and a direct hex input.
 */
export function ColorPickerMenu({
  title,
  color,
  presets,
  onChange,
  onSelectPreset,
}: ColorPickerMenuProps) {
  const normalizedHex = normalizeHex(color);
  const [hsv, setHsv] = useState<HsvColor>(() => hexToHsv(normalizedHex));
  const [hexDraft, setHexDraft] = useState(normalizedHex.toUpperCase());
  const spectrumRef = useRef<HTMLDivElement>(null);
  const draggingRef = useRef(false);

  useEffect(() => {
    setHsv(hexToHsv(normalizedHex));
    setHexDraft(normalizedHex.toUpperCase());
  }, [normalizedHex]);

  function commitHsv(next: HsvColor) {
    setHsv(next);
    const nextHex = hsvToHex(next);
    setHexDraft(nextHex.toUpperCase());
    onChange(nextHex);
  }

  function updateFromSpectrum(clientX: number, clientY: number) {
    const el = spectrumRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const s = Math.round(Math.max(0, Math.min(1, (clientX - rect.left) / rect.width)) * 100);
    const v = Math.round(Math.max(0, Math.min(1, 1 - (clientY - rect.top) / rect.height)) * 100);
    commitHsv({ ...hsv, s, v });
  }

  function handleSpectrumPointerDown(e: ReactPointerEvent<HTMLDivElement>) {
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    draggingRef.current = true;
    updateFromSpectrum(e.clientX, e.clientY);
  }

  function handleSpectrumPointerMove(e: ReactPointerEvent<HTMLDivElement>) {
    if (!draggingRef.current) return;
    updateFromSpectrum(e.clientX, e.clientY);
  }

  function handleSpectrumPointerUp(e: ReactPointerEvent<HTMLDivElement>) {
    if (!draggingRef.current) return;
    draggingRef.current = false;
    e.currentTarget.releasePointerCapture(e.pointerId);
  }

  const pureHueHex = hsvToHex({ h: hsv.h, s: 100, v: 100 });

  return (
    <div className="canva-floating-menu canva-color-dropdown" onClick={(e) => e.stopPropagation()}>
      <div className="canva-dropdown-title">{title}</div>
      <div className="canva-color-grid">
        {presets.map((preset) => (
          <button
            key={preset}
            type="button"
            className={`canva-color-dot ${normalizedHex === preset.toLowerCase() ? "is-selected" : ""}`}
            style={{ backgroundColor: preset }}
            onClick={() => {
              if (onSelectPreset) onSelectPreset(preset);
              else onChange(preset);
            }}
            aria-label={`Select color ${preset}`}
          />
        ))}
      </div>

      <div className="canva-custom-picker">
        <div
          ref={spectrumRef}
          className="canva-spectrum-box"
          style={{ backgroundColor: pureHueHex }}
          onPointerDown={handleSpectrumPointerDown}
          onPointerMove={handleSpectrumPointerMove}
          onPointerUp={handleSpectrumPointerUp}
          onPointerCancel={handleSpectrumPointerUp}
          role="slider"
          aria-label="Color saturation and brightness"
          aria-valuetext={hexDraft}
        >
          <div className="canva-spectrum-white" />
          <div className="canva-spectrum-black" />
          <div
            className="canva-spectrum-thumb"
            style={{
              left: `${hsv.s}%`,
              top: `${100 - hsv.v}%`,
              backgroundColor: normalizedHex,
            }}
          />
        </div>

        <input
          type="range"
          min={0}
          max={359}
          value={hsv.h}
          className="canva-hue-slider"
          aria-label="Color hue"
          onChange={(e) => commitHsv({ ...hsv, h: Number(e.target.value) })}
        />

        <div className="canva-hex-row">
          <span className="canva-hex-swatch" style={{ backgroundColor: normalizedHex }} />
          <input
            type="text"
            className="canva-hex-input"
            value={hexDraft}
            maxLength={7}
            spellCheck={false}
            aria-label="Hex color code"
            onChange={(e) => {
              const raw = e.target.value.toUpperCase();
              setHexDraft(raw);
              if (/^#[0-9A-F]{6}$/.test(raw)) {
                const nextHex = raw.toLowerCase();
                setHsv(hexToHsv(nextHex));
                onChange(nextHex);
              }
            }}
            onBlur={() => setHexDraft(normalizedHex.toUpperCase())}
          />
        </div>
      </div>
    </div>
  );
}
