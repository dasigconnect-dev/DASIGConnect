import { useState, useRef, useEffect } from "react";
import type { PointerEvent as ReactPointerEvent } from "react";
import type {
  AspectRatioType,
  WatermarkAnchor,
  WatermarkElement,
  WatermarkElementType,
  ElementRatioConfig,
} from "../../../types/watermark.types";
import {
  DEFAULT_ANCHOR_INSET_PERCENT,
  DEFAULT_ASPECT_RATIO,
  computeBox,
  fitTextHeight,
  resolveRatioConfig,
  updateElementForRatio,
  getSortedElementsByRatio,
  reorderElementsByRatio,
  reorderElementsByDrag,
  type ElementBox,
  MAX_WATERMARK_ELEMENTS,
  normalizeWatermarkAnchor,
} from "../../../components/watermark/watermarkLayout";
import WatermarkElementContent from "../../../components/watermark/WatermarkElementContent";
import { textElementStyle } from "../../../components/watermark/watermarkStyles";
import { useCanvasInteraction } from "./watermark-editor/useCanvasInteraction";
import {
  COLOR_PRESETS,
  FONT_OPTIONS,
  FONT_STYLESHEET_URL,
  ACCEPTED_IMAGE_TYPES,
  type CanvasBackground,
} from "./watermark-editor/editorConstants";
import {
  createImageElement,
  createShapeElement,
  createTextElement,
  switchShapeVariant,
} from "./watermark-editor/elementFactory";
import { ColorPickerMenu } from "./watermark-editor/ColorPickerMenu";
import watermarkSample from "../../../assets/watermark-preview.svg";
import "../../../styles/watermark-content.css";
import "../../../styles/watermark-editor.css";

interface WatermarkCanvasEditorProps {
  elements: WatermarkElement[];
  onChange: (elements: WatermarkElement[]) => void;
  disabled?: boolean;
  institutionName?: string;
  institutionLogoUrl?: string | null;
  onBack?: () => void;
  onSave?: () => void;
  isSaving?: boolean;
}

const CORNER_ANCHORS = ["top-left", "top-right", "bottom-left", "bottom-right"] as const;
const EDGE_ANCHORS = ["top", "bottom"] as const;

export default function WatermarkCanvasEditor({
  elements,
  onChange,
  disabled = false,
  institutionName,
  institutionLogoUrl,
  onBack,
  onSave,
  isSaving = false,
}: WatermarkCanvasEditorProps) {
  const aspectRatio: AspectRatioType = DEFAULT_ASPECT_RATIO;
  const [background, setBackground] = useState<CanvasBackground>("sample-photo");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [layersOpen, setLayersOpen] = useState(false);
  const [openAnchorMenu, setOpenAnchorMenu] = useState<WatermarkAnchor | null>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [showFontPicker, setShowFontPicker] = useState(false);
  const [showAlignMenu, setShowAlignMenu] = useState(false);
  const [editingTextId, setEditingTextId] = useState<string | null>(null);
  const [draggedLayerId, setDraggedLayerId] = useState<string | null>(null);
  const [dragOverLayerId, setDragOverLayerId] = useState<string | null>(null);
  const [zoom, setZoom] = useState<number>(100);
  const [canvasSize, setCanvasSize] = useState<{ width: number; height: number } | null>(null);
  const [measuredCanvasSize, setMeasuredCanvasSize] = useState<{ width: number; height: number }>({
    width: 520,
    height: 520,
  });
  const [isResizingCanvas, setIsResizingCanvas] = useState(false);

  const canvasRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);
  const anchorImageInputRef = useRef<HTMLInputElement>(null);
  const pendingImageAnchorRef = useRef<WatermarkAnchor | null>(null);
  const canvasResizeSessionRef = useRef<{
    direction: "e" | "w" | "s" | "n" | "se";
    startX: number;
    startY: number;
    startWidth: number;
    startHeight: number;
  } | null>(null);

  const canvasAspect =
    measuredCanvasSize.width > 0 && measuredCanvasSize.height > 0
      ? measuredCanvasSize.width / measuredCanvasSize.height
      : 1;

  function handleZoomIn() {
    setZoom((prev) => Math.min(200, prev + 10));
  }

  function handleZoomOut() {
    setZoom((prev) => Math.max(50, prev - 10));
  }

  function handleResetZoom() {
    setZoom(100);
  }

  function handleResetCanvasSize() {
    setCanvasSize(null);
  }

  function beginCanvasResize(
    event: ReactPointerEvent,
    direction: "e" | "w" | "s" | "n" | "se",
  ) {
    event.stopPropagation();
    event.preventDefault();
    const frame = canvasRef.current;
    if (!frame) return;
    canvasResizeSessionRef.current = {
      direction,
      startX: event.clientX,
      startY: event.clientY,
      startWidth: frame.offsetWidth || measuredCanvasSize.width || 520,
      startHeight: frame.offsetHeight || measuredCanvasSize.height || 520,
    };
    setIsResizingCanvas(true);
  }

  useEffect(() => {
    const frame = canvasRef.current;
    if (!frame) return;
    const updateMeasured = () => {
      const w = frame.offsetWidth;
      const h = frame.offsetHeight;
      if (w > 0 && h > 0) {
        setMeasuredCanvasSize((prev) => (prev.width === w && prev.height === h ? prev : { width: w, height: h }));
      }
    };
    updateMeasured();
    const observer = new ResizeObserver(updateMeasured);
    observer.observe(frame);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    function handleCanvasPointerMove(event: PointerEvent) {
      const session = canvasResizeSessionRef.current;
      if (!session) return;
      const scale = zoom / 100;
      const dx = (event.clientX - session.startX) / scale;
      const dy = (event.clientY - session.startY) / scale;

      const stage = stageRef.current;
      const maxStageW = stage ? Math.max(320, stage.clientWidth - 64) : 1100;
      const maxStageH = stage ? Math.max(320, stage.clientHeight - 64) : 900;
      const minCanvasDim = 220;

      let nextWidth = session.startWidth;
      let nextHeight = session.startHeight;

      if (session.direction.includes("e")) {
        nextWidth = Math.max(minCanvasDim, Math.min(maxStageW, Math.round(session.startWidth + dx * 2)));
      } else if (session.direction.includes("w")) {
        nextWidth = Math.max(minCanvasDim, Math.min(maxStageW, Math.round(session.startWidth - dx * 2)));
      }

      if (session.direction.includes("s")) {
        nextHeight = Math.max(minCanvasDim, Math.min(maxStageH, Math.round(session.startHeight + dy * 2)));
      } else if (session.direction.includes("n")) {
        nextHeight = Math.max(minCanvasDim, Math.min(maxStageH, Math.round(session.startHeight - dy * 2)));
      }

      setCanvasSize({ width: nextWidth, height: nextHeight });
      setMeasuredCanvasSize({ width: nextWidth, height: nextHeight });
    }

    function handleCanvasPointerUp() {
      if (canvasResizeSessionRef.current) {
        canvasResizeSessionRef.current = null;
        setIsResizingCanvas(false);
      }
    }

    window.addEventListener("pointermove", handleCanvasPointerMove);
    window.addEventListener("pointerup", handleCanvasPointerUp);
    window.addEventListener("pointercancel", handleCanvasPointerUp);
    return () => {
      window.removeEventListener("pointermove", handleCanvasPointerMove);
      window.removeEventListener("pointerup", handleCanvasPointerUp);
      window.removeEventListener("pointercancel", handleCanvasPointerUp);
    };
  }, [zoom]);

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;
    function handleWheel(e: WheelEvent) {
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const delta = e.deltaY < 0 ? 10 : -10;
        setZoom((prev) => Math.max(50, Math.min(200, prev + delta)));
      }
    }
    stage.addEventListener("wheel", handleWheel, { passive: false });
    return () => stage.removeEventListener("wheel", handleWheel);
  }, []);

  const interaction = useCanvasInteraction({
    frameRef: canvasRef,
    ratio: aspectRatio,
    canvasAspect,
    elements,
    onChange,
    disabled,
  });

  const selectedElement = elements.find((el) => el.id === selectedId) ?? null;
  const selectedCfg = selectedElement ? resolveRatioConfig(selectedElement, aspectRatio) : null;

  useEffect(() => {
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = FONT_STYLESHEET_URL;
    link.dataset.watermarkEditorFonts = "true";
    document.head.appendChild(link);
    return () => link.remove();
  }, []);

  // Auto-close popovers on outside click
  useEffect(() => {
    function handleDocClick(e: MouseEvent) {
      const target = e.target as HTMLElement;
      if (!target.closest(".canva-popover-anchor")) {
        setShowColorPicker(false);
        setShowFontPicker(false);
        setShowAlignMenu(false);
      }
      if (!target.closest(".wm-anchor-control")) {
        setOpenAnchorMenu(null);
      }
    }
    document.addEventListener("mousedown", handleDocClick);
    return () => document.removeEventListener("mousedown", handleDocClick);
  }, []);

  // Add Element Handlers
  function getAvailableAnchor(type: WatermarkElementType): WatermarkAnchor | null {
    const anchors: readonly WatermarkAnchor[] = type === "shape" ? EDGE_ANCHORS : CORNER_ANCHORS;
    return anchors.find((anchor) =>
      !elements.some((el) => normalizeWatermarkAnchor(el).anchor === anchor)
    ) ?? null;
  }

  function addElement(
    type: WatermarkElementType,
    initialVariant?: string,
    requestedAnchor?: WatermarkAnchor,
  ) {
    if (elements.length >= MAX_WATERMARK_ELEMENTS || disabled) return;
    const anchor = requestedAnchor ?? getAvailableAnchor(type);
    if (!anchor || elements.some((el) => normalizeWatermarkAnchor(el).anchor === anchor)) return;

    let newElement: WatermarkElement;

    if (type === "image") {
      newElement = createImageElement(initialVariant || institutionLogoUrl || "/dasig-logo.png", anchor);
    } else if (type === "text") {
      const defaultText = initialVariant || (institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect");
      newElement = createTextElement(defaultText, anchor);
    } else {
      const isLine = initialVariant === "line";
      const isGradient = initialVariant === "gradient";
      const edgeAnchor = anchor === "top" ? "top" : "bottom";
      newElement = createShapeElement(isLine ? "line" : isGradient ? "gradient" : "rectangle", edgeAnchor);
    }

    const updated = [...elements, newElement];
    onChange(updated);
    setSelectedId(newElement.id);
    setOpenAnchorMenu(null);
  }

  function updateSelected(updates: Partial<ElementRatioConfig>) {
    if (!selectedId || disabled) return;
    const updated = elements.map((el) =>
      el.id === selectedId ? updateElementForRatio(el, aspectRatio, updates) : el
    );
    onChange(updated);
  }

  function updateSelectedShapeVariant(variant: "rectangle" | "line" | "gradient") {
    if (!selectedElement || disabled) return;
    const patch = switchShapeVariant(selectedElement, variant);
    onChange(
      elements.map((el) =>
        el.id === selectedElement.id
          ? updateElementForRatio({ ...el, ...patch }, aspectRatio, patch)
          : el
      )
    );
  }

  function removeSelected() {
    if (!selectedId || disabled) return;
    const updated = elements.filter((el) => el.id !== selectedId);
    onChange(updated);
    setSelectedId(null);
  }

  function alignElement(alignment: WatermarkAnchor) {
    if (!selectedElement || disabled) return;
    const anchor = selectedElement.type === "shape"
      ? alignment === "top" ? "top" : "bottom"
      : CORNER_ANCHORS.includes(alignment as (typeof CORNER_ANCHORS)[number])
        ? alignment
        : "bottom-right";
    const xPercent = anchor === "top" || anchor === "bottom" ? 0 : DEFAULT_ANCHOR_INSET_PERCENT;
    const yPercent = anchor === "top" || anchor === "bottom" ? 0 : DEFAULT_ANCHOR_INSET_PERCENT;
    onChange(elements.map((el) =>
      el.id === selectedElement.id
        ? { ...updateElementForRatio(el, aspectRatio, { xPercent, yPercent }), anchor }
        : el
    ));
    setShowAlignMenu(false);
  }

  function renderCornerAnchorOptions() {
    const isShape = selectedElement?.type === "shape";
    const options = isShape
      ? EDGE_ANCHORS.map((anchor) => ({ anchor, label: anchor === "top" ? "Top edge" : "Bottom edge" }))
      : CORNER_ANCHORS.map((anchor) => ({
          anchor,
          label: anchor.split("-").map((part) => part[0].toUpperCase() + part.slice(1)).join(" "),
        }));

    return (
      <>
        <div className="canva-dropdown-title">{isShape ? "Anchor ribbon to an edge" : "Anchor to a corner"}</div>
        <div className="canva-anchor-grid">
          {options.map(({ anchor, label }) => (
            <button
              key={anchor}
              type="button"
              className={`canva-anchor-option ${selectedElement?.anchor === anchor ? "is-active" : ""}`}
              onClick={() => alignElement(anchor)}
              aria-pressed={selectedElement?.anchor === anchor}
              title={`${label}, with a 3% inset`}
            >
              <span className={`canva-anchor-preview canva-anchor-preview-${anchor}`} aria-hidden="true" />
              {label}
            </button>
          ))}
        </div>
        <p className="canva-anchor-hint">Elements stay locked to their anchor while you resize them.</p>
      </>
    );
  }

  function setElementSize(widthPercent: number, heightPercent?: number) {
    if (!selectedElement || disabled) return;
    const currentCfg = resolveRatioConfig(selectedElement, aspectRatio);
    const currentW = currentCfg.widthPercent || 18;
    const currentH = currentCfg.heightPercent || 18;
    const aspect = currentW > 0 ? currentH / currentW : 1;
    const maxAllowed = 100 - DEFAULT_ANCHOR_INSET_PERCENT * 2;
    const targetW = Math.min(maxAllowed, Math.max(3, Math.round(widthPercent * 10) / 10));
    const targetH =
      heightPercent !== undefined
        ? Math.min(maxAllowed, Math.max(1, heightPercent))
        : Math.min(maxAllowed, Math.round(targetW * aspect * 10) / 10);

    onChange(
      elements.map((el) =>
        el.id === selectedElement.id
          ? updateElementForRatio(el, aspectRatio, {
              xPercent: DEFAULT_ANCHOR_INSET_PERCENT,
              yPercent: DEFAULT_ANCHOR_INSET_PERCENT,
              widthPercent: targetW,
              heightPercent: targetH,
            })
          : el,
      ),
    );
  }

  function moveLayer(id: string, direction: "up" | "down" | "top" | "bottom") {
    if (disabled) return;
    onChange(reorderElementsByRatio(elements, aspectRatio, id, direction));
  }

  function handleReorderLayers(draggedId: string, targetId: string) {
    if (draggedId === targetId || disabled) return;
    onChange(reorderElementsByDrag(elements, aspectRatio, draggedId, targetId));
    setSelectedId(draggedId);
  }

  function handleImageUpload(file: File, anchor?: WatermarkAnchor) {
    if (!file || !file.type.startsWith("image/")) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        if (!anchor && selectedElement && selectedElement.type === "image") {
          onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: reader.result as string } : el)));
        } else {
          addElement("image", reader.result, anchor);
        }
      }
    };
    reader.readAsDataURL(file);
  }

  function openAnchorImagePicker(anchor: WatermarkAnchor) {
    pendingImageAnchorRef.current = anchor;
    setOpenAnchorMenu(null);
    anchorImageInputRef.current?.click();
  }

  function toggleElementVisibility(id: string) {
    if (disabled) return;
    const target = elements.find((el) => el.id === id);
    if (!target) return;
    const currentCfg = resolveRatioConfig(target, aspectRatio);
    onChange(
      elements.map((el) =>
        el.id === id ? updateElementForRatio(el, aspectRatio, { hidden: !currentCfg.hidden }) : el
      )
    );
  }

  function getElementLabel(el: WatermarkElement) {
    if (el.type === "image") {
      return el.imageUrl?.includes("dasig")
        ? "DASIG Logo"
        : institutionName
          ? `${institutionName} Logo`
          : "Logo / Image";
    }
    if (el.type === "text") {
      return el.text || "Text / Handle";
    }
    if (el.shapeType === "line") return "Divider Line";
    if (el.shapeType === "gradient") return "Gradient Scrim";
    return "Background Ribbon";
  }

  // Floating pill positioning helpers
  function getFloatingPillPlacement(box: ElementBox) {
    const isDockedTop = box.top < 14;
    const isFullWidth = box.width >= 90;
    const isDockedLeft = !isFullWidth && box.left < 10;
    const isDockedRight = !isFullWidth && box.left + box.width > 88;

    let positionClass = isDockedTop ? "pill-docked-top" : "pill-docked-bottom";
    if (isDockedLeft) positionClass += " pill-align-left";
    else if (isDockedRight) positionClass += " pill-align-right";
    else positionClass += " pill-align-center";

    return positionClass;
  }

  const sortedElements = getSortedElementsByRatio(elements, aspectRatio);
  const selectedIndex = sortedElements.findIndex((el) => el.id === selectedId);

  return (
    <div className="canva-studio-app">
      {/* 0. Canva Studio Main Header Bar */}
      <header className="canva-studio-header">
        <div className="canva-header-left">
          {onBack && (
            <button
              type="button"
              className="sub-back-btn"
              onClick={onBack}
              title="Return to Page Settings"
            >
              <i className="ti ti-arrow-left" aria-hidden="true" />
              <span>Back to Page Settings</span>
            </button>
          )}
        </div>

        <div className="canva-header-center">
          <h1 className="canva-header-title">Watermark Studio</h1>
        </div>

        <div className="canva-header-right">
          {onSave && (
            <button
              type="button"
              className="canva-header-save-btn"
              disabled={disabled || isSaving}
              aria-busy={isSaving}
              onClick={onSave}
              title="Save watermark changes"
            >
              <i className={isSaving ? "ti ti-loader-2 canva-spinner" : "ti ti-device-floppy"} aria-hidden="true" />
              <span>{isSaving ? "Saving..." : "Save"}</span>
            </button>
          )}
        </div>
      </header>

      {/* 1. Main Studio Layout (Layers Drawer + Center Workspace) */}
      <div className="canva-studio-main">
      {/* 2. Floating Overlay Layers Drawer */}
      <aside className={`canva-drawer ${layersOpen ? "is-open" : "is-closed"}`} aria-hidden={!layersOpen}>
        {layersOpen && (
          <>
            <div className="canva-drawer-header">
              <h3>{`Active Layers (${elements.length}/${MAX_WATERMARK_ELEMENTS})`}</h3>
              <button
                type="button"
                className="canva-drawer-close"
                onClick={() => setLayersOpen(false)}
                title="Close layers drawer"
              >
                <i className="ti ti-x"></i>
              </button>
            </div>

            <div className="canva-drawer-content">
                <div className="canva-drawer-section canva-anim-fade">
                  <p className="canva-drawer-desc">
                    Reorder layers, toggle visibility, or select covered elements.
                  </p>
                  {elements.length === 0 ? (
                    <div className="canva-empty-layers">
                      <i className="ti ti-layers-subtract"></i>
                      <p>No elements added yet. Use a plus button on the canvas to add a layer.</p>
                    </div>
                  ) : (
                    <div className="canva-layers-stack">
                      {[...sortedElements]
                        .map((el, sortedIdx) => ({ el, sortedIdx }))
                        .reverse()
                        .map(({ el, sortedIdx }) => {
                          const isSelected = el.id === selectedId;
                          const elCfg = resolveRatioConfig(el, aspectRatio);
                          const isHidden = !!elCfg.hidden;
                          const isTop = sortedIdx === sortedElements.length - 1;
                          const isBottom = sortedIdx === 0;

                          return (
                            <div
                              key={el.id}
                              className={`canva-layer-row ${isSelected ? "is-selected" : ""} ${draggedLayerId === el.id ? "is-dragging" : ""} ${dragOverLayerId === el.id && draggedLayerId !== el.id ? "is-drag-over" : ""}`}
                              onClick={() => setSelectedId(el.id)}
                              draggable={!disabled}
                              onDragStart={(e) => {
                                e.dataTransfer.setData("text/plain", el.id);
                                e.dataTransfer.effectAllowed = "move";
                                setDraggedLayerId(el.id);
                              }}
                              onDragOver={(e) => {
                                e.preventDefault();
                                e.dataTransfer.dropEffect = "move";
                                if (dragOverLayerId !== el.id) {
                                  setDragOverLayerId(el.id);
                                }
                              }}
                              onDragLeave={() => {
                                setDragOverLayerId(null);
                              }}
                              onDrop={(e) => {
                                e.preventDefault();
                                const sourceId = e.dataTransfer.getData("text/plain") || draggedLayerId;
                                if (sourceId) {
                                  handleReorderLayers(sourceId, el.id);
                                }
                                setDraggedLayerId(null);
                                setDragOverLayerId(null);
                              }}
                              onDragEnd={() => {
                                setDraggedLayerId(null);
                                setDragOverLayerId(null);
                              }}
                              title="Drag to reorder in this ratio or click to select"
                            >
                              <div className="canva-layer-info">
                                <i className="ti ti-grip-vertical canva-layer-grip" title="Drag to reorder" />
                                <i
                                  className={`ti ${
                                    el.type === "image"
                                      ? "ti-photo"
                                      : el.type === "text"
                                        ? "ti-typography"
                                        : "ti-rectangle"
                                  }`}
                                />
                                <span className="canva-layer-title">{getElementLabel(el)}</span>
                              </div>

                              <div
                                className="canva-layer-actions"
                                onClick={(e) => e.stopPropagation()}
                                onDragStart={(e) => e.stopPropagation()}
                                draggable={false}
                              >
                                <button
                                  type="button"
                                  className="canva-layer-icon-btn"
                                  onClick={() => moveLayer(el.id, "up")}
                                  disabled={isTop || disabled}
                                  title="Bring Forward in this ratio"
                                >
                                  <i className="ti ti-arrow-up" />
                                </button>
                                <button
                                  type="button"
                                  className="canva-layer-icon-btn"
                                  onClick={() => moveLayer(el.id, "down")}
                                  disabled={isBottom || disabled}
                                  title="Send Backward in this ratio"
                                >
                                  <i className="ti ti-arrow-down" />
                                </button>
                                <button
                                  type="button"
                                  className={`canva-layer-icon-btn ${isHidden ? "is-hidden" : ""}`}
                                  onClick={() => toggleElementVisibility(el.id)}
                                  title={isHidden ? "Show element in this ratio" : "Hide element in this ratio"}
                                >
                                  <i className={`ti ${isHidden ? "ti-eye-off" : "ti-eye"}`} />
                                </button>
                                <button
                                  type="button"
                                  className="canva-layer-icon-btn is-danger"
                                  onClick={() => {
                                    onChange(elements.filter((item) => item.id !== el.id));
                                    if (selectedId === el.id) setSelectedId(null);
                                  }}
                                  title="Delete layer"
                                  disabled={disabled}
                                >
                                  <i className="ti ti-trash" />
                                </button>
                              </div>
                            </div>
                          );
                        })}
                    </div>
                  )}
                </div>
            </div>
          </>
        )}
      </aside>

      {/* 3. Main Design Studio Workspace */}
      <main className="canva-workspace">
        {/* Top Contextual Canva Toolbar */}
        <header className="canva-topbar">
          {/* A. If Text Element is Selected */}
          {selectedElement && selectedElement.type === "text" && selectedCfg && (
            <div className="canva-context-group canva-anim-bar">
              {/* Font Family Selector Dropdown */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn canva-font-btn"
                  onClick={() => setShowFontPicker((prev) => !prev)}
                  title="Font Family"
                  disabled={disabled}
                >
                  <i className="ti ti-letter-case" />
                  <span>{FONT_OPTIONS.find((f) => f.value === (selectedCfg.fontFamily || "inherit"))?.label || "Modern Sans"}</span>
                  <i className="ti ti-chevron-down" />
                </button>

                {showFontPicker && (
                  <div className="canva-floating-menu canva-font-dropdown">
                    <div className="canva-dropdown-title">Font Family</div>
                    <div className="canva-font-list">
                      {FONT_OPTIONS.map((f) => (
                        <button
                          key={f.value}
                          type="button"
                          className={`canva-font-item ${(selectedCfg.fontFamily || "inherit") === f.value ? "is-selected" : ""}`}
                          style={{ fontFamily: f.value }}
                          onClick={() => {
                            updateSelected({ fontFamily: f.value });
                            setShowFontPicker(false);
                          }}
                        >
                          <span>{f.label}</span>
                          {(selectedCfg.fontFamily || "inherit") === f.value && (
                            <i className="ti ti-check" />
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Font Size Stepper */}
              <div className="canva-stepper-pill" title="Font size as a percentage of the image's shorter side">
                <button
                  type="button"
                  className="canva-stepper-btn"
                  onClick={() => {
                    const newFont = Math.max(1.5, Math.round(((selectedCfg.fontSizePercent || 3.2) - 0.3) * 10) / 10);
                    updateSelected({
                      fontSizePercent: newFont,
                      heightPercent: fitTextHeight(selectedElement.text || "", newFont),
                    });
                  }}
                  disabled={disabled}
                >
                  <i className="ti ti-minus" />
                </button>
                <span className="canva-stepper-val">{Math.round((selectedCfg.fontSizePercent || 3.2) * 10) / 10}%</span>
                <button
                  type="button"
                  className="canva-stepper-btn"
                  onClick={() => {
                    const newFont = Math.min(8.0, Math.round(((selectedCfg.fontSizePercent || 3.2) + 0.3) * 10) / 10);
                    updateSelected({
                      fontSizePercent: newFont,
                      heightPercent: fitTextHeight(selectedElement.text || "", newFont),
                    });
                  }}
                  disabled={disabled}
                >
                  <i className="ti ti-plus" />
                </button>
              </div>

              {/* Text Color Swatch Popover */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowColorPicker((prev) => !prev)}
                  title="Text Color"
                  disabled={disabled}
                >
                  <span
                    className="canva-color-preview-pill"
                    style={{ backgroundColor: selectedCfg.textColor || "#FFFFFF" }}
                  />
                  <span>Color</span>
                  <i className="ti ti-chevron-down" />
                </button>

                {showColorPicker && (
                  <ColorPickerMenu
                    title="Text Color"
                    color={selectedCfg.textColor || "#FFFFFF"}
                    presets={COLOR_PRESETS}
                    onChange={(color) => updateSelected({ textColor: color })}
                    onSelectPreset={(color) => {
                      updateSelected({ textColor: color });
                      setShowColorPicker(false);
                    }}
                  />
                )}
              </div>

              {/* Bold Toggle */}
              <button
                type="button"
                className={`canva-tool-btn ${selectedCfg.fontWeight === "700" || selectedCfg.fontWeight === "800" ? "is-active" : ""}`}
                onClick={() =>
                  updateSelected({
                    fontWeight: selectedCfg.fontWeight === "700" ? "400" : "700",
                  })
                }
                title="Bold"
                disabled={disabled}
              >
                <i className="ti ti-bold" />
              </button>

              {/* Italic Toggle */}
              <button
                type="button"
                className={`canva-tool-btn ${selectedCfg.fontStyle === "italic" ? "is-active" : ""}`}
                onClick={() =>
                  updateSelected({
                    fontStyle: selectedCfg.fontStyle === "italic" ? "normal" : "italic",
                  })
                }
                title="Italic"
                disabled={disabled}
              >
                <i className="ti ti-italic" />
              </button>

              {/* Text Shadow Toggle */}
              <button
                type="button"
                className={`canva-tool-btn ${selectedCfg.textShadow !== false ? "is-active" : ""}`}
                onClick={() =>
                  updateSelected({
                    textShadow: selectedCfg.textShadow === false,
                  })
                }
                title={selectedCfg.textShadow === false ? "Enable Text Shadow" : "Remove Text Shadow"}
                disabled={disabled}
              >
                <i className="ti ti-shadow" />
                <span>Shadow</span>
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedCfg.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedCfg.opacity ?? 1.0}
                  onChange={(e) => updateSelected({ opacity: parseFloat(e.target.value) })}
                  disabled={disabled}
                />
              </div>

              {/* Position / Align Menu Toggle */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowAlignMenu((prev) => !prev)}
                  title="Position & Alignment"
                  disabled={disabled}
                >
                  <i className="ti ti-layout-align-bottom" />
                  <span>Position</span>
                </button>

                {showAlignMenu && (
                  <div className="canva-floating-menu canva-align-dropdown">
                    {renderCornerAnchorOptions()}
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">
                      Layer Stacking ({selectedIndex + 1} of {elements.length})
                    </div>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "up")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "down")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "top")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "bottom")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                    <div className="canva-dropdown-divider" />
                    <button
                      type="button"
                      onClick={() => {
                        setLayersOpen(true);
                        setShowAlignMenu(false);
                      }}
                    >
                      <i className="ti ti-layers-intersect" /> Open Layers Panel
                    </button>
                  </div>
                )}
              </div>

              {/* Direct Layers Toggle Button */}
              <button
                type="button"
                className={`canva-tool-btn ${layersOpen ? "is-active" : ""}`}
                onClick={() => setLayersOpen((prev) => !prev)}
                title="Manage Layers"
                disabled={disabled}
              >
                <i className="ti ti-layers-intersect" />
                <span>Layers</span>
              </button>

              {/* Delete Button */}
              <button
                type="button"
                className="canva-tool-btn is-danger"
                onClick={removeSelected}
                title="Delete Text"
                disabled={disabled}
              >
                <i className="ti ti-trash" />
              </button>
            </div>
          )}

          {/* B. If Shape / Ribbon is Selected */}
          {selectedElement && selectedElement.type === "shape" && selectedCfg && (
            <div className="canva-context-group canva-anim-bar">
              {/* Shape Type Segmented Control */}
              <div className="canva-pill-segment">
                <button
                  type="button"
                  className={`canva-segment-item ${selectedElement.shapeType === "rectangle" || (!selectedElement.shapeType && selectedElement.shapeType !== "line" && selectedElement.shapeType !== "gradient") ? "is-active" : ""}`}
                  onClick={() => updateSelectedShapeVariant("rectangle")}
                  disabled={disabled}
                >
                  Ribbon
                </button>
                <button
                  type="button"
                  className={`canva-segment-item ${selectedElement.shapeType === "gradient" ? "is-active" : ""}`}
                  onClick={() => updateSelectedShapeVariant("gradient")}
                  disabled={disabled}
                >
                  Gradient Scrim
                </button>
              </div>

              {/* Shape Color Swatch */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowColorPicker((prev) => !prev)}
                  title="Shape Color"
                  disabled={disabled}
                >
                  <span
                    className="canva-color-preview-pill"
                    style={{
                      backgroundColor:
                        selectedElement.shapeType === "line"
                          ? selectedCfg.strokeColor || "#FFFFFF"
                          : selectedCfg.fillColor || (selectedElement.shapeType === "gradient" ? "#07112a" : "rgba(15, 23, 42, 0.75)"),
                    }}
                  />
                  <span>Color</span>
                  <i className="ti ti-chevron-down" />
                </button>

                {showColorPicker && (
                  <ColorPickerMenu
                    title={
                      selectedElement.shapeType === "line"
                        ? "Line Color"
                        : selectedElement.shapeType === "gradient"
                          ? "Gradient Color"
                          : "Fill Color"
                    }
                    color={
                      selectedElement.shapeType === "line"
                        ? selectedCfg.strokeColor || "#FFFFFF"
                        : selectedCfg.fillColor || (selectedElement.shapeType === "gradient" ? "#07112a" : "#0f172a")
                    }
                    presets={COLOR_PRESETS}
                    onChange={(color) =>
                      updateSelected(
                        selectedElement.shapeType === "line"
                          ? { strokeColor: color }
                          : { fillColor: color }
                      )
                    }
                    onSelectPreset={(color) => {
                      updateSelected(
                        selectedElement.shapeType === "line"
                          ? { strokeColor: color }
                          : { fillColor: color }
                      );
                      setShowColorPicker(false);
                    }}
                  />
                )}
              </div>

              {/* Height / Thickness Stepper with Slider */}
              {selectedElement.shapeType !== "line" ? (
                <div className="canva-stepper-pill" title="Ribbon height as a percentage of the image's shorter side">
                  <span className="canva-stepper-label">Height:</span>
                  <span className="canva-stepper-val">{Math.round((selectedCfg.heightPercent || 14) * 10) / 10}%</span>
                  <input
                    type="range"
                    min={3}
                    max={60}
                    step={0.5}
                    className="canva-mini-slider"
                    value={selectedCfg.heightPercent || 14}
                    onChange={(e) => updateSelected({ heightPercent: parseFloat(e.target.value) })}
                    disabled={disabled}
                  />
                </div>
              ) : (
                <div className="canva-stepper-pill" title="Line thickness as a percentage of the image's shorter side">
                  <span className="canva-stepper-label">Thickness:</span>
                  <span className="canva-stepper-val">{Math.round((selectedCfg.heightPercent || 2) * 10) / 10}%</span>
                  <input
                    type="range"
                    min={0.5}
                    max={6.0}
                    step={0.5}
                    className="canva-mini-slider"
                    value={selectedCfg.heightPercent || 2}
                    onChange={(e) => updateSelected({ heightPercent: parseFloat(e.target.value) })}
                    disabled={disabled}
                  />
                </div>
              )}

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedCfg.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedCfg.opacity ?? 1.0}
                  onChange={(e) => updateSelected({ opacity: parseFloat(e.target.value) })}
                  disabled={disabled}
                />
              </div>

              {/* Position / Align Menu Toggle */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowAlignMenu((prev) => !prev)}
                  title="Position & Alignment"
                  disabled={disabled}
                >
                  <i className="ti ti-layout-align-bottom" />
                  <span>Position</span>
                </button>

                {showAlignMenu && (
                  <div className="canva-floating-menu canva-align-dropdown">
                    {renderCornerAnchorOptions()}
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">
                      Layer Stacking ({selectedIndex + 1} of {elements.length})
                    </div>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "up")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "down")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "top")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "bottom")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                    <div className="canva-dropdown-divider" />
                    <button
                      type="button"
                      onClick={() => {
                        setLayersOpen(true);
                        setShowAlignMenu(false);
                      }}
                    >
                      <i className="ti ti-layers-intersect" /> Open Layers Panel
                    </button>
                  </div>
                )}
              </div>

              {/* Direct Layers Toggle Button */}
              <button
                type="button"
                className={`canva-tool-btn ${layersOpen ? "is-active" : ""}`}
                onClick={() => setLayersOpen((prev) => !prev)}
                title="Manage Layers"
                disabled={disabled}
              >
                <i className="ti ti-layers-intersect" />
                <span>Layers</span>
              </button>

              {/* Delete Button */}
              <button
                type="button"
                className="canva-tool-btn is-danger"
                onClick={removeSelected}
                title="Delete Ribbon"
                disabled={disabled}
              >
                <i className="ti ti-trash" />
              </button>
            </div>
          )}

          {/* C. If Image / Logo is Selected */}
          {selectedElement && selectedElement.type === "image" && selectedCfg && (
            <div className="canva-context-group canva-anim-bar">
              <label className="canva-tool-btn" title="Replace Logo">
                <i className="ti ti-upload" />
                <span>Replace Logo</span>
                <input
                  type="file"
                  accept={ACCEPTED_IMAGE_TYPES}
                  style={{ display: "none" }}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleImageUpload(file);
                    e.target.value = "";
                  }}
                  disabled={disabled}
                />
              </label>

              {institutionLogoUrl && (
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: institutionLogoUrl } : el)))}
                  title="Use Institution Seal"
                  disabled={disabled}
                >
                  <i className="ti ti-building" />
                  <span>Institution Logo</span>
                </button>
              )}

              <button
                type="button"
                className="canva-tool-btn"
                onClick={() => onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: "/dasig-logo.png" } : el)))}
                title="Use Regional DASIG Logo"
                disabled={disabled}
              >
                <i className="ti ti-world" />
                <span>DASIG Logo</span>
              </button>

              {/* Image Size Stepper with Slider */}
              <div className="canva-stepper-pill" title="Photo size as a percentage of the image's shorter side">
                <span className="canva-stepper-label">Size:</span>
                <span className="canva-stepper-val">{Math.round((selectedCfg.widthPercent || 18) * 10) / 10}%</span>
                <input
                  type="range"
                  min={5}
                  max={100}
                  step={1}
                  className="canva-mini-slider"
                  value={selectedCfg.widthPercent || 18}
                  onChange={(e) => setElementSize(parseFloat(e.target.value))}
                  disabled={disabled}
                />
              </div>

              {/* Reset Size Button */}
              <button
                type="button"
                className="canva-tool-btn"
                onClick={() => {
                  setElementSize(18, 18);
                }}
                title="Reset to default logo size (18%)"
                disabled={disabled}
              >
                <i className="ti ti-rotate-clockwise" />
                <span>Reset Size</span>
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedCfg.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedCfg.opacity ?? 1.0}
                  onChange={(e) => updateSelected({ opacity: parseFloat(e.target.value) })}
                  disabled={disabled}
                />
              </div>

              {/* Position / Align Menu Toggle */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowAlignMenu((prev) => !prev)}
                  title="Position & Alignment"
                  disabled={disabled}
                >
                  <i className="ti ti-layout-align-bottom" />
                  <span>Position</span>
                </button>

                {showAlignMenu && (
                  <div className="canva-floating-menu canva-align-dropdown">
                    {renderCornerAnchorOptions()}
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">Size Presets</div>
                    <button type="button" onClick={() => setElementSize(18, 18)}>
                      <i className="ti ti-badge" /> Standard Logo (18%)
                    </button>
                    <button type="button" onClick={() => setElementSize(35, 35)}>
                      <i className="ti ti-aspect-ratio" /> Medium (35%)
                    </button>
                    <button type="button" onClick={() => setElementSize(60, 60)}>
                      <i className="ti ti-maximize" /> Large (60%)
                    </button>
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">
                      Layer Stacking ({selectedIndex + 1} of {elements.length})
                    </div>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "up")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "down")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "top")}
                      disabled={selectedIndex === sortedElements.length - 1}
                    >
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button
                      type="button"
                      onClick={() => moveLayer(selectedElement.id, "bottom")}
                      disabled={selectedIndex === 0}
                    >
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                    <div className="canva-dropdown-divider" />
                    <button
                      type="button"
                      onClick={() => {
                        setLayersOpen(true);
                        setShowAlignMenu(false);
                      }}
                    >
                      <i className="ti ti-layers-intersect" /> Open Layers Panel
                    </button>
                  </div>
                )}
              </div>

              {/* Direct Layers Toggle Button */}
              <button
                type="button"
                className={`canva-tool-btn ${layersOpen ? "is-active" : ""}`}
                onClick={() => setLayersOpen((prev) => !prev)}
                title="Manage Layers"
                disabled={disabled}
              >
                <i className="ti ti-layers-intersect" />
                <span>Layers</span>
              </button>

              {/* Delete Button */}
              <button
                type="button"
                className="canva-tool-btn is-danger"
                onClick={removeSelected}
                title="Delete Logo"
                disabled={disabled}
              >
                <i className="ti ti-trash" />
              </button>
            </div>
          )}

          {/* D. Default General Bar (When nothing is selected) */}
          {!selectedElement && (
            <div className="canva-default-topbar-group canva-anim-bar">
              <span className="canva-topbar-hint">
                <i className="ti ti-wand" /> Select an element to edit or use a plus button on the canvas
              </span>
              <button
                type="button"
                className={`canva-tool-btn ${layersOpen ? "is-active" : ""}`}
                onClick={() => setLayersOpen((prev) => !prev)}
                title="View & Manage Layers"
                disabled={disabled}
              >
                <i className="ti ti-layers-intersect" />
                <span>Layers ({elements.length}/{MAX_WATERMARK_ELEMENTS})</span>
              </button>
            </div>
          )}
        </header>

        {/* 4. Canvas Stage Area */}
        <div
          className="canva-stage-wrap"
          ref={stageRef}
          onClick={(e) => {
            if (
              e.target === e.currentTarget ||
              (e.target as HTMLElement).classList.contains("canva-stage-wrap") ||
              (e.target as HTMLElement).classList.contains("canva-frame-shell")
            ) {
              setSelectedId(null);
            }
          }}
        >
          <div
            className={`canva-frame-shell ${isResizingCanvas ? "is-resizing-canvas" : ""}`}
            style={{
              transform: `scale(${zoom / 100})`,
              transformOrigin: "center center",
              transition: isResizingCanvas ? "none" : "transform 0.12s ease-out",
            }}
          >
            {/* Responsive Preview Resize Handles (DevTools style) */}
            <button
              type="button"
              className="wm-canvas-resize-handle wm-canvas-resize-e"
              onPointerDown={(e) => beginCanvasResize(e, "e")}
              onDoubleClick={handleResetCanvasSize}
              title="Drag to resize canvas width (double-click to reset)"
              aria-label="Resize canvas width"
            >
              <span className="wm-canvas-resize-grip" aria-hidden="true" />
            </button>
            <button
              type="button"
              className="wm-canvas-resize-handle wm-canvas-resize-w"
              onPointerDown={(e) => beginCanvasResize(e, "w")}
              onDoubleClick={handleResetCanvasSize}
              title="Drag to resize canvas width (double-click to reset)"
              aria-label="Resize canvas width"
            >
              <span className="wm-canvas-resize-grip" aria-hidden="true" />
            </button>
            <button
              type="button"
              className="wm-canvas-resize-handle wm-canvas-resize-s"
              onPointerDown={(e) => beginCanvasResize(e, "s")}
              onDoubleClick={handleResetCanvasSize}
              title="Drag to resize canvas height (double-click to reset)"
              aria-label="Resize canvas height"
            >
              <span className="wm-canvas-resize-grip" aria-hidden="true" />
            </button>
            <button
              type="button"
              className="wm-canvas-resize-handle wm-canvas-resize-n"
              onPointerDown={(e) => beginCanvasResize(e, "n")}
              onDoubleClick={handleResetCanvasSize}
              title="Drag to resize canvas height (double-click to reset)"
              aria-label="Resize canvas height"
            >
              <span className="wm-canvas-resize-grip" aria-hidden="true" />
            </button>
            <button
              type="button"
              className="wm-canvas-resize-handle wm-canvas-resize-se"
              onPointerDown={(e) => beginCanvasResize(e, "se")}
              onDoubleClick={handleResetCanvasSize}
              title="Drag to resize canvas width and height (double-click to reset)"
              aria-label="Resize canvas width and height"
            >
              <span className="wm-canvas-resize-corner-grip" aria-hidden="true" />
            </button>

            {isResizingCanvas && (
              <div className="wm-canvas-size-hud" aria-live="polite">
                {measuredCanvasSize.width} &times; {measuredCanvasSize.height} px
              </div>
            )}

          <div
            className={`canva-frame wm-bg-${background} ${canvasSize ? "has-custom-size" : ""} ${isResizingCanvas ? "is-resizing-canvas" : ""}`}
            ref={canvasRef}
            style={
              canvasSize
                ? {
                    width: `${canvasSize.width}px`,
                    height: `${canvasSize.height}px`,
                    aspectRatio: "unset",
                  }
                : undefined
            }
            onClick={(e) => {
              if (
                e.target === canvasRef.current ||
                (e.target as HTMLElement).classList.contains("wm-canvas-sample-image")
              ) {
                setSelectedId(null);
              }
            }}
          >
            {background === "sample-photo" && (
              <img className="wm-canvas-sample-image" src={watermarkSample} alt="" draggable={false} />
            )}

            <input
              ref={anchorImageInputRef}
              type="file"
              accept={ACCEPTED_IMAGE_TYPES}
              className="wm-anchor-image-input"
              onChange={(event) => {
                const file = event.target.files?.[0];
                const anchor = pendingImageAnchorRef.current;
                pendingImageAnchorRef.current = null;
                if (file && anchor) handleImageUpload(file, anchor);
                event.target.value = "";
              }}
            />

            {CORNER_ANCHORS.map((anchor) => {
              const occupied = elements.some((el) => normalizeWatermarkAnchor(el).anchor === anchor);
              if (occupied) return null;
              return (
                <div
                  key={anchor}
                  className={`wm-anchor-control wm-corner-anchor wm-corner-anchor-${anchor} ${openAnchorMenu === anchor ? "is-open" : ""}`}
                  onPointerDown={(event) => event.stopPropagation()}
                >
                  <button
                    type="button"
                    className="wm-anchor-add"
                    aria-label={`Add photo or text at ${anchor.replace("-", " ")}`}
                    aria-expanded={openAnchorMenu === anchor}
                    onClick={() => setOpenAnchorMenu((current) => current === anchor ? null : anchor)}
                    disabled={disabled || elements.length >= MAX_WATERMARK_ELEMENTS}
                  >
                    <i className="ti ti-plus" aria-hidden="true" />
                  </button>
                  {openAnchorMenu === anchor && (
                    <div className={`wm-anchor-menu wm-anchor-menu-${anchor}`} role="menu">
                      <strong>Add at {anchor.replace("-", " ")}</strong>
                      <button type="button" role="menuitem" onClick={() => openAnchorImagePicker(anchor)}>
                        <i className="ti ti-photo-plus" /> Upload photo
                      </button>
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() => addElement("image", institutionLogoUrl || "/dasig-logo.png", anchor)}
                      >
                        <i className="ti ti-brand-asana" /> Use DASIG logo
                      </button>
                      <button
                        type="button"
                        role="menuitem"
                        onClick={() => addElement(
                          "text",
                          institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect",
                          anchor,
                        )}
                      >
                        <i className="ti ti-typography" /> Add text
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            {EDGE_ANCHORS.map((anchor) => {
              const occupied = elements.some((el) => normalizeWatermarkAnchor(el).anchor === anchor);
              if (occupied) return null;
              return (
                <div
                  key={anchor}
                  className={`wm-anchor-control wm-edge-anchor wm-edge-anchor-${anchor} ${openAnchorMenu === anchor ? "is-open" : ""}`}
                  onPointerDown={(event) => event.stopPropagation()}
                >
                  <button
                    type="button"
                    className="wm-anchor-add"
                    aria-label={`Add ribbon or gradient at ${anchor} edge`}
                    aria-expanded={openAnchorMenu === anchor}
                    onClick={() => setOpenAnchorMenu((current) => current === anchor ? null : anchor)}
                    disabled={disabled || elements.length >= MAX_WATERMARK_ELEMENTS}
                  >
                    <i className="ti ti-plus" aria-hidden="true" />
                  </button>
                  {openAnchorMenu === anchor && (
                    <div className={`wm-anchor-menu wm-edge-menu-${anchor}`} role="menu">
                      <strong>Add to {anchor} edge</strong>
                      <button type="button" role="menuitem" onClick={() => addElement("shape", "rectangle", anchor)}>
                        <i className="ti ti-rectangle" /> Ribbon
                      </button>
                      <button type="button" role="menuitem" onClick={() => addElement("shape", "gradient", anchor)}>
                        <i className="ti ti-layers-subtract" /> Gradient
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            {/* Rendered Canvas Elements */}
            {sortedElements.map((el, index) => {
              const isSelected = el.id === selectedId;
              const elCfg = resolveRatioConfig(el, aspectRatio, index);
              const isHidden = !!elCfg.hidden;
              const box = computeBox(el, aspectRatio, canvasAspect);
              const pillPlacement = getFloatingPillPlacement(box);

              if (isHidden) return null;

              return (
                <div
                  key={el.id}
                  className={`wm-canvas-element wm-type-${el.type} ${isSelected ? "is-selected" : ""}`}
                  style={{
                    left: `${box.left}%`,
                    top: `${box.top}%`,
                    width: `${box.width}%`,
                    height: `${box.height}%`,
                    zIndex: 10 + index * 5 + (isSelected ? 2 : 0),
                  }}
                  onClick={(event) => {
                    event.stopPropagation();
                    setSelectedId(el.id);
                    if (editingTextId && editingTextId !== el.id) {
                      setEditingTextId(null);
                    }
                  }}
                  onDoubleClick={(event) => {
                    event.stopPropagation();
                    if (el.type === "text" && !disabled) {
                      setSelectedId(el.id);
                      setEditingTextId(el.id);
                    }
                  }}
                >
                  {/* Element Content */}
                  {el.type === "text" && editingTextId === el.id && !disabled ? (
                    <textarea
                      autoFocus
                      rows={Math.max(1, (el.text || "").split("\n").length)}
                      className="wm-element-content wm-text-content wm-text-inline-input"
                      value={el.text || ""}
                      onChange={(e) => {
                        const val = e.target.value;
                        const fontPercent = elCfg.fontSizePercent || 3.2;
                        const calculatedHeight = fitTextHeight(val, fontPercent);
                        onChange(
                          elements.map((item) =>
                            item.id === el.id
                              ? {
                                  ...updateElementForRatio(item, aspectRatio, { heightPercent: calculatedHeight }),
                                  text: val,
                                }
                              : item
                          )
                        );
                      }}
                      onFocus={(e) => e.target.select()}
                      onBlur={() => setEditingTextId(null)}
                      onKeyDown={(e) => {
                        if (e.key === "Escape") {
                          setEditingTextId(null);
                        }
                      }}
                      onPointerDown={(e) => e.stopPropagation()}
                      onMouseDown={(e) => e.stopPropagation()}
                      onClick={(e) => e.stopPropagation()}
                      style={{
                        ...textElementStyle(el, elCfg),
                        opacity: elCfg.opacity,
                      }}
                    />
                  ) : (
                    <div style={{ width: "100%", height: "100%", opacity: elCfg.opacity }}>
                      <WatermarkElementContent el={el} ratio={aspectRatio} />
                    </div>
                  )}

                  {/* 8-Point Selection Handles & Mini Action Bar */}
                  {isSelected && !disabled && (
                    <>
                      <div
                        className="wm-element-handles"
                        onClick={(e) => e.stopPropagation()}
                        onPointerDown={(e) => e.stopPropagation()}
                      >
                        {el.type === "shape" ? (
                          normalizeWatermarkAnchor(el).anchor === "top" ? (
                            <span className="wm-handle handle-s" onPointerDown={(e) => interaction.beginResize(e, el.id, "s")} title="Adjust Height" />
                          ) : (
                            <span className="wm-handle handle-n" onPointerDown={(e) => interaction.beginResize(e, el.id, "n")} title="Adjust Height" />
                          )
                        ) : (
                          <>
                            <span className="wm-handle handle-nw" onPointerDown={(e) => interaction.beginResize(e, el.id, "nw")} title="Resize" />
                            <span className="wm-handle handle-n" onPointerDown={(e) => interaction.beginResize(e, el.id, "n")} title="Adjust Height" />
                            <span className="wm-handle handle-ne" onPointerDown={(e) => interaction.beginResize(e, el.id, "ne")} title="Resize" />
                            <span className="wm-handle handle-e" onPointerDown={(e) => interaction.beginResize(e, el.id, "e")} title="Adjust Width" />
                            <span className="wm-handle handle-se" onPointerDown={(e) => interaction.beginResize(e, el.id, "se")} title="Resize" />
                            <span className="wm-handle handle-s" onPointerDown={(e) => interaction.beginResize(e, el.id, "s")} title="Adjust Height" />
                            <span className="wm-handle handle-sw" onPointerDown={(e) => interaction.beginResize(e, el.id, "sw")} title="Resize" />
                            <span className="wm-handle handle-w" onPointerDown={(e) => interaction.beginResize(e, el.id, "w")} title="Adjust Width" />
                          </>
                        )}
                      </div>

                      {/* Mini Floating Action Tooltip */}
                      <div
                        className={`canva-floating-pill-bar ${pillPlacement}`}
                        onClick={(e) => e.stopPropagation()}
                        onPointerDown={(e) => e.stopPropagation()}
                      >
                        <button
                          type="button"
                          className="canva-pill-action-btn"
                          onClick={() => {
                            if (selectedIndex < sortedElements.length - 1) moveLayer(el.id, "up");
                            else moveLayer(el.id, "bottom");
                          }}
                          title="Cycle Layer Order (Forward / Backward)"
                        >
                          <i className="ti ti-stack-2" />
                        </button>
                        <button
                          type="button"
                          className="canva-pill-action-btn is-del"
                          onClick={removeSelected}
                          title="Delete Element"
                        >
                          <i className="ti ti-trash" />
                        </button>
                      </div>
                    </>
                  )}
                </div>
              );
            })}

          </div>
          </div>
        </div>

        {/* 5. Persistent Bottom Controls: Preview & Zoom */}
        <footer className="canva-bottom-bar">
          <div className="canva-pill-segment">
            <span className="canva-segment-label">Preview:</span>
            <button
              type="button"
              className={`canva-segment-item ${background === "sample-photo" ? "is-active" : ""}`}
              onClick={() => setBackground("sample-photo")}
              title="Show the generated sample photo"
            >
              <i className="ti ti-photo" /> Sample image
            </button>
            <button
              type="button"
              className={`canva-segment-item ${background === "checkerboard" ? "is-active" : ""}`}
              onClick={() => setBackground("checkerboard")}
              title="Show transparent background"
            >
              <i className="ti ti-grid-dots" /> Transparent
            </button>
            <span className="canva-canvas-size-pill" title="Drag the canvas edges or corner to preview different image dimensions">
              <i className="ti ti-dimensions" />
              <span>
                {measuredCanvasSize.width} &times; {measuredCanvasSize.height}
              </span>
              {canvasSize && (
                <button
                  type="button"
                  className="canva-canvas-size-reset"
                  onClick={handleResetCanvasSize}
                  title="Reset canvas to 1:1 square"
                  aria-label="Reset canvas size"
                >
                  <i className="ti ti-rotate-clockwise" />
                </button>
              )}
            </span>
          </div>

          {/* Zoom In / Zoom Out Controls */}
          <div className="canva-pill-segment canva-zoom-segment">
            <span className="canva-segment-label">Zoom:</span>
            <button
              type="button"
              className="canva-zoom-btn"
              onClick={handleZoomOut}
              disabled={zoom <= 50}
              title="Zoom Out (Ctrl -)"
              aria-label="Zoom out"
            >
              <i className="ti ti-minus" />
            </button>
            <button
              type="button"
              className="canva-zoom-val-btn"
              onClick={handleResetZoom}
              title="Click to Reset Zoom to 100%"
            >
              {zoom}%
            </button>
            <button
              type="button"
              className="canva-zoom-btn"
              onClick={handleZoomIn}
              disabled={zoom >= 200}
              title="Zoom In (Ctrl +)"
              aria-label="Zoom in"
            >
              <i className="ti ti-plus" />
            </button>
            {zoom !== 100 && (
              <button
                type="button"
                className="canva-zoom-fit-btn"
                onClick={handleResetZoom}
                title="Reset Zoom to 100%"
              >
                Fit
              </button>
            )}
          </div>
        </footer>
      </main>
      </div>
    </div>
  );
}
