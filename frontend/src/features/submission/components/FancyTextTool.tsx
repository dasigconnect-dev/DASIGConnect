import { useMemo, useRef, useState } from "react";

export interface FancyTextSelection {
  start: number;
  end: number;
}

type FancyTextStyleId =
  | "bold-serif"
  | "italic-serif"
  | "bold-sans"
  | "italic-sans"
  | "script"
  | "plain";

interface FancyTextStyle {
  id: FancyTextStyleId;
  label: string;
  title: string;
}

interface Props {
  caption: string;
  selection: FancyTextSelection;
  disabled?: boolean;
  onReplaceSelection: (
    nextCaption: string,
    nextSelection: FancyTextSelection,
  ) => void;
  onPreviewSelection: (
    nextCaption: string,
    nextSelection: FancyTextSelection,
  ) => void;
  onRestoreSelection: (nextSelection: FancyTextSelection) => void;
  onPreviewStateChange?: (active: boolean) => void;
}

const CAPTION_WORD_LIMIT = 2000;
const PREVIEW_TEXT_LIMIT = 54;
const UPPER_A = "A".codePointAt(0)!;
const LOWER_A = "a".codePointAt(0)!;
const DIGIT_ZERO = "0".codePointAt(0)!;

const STYLE_OPTIONS: FancyTextStyle[] = [
  {
    id: "bold-serif",
    label: "Bold Serif",
    title: "Apply bold serif Unicode styling",
  },
  {
    id: "italic-serif",
    label: "Italic Serif",
    title: "Apply italic serif Unicode styling",
  },
  {
    id: "bold-sans",
    label: "Bold Sans-Serif",
    title: "Apply bold sans-serif Unicode styling",
  },
  {
    id: "italic-sans",
    label: "Italic Sans-Serif",
    title: "Apply italic sans-serif Unicode styling",
  },
  {
    id: "script",
    label: "Script / Cursive",
    title: "Apply script Unicode styling",
  },
  {
    id: "plain",
    label: "Plain",
    title: "Revert styled Unicode text to standard characters",
  },
];

const STYLE_MAPS: Record<Exclude<FancyTextStyleId, "plain">, Map<string, string>> = {
  "bold-serif": buildStyleMap(0x1d400, 0x1d41a, 0x1d7ce),
  "italic-serif": buildStyleMap(0x1d434, 0x1d44e, undefined, {
    lower: { h: 0x210e },
  }),
  "bold-sans": buildStyleMap(0x1d5d4, 0x1d5ee, 0x1d7ec),
  "italic-sans": buildStyleMap(0x1d608, 0x1d622),
  script: buildStyleMap(0x1d49c, 0x1d4b6, undefined, {
    upper: {
      B: 0x212c,
      E: 0x2130,
      F: 0x2131,
      H: 0x210b,
      I: 0x2110,
      L: 0x2112,
      M: 0x2133,
      R: 0x211b,
    },
    lower: {
      e: 0x212f,
      g: 0x210a,
      o: 0x2134,
    },
  }),
};

const REVERSE_STYLE_MAP = buildReverseStyleMap();

export default function FancyTextTool({
  caption,
  selection,
  disabled = false,
  onReplaceSelection,
  onPreviewSelection,
  onRestoreSelection,
  onPreviewStateChange,
}: Props) {
  const [open, setOpen] = useState(false);
  const baseCaptionRef = useRef<string | null>(null);
  const baseSelectionRef = useRef<FancyTextSelection | null>(null);
  const lastPreviewCaptionRef = useRef<string | null>(null);
  const previewingRef = useRef(false);

  const isPreviewingCurrentCaption =
    previewingRef.current && caption === lastPreviewCaptionRef.current;
  const activeCaption = isPreviewingCurrentCaption && baseCaptionRef.current != null
    ? baseCaptionRef.current
    : caption;
  const activeSelection = isPreviewingCurrentCaption && baseSelectionRef.current
    ? baseSelectionRef.current
    : selection;
  const hasExplicitSelection = activeSelection.end > activeSelection.start;
  const effectiveSelection = hasExplicitSelection
    ? activeSelection
    : open && activeCaption.length > 0
      ? { start: 0, end: activeCaption.length }
      : activeSelection;
  const selectedText = effectiveSelection.end > effectiveSelection.start
    ? activeCaption.slice(effectiveSelection.start, effectiveSelection.end)
    : "";
  const canUseSelection = Boolean(selectedText) && !disabled;

  const previews = useMemo(
    () =>
      STYLE_OPTIONS.map((style) => {
        const replacement = selectedText
          ? transformFancyText(selectedText, style.id)
          : sampleForStyle(style.id);

        return {
          ...style,
          replacement,
          replacementLength: replacement.length,
          preview: selectedText ? formatPreviewText(replacement) : replacement,
        };
      }),
    [selectedText],
  );

  function buildStyledCaption(styleId: FancyTextStyleId) {
    const replacement = transformFancyText(selectedText, styleId);
    const nextCaption =
      activeCaption.slice(0, effectiveSelection.start) +
      replacement +
      activeCaption.slice(effectiveSelection.end);
    const nextEnd = effectiveSelection.start + replacement.length;

    return {
      nextCaption,
      nextSelection: {
        start: effectiveSelection.start,
        end: nextEnd,
      },
    };
  }

  function openPanel() {
    baseCaptionRef.current = caption;
    baseSelectionRef.current = selection;
    previewingRef.current = false;
    lastPreviewCaptionRef.current = null;
    onPreviewStateChange?.(false);
    setOpen(true);
    onRestoreSelection(selection);
  }

  function closePanel(options: { restorePreview?: boolean; restoreSelection?: boolean } = {}) {
    const previewBaseCaption = baseCaptionRef.current;
    const previewBaseSelection = baseSelectionRef.current;
    const shouldRestorePreview =
      previewingRef.current &&
      caption === lastPreviewCaptionRef.current &&
      previewBaseCaption != null &&
      previewBaseSelection != null;
    const nextSelection = shouldRestorePreview && previewBaseSelection
      ? previewBaseSelection
      : selection;
    if (options.restorePreview && shouldRestorePreview && previewBaseSelection) {
      onPreviewSelection(previewBaseCaption, previewBaseSelection);
    }
    previewingRef.current = false;
    lastPreviewCaptionRef.current = null;
    onPreviewStateChange?.(false);
    baseCaptionRef.current = null;
    baseSelectionRef.current = null;
    setOpen(false);
    if (options.restoreSelection !== false) onRestoreSelection(nextSelection);
  }

  function previewStyle(styleId: FancyTextStyleId) {
    if (!canUseSelection) return;
    if (!isPreviewingCurrentCaption) {
      baseCaptionRef.current = caption;
      baseSelectionRef.current = selection;
    }
    const preview = buildStyledCaption(styleId);
    previewingRef.current = true;
    lastPreviewCaptionRef.current = preview.nextCaption;
    onPreviewStateChange?.(true);
    onPreviewSelection(preview.nextCaption, preview.nextSelection);
  }

  function applyStyle(styleId: FancyTextStyleId) {
    if (!canUseSelection) return;
    const result = buildStyledCaption(styleId);
    previewingRef.current = false;
    lastPreviewCaptionRef.current = null;
    onPreviewStateChange?.(false);
    onReplaceSelection(result.nextCaption, result.nextSelection);
    closePanel({ restorePreview: false, restoreSelection: false });
  }

  return (
    <div className="fancy-text-tool">
      <button
        type="button"
        className="fancy-text-trigger"
        aria-expanded={open}
        aria-controls="fancy-text-panel"
        disabled={disabled}
        title="Open Fancy text"
        onMouseDown={(event) => {
          event.preventDefault();
          event.stopPropagation();
          onRestoreSelection(selection);
        }}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          if (open) {
            closePanel({ restorePreview: true });
          } else {
            openPanel();
          }
        }}
      >
        <i className="ti ti-typography" aria-hidden />
        Fancy text
      </button>

      {open && (
        <div
          id="fancy-text-panel"
          className="fancy-text-panel"
          role="region"
          aria-label="Fancy text styles"
          onMouseDown={(event) => event.preventDefault()}
          onMouseLeave={() => {
            if (
              !previewingRef.current ||
              caption !== lastPreviewCaptionRef.current ||
              !baseCaptionRef.current ||
              !baseSelectionRef.current
            ) return;
            onPreviewSelection(baseCaptionRef.current, baseSelectionRef.current);
            previewingRef.current = false;
            lastPreviewCaptionRef.current = null;
            onPreviewStateChange?.(false);
            onRestoreSelection(baseSelectionRef.current);
          }}
        >
          <div className="fancy-text-panel-head">
            <span className="fancy-text-panel-title">Fancy text</span>
            <button
              type="button"
              className="fancy-text-close"
              aria-label="Close Fancy text"
              onMouseDown={(event) => event.preventDefault()}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                closePanel({ restorePreview: true });
              }}
            >
              <i className="ti ti-x" aria-hidden />
            </button>
          </div>

          {!selectedText && (
            <div className="fancy-text-empty" aria-live="polite">
              No caption text selected.
            </div>
          )}

          <div className="fancy-text-options">
            {previews.map((style) => {
              const nextLength =
                countWords(activeCaption.slice(0, effectiveSelection.start)) +
                countWords(style.replacement) +
                countWords(activeCaption.slice(effectiveSelection.end));
              const exceedsLimit = nextLength > CAPTION_WORD_LIMIT;
              const disabledStyle = !canUseSelection || exceedsLimit;

              return (
                <button
                  key={style.id}
                  type="button"
                  className="fancy-text-option"
                  disabled={disabledStyle}
                  title={
                    exceedsLimit
                      ? "Styled text would exceed the caption word limit"
                      : style.title
                  }
                  onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => previewStyle(style.id)}
                  onFocus={() => previewStyle(style.id)}
                  onClick={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    applyStyle(style.id);
                  }}
                >
                  <span className="fancy-text-option-label">
                    {style.label}
                  </span>
                  <span className="fancy-text-option-preview">
                    {style.preview}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

function countWords(value: string): number {
  return value.trim().match(/\S+/g)?.length ?? 0;
}

function transformFancyText(text: string, styleId: FancyTextStyleId): string {
  const plainText = revertFancyText(text);
  if (styleId === "plain") return plainText;

  const styleMap = STYLE_MAPS[styleId];
  return Array.from(plainText)
    .map((character) => styleMap.get(character) ?? character)
    .join("");
}

function revertFancyText(text: string): string {
  return Array.from(text)
    .map((character) => REVERSE_STYLE_MAP.get(character) ?? character)
    .join("");
}

function sampleForStyle(styleId: FancyTextStyleId): string {
  return transformFancyText("DASIG Text", styleId);
}

function formatPreviewText(text: string): string {
  const characters = Array.from(text);
  if (characters.length <= PREVIEW_TEXT_LIMIT) return text;

  const clipped = characters.slice(0, PREVIEW_TEXT_LIMIT);
  let lastSpaceIndex = -1;
  for (let index = clipped.length - 1; index >= 0; index -= 1) {
    if (/\s/.test(clipped[index])) {
      lastSpaceIndex = index;
      break;
    }
  }
  const safeClip = lastSpaceIndex > 12
    ? clipped.slice(0, lastSpaceIndex)
    : clipped;

  return `${safeClip.join("").trimEnd()}...`;
}

function buildStyleMap(
  upperStart: number,
  lowerStart: number,
  digitStart?: number,
  exceptions?: {
    upper?: Record<string, number>;
    lower?: Record<string, number>;
  },
): Map<string, string> {
  const map = new Map<string, string>();

  for (let index = 0; index < 26; index += 1) {
    const upper = String.fromCodePoint(UPPER_A + index);
    const lower = String.fromCodePoint(LOWER_A + index);
    const upperCodePoint = exceptions?.upper?.[upper] ?? upperStart + index;
    const lowerCodePoint = exceptions?.lower?.[lower] ?? lowerStart + index;

    map.set(upper, String.fromCodePoint(upperCodePoint));
    map.set(lower, String.fromCodePoint(lowerCodePoint));
  }

  if (digitStart) {
    for (let index = 0; index < 10; index += 1) {
      map.set(
        String.fromCodePoint(DIGIT_ZERO + index),
        String.fromCodePoint(digitStart + index),
      );
    }
  }

  return map;
}

function buildReverseStyleMap(): Map<string, string> {
  const reverseMap = new Map<string, string>();

  Object.values(STYLE_MAPS).forEach((styleMap) => {
    styleMap.forEach((styledCharacter, plainCharacter) => {
      reverseMap.set(styledCharacter, plainCharacter);
    });
  });

  return reverseMap;
}
