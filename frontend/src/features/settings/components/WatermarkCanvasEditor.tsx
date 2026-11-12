import { useState, useRef, useEffect } from "react";
import type {
  AspectRatioType,
  WatermarkAnchor,
  WatermarkElement,
  WatermarkElementType,
  ElementRatioConfig,
} from "../../../types/watermark.types";
import {
  DEFAULT_ANCHOR_INSET_PERCENT,
  computeBox,
  applyBox,
  snapBox,
  fitTextHeight,
  aspectOf,
  resolveRatioConfig,
  updateElementForRatio,
  getSortedElementsByRatio,
  reorderElementsByRatio,
  reorderElementsByDrag,
  type ElementBox,
  type HorizontalAnchor,
  type VerticalAnchor,
  FULL_WIDTH_PERCENT,
  MAX_WATERMARK_ELEMENTS,
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

type SideTab = "shapes" | "text" | "uploads" | "layers" | null;

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
  const [aspectRatio, setAspectRatio] = useState<AspectRatioType>("1:1");
  const [background, setBackground] = useState<CanvasBackground>("checkerboard");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<SideTab>(null);
  const [showColorPicker, setShowColorPicker] = useState(false);
  const [showFontPicker, setShowFontPicker] = useState(false);
  const [showAlignMenu, setShowAlignMenu] = useState(false);
  const [showInfo, setShowInfo] = useState(false);
  const [editingTextId, setEditingTextId] = useState<string | null>(null);
  const [draggedLayerId, setDraggedLayerId] = useState<string | null>(null);
  const [dragOverLayerId, setDragOverLayerId] = useState<string | null>(null);
  const [zoom, setZoom] = useState<number>(100);

  const canvasRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLDivElement>(null);

  function handleZoomIn() {
    setZoom((prev) => Math.min(200, prev + 10));
  }

  function handleZoomOut() {
    setZoom((prev) => Math.max(50, prev - 10));
  }

  function handleResetZoom() {
    setZoom(100);
  }

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
      if (!target.closest(".canva-rail-bottom")) {
        setShowInfo(false);
      }
    }
    document.addEventListener("mousedown", handleDocClick);
    return () => document.removeEventListener("mousedown", handleDocClick);
  }, []);

  // Add Element Handlers
  function addElement(type: WatermarkElementType, initialVariant?: string) {
    if (elements.length >= MAX_WATERMARK_ELEMENTS || disabled) return;

    let newElement: WatermarkElement;

    if (type === "image") {
      newElement = createImageElement(initialVariant || institutionLogoUrl || "/dasig-logo.png");
    } else if (type === "text") {
      const defaultText = initialVariant || (institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect");
      newElement = createTextElement(defaultText);
    } else {
      const isLine = initialVariant === "line";
      const isGradient = initialVariant === "gradient";
      newElement = createShapeElement(isLine ? "line" : isGradient ? "gradient" : "rectangle");
    }

    const updated = [...elements, newElement];
    onChange(updated);
    setSelectedId(newElement.id);
    setActiveTab(null);
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

  function alignElement(alignment: "bottom-right" | "bottom-left" | "top-right" | "top-left" | "bottom-center" | "top-center" | "bottom-bar" | "top-bar" | "center") {
    if (!selectedElement || disabled) return;

    const currentBox = computeBox(selectedElement, aspectRatio);
    const cornerAnchor = (["top-left", "top-right", "bottom-left", "bottom-right"] as const)
      .find((anchor) => anchor === alignment);
    if (cornerAnchor) {
      const anchor: WatermarkAnchor = cornerAnchor;
      const updated = elements.map((el) => {
        if (el.id !== selectedElement.id) return el;
        const ratios = el.ratios ? { ...el.ratios } : undefined;
        if (ratios) {
          for (const ratio of ["1:1", "4:3", "16:9"] as const) {
            const config = ratios[ratio];
            if (config) {
              ratios[ratio] = {
                ...config,
                xPercent: DEFAULT_ANCHOR_INSET_PERCENT,
                yPercent: DEFAULT_ANCHOR_INSET_PERCENT,
              };
            }
          }
        }
        return {
          ...el,
          anchor,
          xPercent: DEFAULT_ANCHOR_INSET_PERCENT,
          yPercent: DEFAULT_ANCHOR_INSET_PERCENT,
          ratios,
        };
      });
      onChange(updated);
      setShowAlignMenu(false);
      return;
    }

    const selectedIndex = elements.findIndex((el) => el.id === selectedElement.id);
    const freeformRatios = { ...selectedElement.ratios };
    const freeformElement: WatermarkElement = {
      ...selectedElement,
      anchor: "freeform",
      ratios: freeformRatios,
    };
    for (const ratio of ["1:1", "4:3", "16:9"] as const) {
      const box = computeBox(selectedElement, ratio);
      const config = resolveRatioConfig(selectedElement, ratio, selectedIndex);
      freeformRatios[ratio] = {
        ...config,
        xPercent: box.left,
        yPercent: box.top,
      };
    }
    const squareConfig = freeformRatios["1:1"];
    if (squareConfig) {
      freeformElement.xPercent = squareConfig.xPercent;
      freeformElement.yPercent = squareConfig.yPercent;
    }

    if (alignment === "bottom-bar") {
      const newBox: ElementBox = {
        left: 0,
        top: Math.max(0, 100 - currentBox.height),
        width: 100,
        height: currentBox.height,
      };
      onChange(elements.map((el) =>
        el.id === selectedElement.id ? applyBox(freeformElement, aspectRatio, newBox) : el
      ));
      setShowAlignMenu(false);
      return;
    }

    if (alignment === "top-bar") {
      const newBox: ElementBox = {
        left: 0,
        top: 0,
        width: 100,
        height: currentBox.height,
      };
      onChange(elements.map((el) =>
        el.id === selectedElement.id ? applyBox(freeformElement, aspectRatio, newBox) : el
      ));
      setShowAlignMenu(false);
      return;
    }

    let h: HorizontalAnchor = "center";
    let v: VerticalAnchor = "middle";
    if (alignment.includes("left")) h = "left";
    else if (alignment.includes("right")) h = "right";

    if (alignment.includes("top")) v = "top";
    else if (alignment.includes("bottom")) v = "bottom";

    const snapped = snapBox(currentBox, h, v);
    onChange(elements.map((el) =>
      el.id === selectedElement.id ? applyBox(freeformElement, aspectRatio, snapped) : el
    ));
    setShowAlignMenu(false);
  }

  function renderCornerAnchorOptions() {
    const options: Array<{ anchor: Exclude<WatermarkAnchor, "freeform">; label: string }> = [
      { anchor: "top-left", label: "Top Left" },
      { anchor: "top-right", label: "Top Right" },
      { anchor: "bottom-left", label: "Bottom Left" },
      { anchor: "bottom-right", label: "Bottom Right" },
    ];

    return (
      <>
        <div className="canva-dropdown-title">Anchor to a corner (all photo sizes)</div>
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
        <p className="canva-anchor-hint">Drag the element to fine-tune its inset.</p>
      </>
    );
  }

  function setElementSize(widthPercent: number, heightPercent?: number) {
    if (!selectedElement || disabled) return;
    const currentCfg = resolveRatioConfig(selectedElement, aspectRatio);
    const currentW = currentCfg.widthPercent || 18;
    const currentH = currentCfg.heightPercent || 18;
    const aspect = currentW > 0 ? currentH / currentW : 1;
    const targetH = heightPercent !== undefined ? heightPercent : Math.min(100, Math.round(widthPercent * aspect * 10) / 10);

    const canvasAspect = aspectOf(aspectRatio);
    const boxW = Math.min(widthPercent / canvasAspect, 100);
    const boxH = Math.min(targetH, 100);
    const currentBox = computeBox(selectedElement, aspectRatio);

    const newBox: ElementBox = {
      left: Math.max(0, Math.min(currentBox.left, 100 - boxW)),
      top: Math.max(0, Math.min(currentBox.top, 100 - boxH)),
      width: boxW,
      height: boxH,
    };
    onChange(elements.map((el) => (el.id === selectedElement.id ? applyBox(el, aspectRatio, newBox, canvasAspect) : el)));
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

  function handleImageUpload(file: File) {
    if (!file || !file.type.startsWith("image/")) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        if (selectedElement && selectedElement.type === "image") {
          onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: reader.result as string } : el)));
        } else {
          addElement("image", reader.result);
        }
      }
    };
    reader.readAsDataURL(file);
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
    const isDockedLeft = box.left < 10;
    const isDockedRight = box.left + box.width > 88;

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
          <div className="canva-header-ratio-badge" title={`Active canvas format is ${aspectRatio}`}>
            <i className="ti ti-aspect-ratio" aria-hidden="true" />
            <span>Format: <strong>{aspectRatio}</strong></span>
          </div>
          {onSave && (
            <button
              type="button"
              className="canva-header-save-btn"
              disabled={disabled || isSaving}
              aria-busy={isSaving}
              onClick={onSave}
              title="Save watermark changes across all ratios"
            >
              <i className={isSaving ? "ti ti-loader-2 canva-spinner" : "ti ti-device-floppy"} aria-hidden="true" />
              <span>{isSaving ? "Saving..." : "Save"}</span>
            </button>
          )}
        </div>
      </header>

      {/* 1. Main Studio Layout (Left Rail + Drawer + Center Workspace) */}
      <div className="canva-studio-main">
        {/* Left Vertical Navigation Rail (Blue & White) */}
        <nav className="canva-rail" aria-label="Canva Design Tools">
        <div className="canva-rail-top">
          <button
            type="button"
            className={`canva-rail-btn ${activeTab === "shapes" ? "is-active" : ""}`}
            onClick={() => setActiveTab((prev) => (prev === "shapes" ? null : "shapes"))}
            title="Add Shapes & Ribbons"
          >
            <i className="ti ti-rectangle"></i>
            <span>Shape</span>
          </button>

          <button
            type="button"
            className={`canva-rail-btn ${activeTab === "text" ? "is-active" : ""}`}
            onClick={() => setActiveTab((prev) => (prev === "text" ? null : "text"))}
            title="Add Text & Handle"
          >
            <i className="ti ti-typography"></i>
            <span>Text</span>
          </button>

          <button
            type="button"
            className={`canva-rail-btn ${activeTab === "uploads" ? "is-active" : ""}`}
            onClick={() => setActiveTab((prev) => (prev === "uploads" ? null : "uploads"))}
            title="Upload / Select Logo"
          >
            <i className="ti ti-upload"></i>
            <span>Uploads</span>
          </button>

          <button
            type="button"
            className={`canva-rail-btn ${activeTab === "layers" ? "is-active" : ""}`}
            onClick={() => setActiveTab((prev) => (prev === "layers" ? null : "layers"))}
            title="View & Manage Layers"
          >
            <div className="canva-rail-icon-wrap">
              <i className="ti ti-layers-intersect"></i>
              {elements.length > 0 && <span className="canva-rail-badge">{elements.length}</span>}
            </div>
            <span>Layers</span>
          </button>
        </div>

        {/* Info button at the bottom of rail */}
        <div className="canva-rail-bottom">
          <button
            type="button"
            className={`canva-rail-icon-only-btn ${showInfo ? "is-active" : ""}`}
            onClick={() => setShowInfo((prev) => !prev)}
            title="Watermark Studio Info & Tips"
          >
            <i className="ti ti-info-circle"></i>
          </button>

          {showInfo && (
            <div className="canva-info-popover">
              <div className="canva-info-header">
                <strong>
                  <i className="ti ti-bulb" /> Watermark Tips
                </strong>
                <button type="button" onClick={() => setShowInfo(false)}>
                  <i className="ti ti-x" />
                </button>
              </div>
              <ul className="canva-info-list">
                <li>
                  <strong>Add Elements:</strong> Use <em>Shape</em>, <em>Text</em>, or <em>Uploads</em> (up to 3).
                </li>
                <li>
                  <strong>Multi-Ratio:</strong> Elements stay the same across 1:1, 4:3, and 16:9, but each ratio saves its own layering, positioning, size, colors, and transparency!
                </li>
                <li>
                  <strong>Customize:</strong> Click any element to adjust its properties for the current format.
                </li>
                <li>
                  <strong>Drag & Resize:</strong> Move freely or drag handles to scale.
                </li>
              </ul>
            </div>
          )}
        </div>
      </nav>

      {/* 2. Floating Overlay Flyout Asset Drawer */}
      <aside className={`canva-drawer ${activeTab ? "is-open" : "is-closed"}`}>
        {activeTab && (
          <>
            <div className="canva-drawer-header">
              <h3>
                {activeTab === "shapes" && "Shapes & Ribbons"}
                {activeTab === "text" && "Text & Handles"}
                {activeTab === "uploads" && "Brand Logos & Assets"}
                {activeTab === "layers" && `Active Layers (${elements.length}/3)`}
              </h3>
              <button
                type="button"
                className="canva-drawer-close"
                onClick={() => setActiveTab(null)}
                title="Close drawer"
              >
                <i className="ti ti-x"></i>
              </button>
            </div>

            <div className="canva-drawer-content">
              {/* Drawer Tab 1: Shapes */}
              {activeTab === "shapes" && (
                <div className="canva-drawer-section canva-anim-fade">
                  <p className="canva-drawer-desc">Add solid ribbons, gradient scrims, or divider lines to highlight text.</p>
                  <div className="canva-item-card-grid">
                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("shape", "rectangle")}
                      disabled={elements.length >= MAX_WATERMARK_ELEMENTS || disabled}
                    >
                      <div className="canva-item-preview shape-ribbon-preview">
                        <div className="mini-ribbon" />
                      </div>
                      <div className="canva-item-meta">
                        <strong>Background Ribbon</strong>
                        <span>Bottom or top solid branding banner</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>

                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("shape", "gradient")}
                      disabled={elements.length >= MAX_WATERMARK_ELEMENTS || disabled}
                    >
                      <div className="canva-item-preview shape-gradient-preview">
                        <div className="mini-gradient" />
                      </div>
                      <div className="canva-item-meta">
                        <strong>Gradient Scrim</strong>
                        <span>Smooth bottom photo fade</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>

                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("shape", "line")}
                      disabled={elements.length >= MAX_WATERMARK_ELEMENTS || disabled}
                    >
                      <div className="canva-item-preview shape-line-preview">
                        <div className="mini-line" />
                      </div>
                      <div className="canva-item-meta">
                        <strong>Divider Line</strong>
                        <span>Clean separator line</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>
                  </div>
                </div>
              )}

              {/* Drawer Tab 2: Text */}
              {activeTab === "text" && (
                <div className="canva-drawer-section canva-anim-fade">
                  <p className="canva-drawer-desc">Insert handle, hashtag, or official organization watermark text.</p>
                  <div className="canva-item-card-grid">
                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("text", institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect")}
                      disabled={elements.length >= MAX_WATERMARK_ELEMENTS || disabled}
                    >
                      <div className="canva-item-preview text-handle-preview">
                        <span className="mini-text-bold">@Handle</span>
                      </div>
                      <div className="canva-item-meta">
                        <strong>Official Handle</strong>
                        <span>{institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect"}</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>

                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("text", "#DASIGCentralVisayas")}
                      disabled={elements.length >= MAX_WATERMARK_ELEMENTS || disabled}
                    >
                      <div className="canva-item-preview text-hashtag-preview">
                        <span className="mini-text-regular">#Hashtag</span>
                      </div>
                      <div className="canva-item-meta">
                        <strong>Campaign Hashtag</strong>
                        <span>#DASIGCentralVisayas</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>
                  </div>
                </div>
              )}

              {/* Drawer Tab 3: Uploads */}
              {activeTab === "uploads" && (
                <div className="canva-drawer-section canva-anim-fade">
                  <label className="canva-upload-zone">
                    <i className="ti ti-cloud-upload"></i>
                    <span>Upload Brand Logo</span>
                    <small>PNG, SVG, JPG, or WEBP</small>
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

                  <div className="canva-brand-presets">
                    <div className="canva-brand-title">Preset Brand Logos</div>
                    <div className="canva-item-card-grid">
                      {institutionLogoUrl && (
                        <button
                          type="button"
                          className="canva-item-card"
                          onClick={() => {
                            if (selectedElement && selectedElement.type === "image") {
                              onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: institutionLogoUrl } : el)));
                            } else {
                              addElement("image", institutionLogoUrl);
                            }
                          }}
                          disabled={(elements.length >= MAX_WATERMARK_ELEMENTS && (!selectedElement || selectedElement.type !== "image")) || disabled}
                        >
                          <div className="canva-item-preview">
                            <img src={institutionLogoUrl} alt="Institution Logo" />
                          </div>
                          <div className="canva-item-meta">
                            <strong>{institutionName || "Institution"} Logo</strong>
                            <span>Official seal</span>
                          </div>
                          <i className="ti ti-plus canva-add-icon"></i>
                        </button>
                      )}

                      <button
                        type="button"
                        className="canva-item-card"
                        onClick={() => {
                          if (selectedElement && selectedElement.type === "image") {
                            onChange(elements.map((el) => (el.id === selectedElement.id ? { ...el, imageUrl: "/dasig-logo.png" } : el)));
                          } else {
                            addElement("image", "/dasig-logo.png");
                          }
                        }}
                        disabled={(elements.length >= MAX_WATERMARK_ELEMENTS && (!selectedElement || selectedElement.type !== "image")) || disabled}
                      >
                        <div className="canva-item-preview">
                          <img src="/dasig-logo.png" alt="DASIG Logo" />
                        </div>
                        <div className="canva-item-meta">
                          <strong>DASIG Main Logo</strong>
                          <span>Regional network icon</span>
                        </div>
                        <i className="ti ti-plus canva-add-icon"></i>
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {/* Drawer Tab 4: Layers */}
              {activeTab === "layers" && (
                <div className="canva-drawer-section canva-anim-fade">
                  <p className="canva-drawer-desc">
                    Reorder layers, toggle visibility, or select covered elements for <strong>{aspectRatio}</strong>.
                  </p>
                  {elements.length === 0 ? (
                    <div className="canva-empty-layers">
                      <i className="ti ti-layers-subtract"></i>
                      <p>No elements added yet. Click Shape, Text, or Uploads on the left to add a layer.</p>
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
                                <span className={`canva-layer-badge ${isTop ? "is-top" : isBottom ? "is-bottom" : ""}`}>
                                  {isTop ? "Front" : isBottom ? "Back" : "Middle"}
                                </span>
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
              )}
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
              <div className="canva-stepper-pill" title={`Font Size (% of canvas height in ${aspectRatio})`}>
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
                  title={`Text Color (${aspectRatio})`}
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
                  <div className="canva-floating-menu canva-color-dropdown">
                    <div className="canva-dropdown-title">Text Color ({aspectRatio})</div>
                    <div className="canva-color-grid">
                      {COLOR_PRESETS.map((color) => (
                        <button
                          key={color}
                          type="button"
                          className={`canva-color-dot ${(selectedCfg.textColor || "#FFFFFF").toLowerCase() === color.toLowerCase() ? "is-selected" : ""}`}
                          style={{ backgroundColor: color }}
                          onClick={() => {
                            updateSelected({ textColor: color });
                            setShowColorPicker(false);
                          }}
                        />
                      ))}
                    </div>
                    <div className="canva-custom-color-row">
                      <label htmlFor="custom-text-color">Custom:</label>
                      <input
                        id="custom-text-color"
                        type="color"
                        value={selectedCfg.textColor?.startsWith("#") ? selectedCfg.textColor : "#FFFFFF"}
                        onChange={(e) => updateSelected({ textColor: e.target.value })}
                      />
                    </div>
                  </div>
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

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title={`Transparency / Opacity (${aspectRatio})`}>
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
                    <div className="canva-dropdown-title">Freeform placement ({aspectRatio})</div>
                    <button type="button" onClick={() => alignElement("bottom-center")}>
                      <i className="ti ti-layout-align-bottom" /> Bottom-Center
                    </button>
                    <button type="button" onClick={() => alignElement("top-center")}>
                      <i className="ti ti-layout-align-top" /> Top-Center
                    </button>
                    <button type="button" onClick={() => alignElement("center")}>
                      <i className="ti ti-layout-align-middle" /> Center
                    </button>
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">
                      Layer Stacking in {aspectRatio} ({selectedIndex + 1} of {elements.length})
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
                        setActiveTab("layers");
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
                className={`canva-tool-btn ${activeTab === "layers" ? "is-active" : ""}`}
                onClick={() => setActiveTab((prev) => (prev === "layers" ? null : "layers"))}
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
                <button
                  type="button"
                  className={`canva-segment-item ${selectedElement.shapeType === "line" ? "is-active" : ""}`}
                  onClick={() => updateSelectedShapeVariant("line")}
                  disabled={disabled}
                >
                  Line
                </button>
              </div>

              {/* Shape Color Swatch */}
              <div className="canva-popover-anchor">
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => setShowColorPicker((prev) => !prev)}
                  title={`Shape Color (${aspectRatio})`}
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
                  <div className="canva-floating-menu canva-color-dropdown">
                    <div className="canva-dropdown-title">
                      {selectedElement.shapeType === "line"
                        ? `Line Color (${aspectRatio})`
                        : selectedElement.shapeType === "gradient"
                          ? `Gradient Color (${aspectRatio})`
                          : `Fill Color (${aspectRatio})`}
                    </div>
                    <div className="canva-color-grid">
                      {COLOR_PRESETS.map((color) => {
                        const activeColor =
                          selectedElement.shapeType === "line"
                            ? selectedCfg.strokeColor || "#FFFFFF"
                            : selectedCfg.fillColor || (selectedElement.shapeType === "gradient" ? "#07112a" : "rgba(15, 23, 42, 0.75)");
                        return (
                          <button
                            key={color}
                            type="button"
                            className={`canva-color-dot ${activeColor.toLowerCase() === color.toLowerCase() ? "is-selected" : ""}`}
                            style={{ backgroundColor: color }}
                            onClick={() => {
                              updateSelected(
                                selectedElement.shapeType === "line"
                                  ? { strokeColor: color }
                                  : { fillColor: color }
                              );
                              setShowColorPicker(false);
                            }}
                          />
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>

              {/* Height / Thickness Stepper with Slider */}
              {selectedElement.shapeType !== "line" ? (
                <div className="canva-stepper-pill" title={`Height in ${aspectRatio}`}>
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
                <div className="canva-stepper-pill" title={`Thickness in ${aspectRatio}`}>
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

              {/* Full Width Toggle */}
              <button
                type="button"
                className={`canva-tool-btn ${selectedCfg.widthPercent >= FULL_WIDTH_PERCENT ? "is-active" : ""}`}
                onClick={() => updateSelected({ widthPercent: FULL_WIDTH_PERCENT })}
                title="Extend width to fill canvas in all formats"
                disabled={disabled}
              >
                <i className="ti ti-arrows-horizontal" />
                <span>Full Width</span>
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title={`Transparency / Opacity (${aspectRatio})`}>
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
                    <div className="canva-dropdown-title">Freeform placement ({aspectRatio})</div>
                    <button type="button" onClick={() => alignElement("bottom-bar")}>
                      <i className="ti ti-layout-align-bottom" /> Full Bottom Bar
                    </button>
                    <button type="button" onClick={() => alignElement("top-bar")}>
                      <i className="ti ti-layout-align-top" /> Full Top Bar
                    </button>
                    <button type="button" onClick={() => alignElement("center")}>
                      <i className="ti ti-layout-align-middle" /> Centered Bar
                    </button>
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">
                      Layer Stacking in {aspectRatio} ({selectedIndex + 1} of {elements.length})
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
                        setActiveTab("layers");
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
                className={`canva-tool-btn ${activeTab === "layers" ? "is-active" : ""}`}
                onClick={() => setActiveTab((prev) => (prev === "layers" ? null : "layers"))}
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
              <div className="canva-stepper-pill" title={`Logo / Image Size in ${aspectRatio}`}>
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
                  alignElement("bottom-right");
                }}
                title="Reset to default logo size (18%) and position"
                disabled={disabled}
              >
                <i className="ti ti-rotate-clockwise" />
                <span>Reset Size</span>
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title={`Transparency / Opacity (${aspectRatio})`}>
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
                    <div className="canva-dropdown-title">Freeform placement ({aspectRatio})</div>
                    <button type="button" onClick={() => alignElement("bottom-center")}>
                      <i className="ti ti-layout-align-bottom" /> Bottom-Center
                    </button>
                    <button type="button" onClick={() => alignElement("top-center")}>
                      <i className="ti ti-layout-align-top" /> Top-Center
                    </button>
                    <button type="button" onClick={() => alignElement("center")}>
                      <i className="ti ti-layout-align-middle" /> Center
                    </button>
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
                      Layer Stacking in {aspectRatio} ({selectedIndex + 1} of {elements.length})
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
                        setActiveTab("layers");
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
                className={`canva-tool-btn ${activeTab === "layers" ? "is-active" : ""}`}
                onClick={() => setActiveTab((prev) => (prev === "layers" ? null : "layers"))}
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
                <i className="ti ti-wand" /> Select an element to edit for <strong>{aspectRatio}</strong> or pick a tool from the side rail
              </span>
              <span className="canva-layer-counter-pill">
                <i className="ti ti-layers-intersect" /> {elements.length}/3 Elements Added
              </span>
            </div>
          )}
        </header>

        {/* 4. Canvas Stage Area */}
        <div
          className="canva-stage-wrap"
          ref={stageRef}
          onClick={(e) => {
            if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains("canva-stage-wrap")) {
              setSelectedId(null);
            }
          }}
        >
          <div
            className={`canva-frame wm-aspect-${aspectRatio.replace(":", "-")} wm-bg-${background}`}
            ref={canvasRef}
            style={{
              transform: `scale(${zoom / 100})`,
              transformOrigin: "center center",
              transition: "transform 0.12s ease-out",
            }}
            onClick={(e) => {
              if (
                e.target === canvasRef.current ||
                (e.target as HTMLElement).classList.contains("wm-canvas-sample-bg")
              ) {
                setSelectedId(null);
              }
            }}
          >
            {/* Background Sample Images */}
            {background === "sample-light" && (
              <div className="wm-canvas-sample-bg wm-sample-light-gradient" />
            )}
            {background === "sample-dark" && (
              <div className="wm-canvas-sample-bg wm-sample-dark-gradient" />
            )}

            {/* Rendered Canvas Elements */}
            {sortedElements.map((el, index) => {
              const isSelected = el.id === selectedId;
              const elCfg = resolveRatioConfig(el, aspectRatio, index);
              const isHidden = !!elCfg.hidden;
              const box = computeBox(el, aspectRatio);
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
                  onPointerDown={(event) => interaction.beginMove(event, el.id)}
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
                        <span className="wm-handle handle-nw" onPointerDown={(e) => interaction.beginResize(e, el.id, "nw")} title="Resize" />
                        <span className="wm-handle handle-n" onPointerDown={(e) => interaction.beginResize(e, el.id, "n")} title="Adjust Height" />
                        <span className="wm-handle handle-ne" onPointerDown={(e) => interaction.beginResize(e, el.id, "ne")} title="Resize" />
                        <span className="wm-handle handle-e" onPointerDown={(e) => interaction.beginResize(e, el.id, "e")} title="Adjust Width" />
                        <span className="wm-handle handle-se" onPointerDown={(e) => interaction.beginResize(e, el.id, "se")} title="Resize" />
                        <span className="wm-handle handle-s" onPointerDown={(e) => interaction.beginResize(e, el.id, "s")} title="Adjust Height" />
                        <span className="wm-handle handle-sw" onPointerDown={(e) => interaction.beginResize(e, el.id, "sw")} title="Resize" />
                        <span className="wm-handle handle-w" onPointerDown={(e) => interaction.beginResize(e, el.id, "w")} title="Adjust Width" />
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
                          onClick={() => alignElement(el.type === "shape" ? "bottom-bar" : "bottom-right")}
                          title="Snap to Bottom Position"
                        >
                          <i className="ti ti-layout-align-bottom" />
                        </button>
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

            {/* Empty Canvas Notice */}
            {elements.length === 0 && (
              <div className="wm-empty-canvas-notice">
                <div className="wm-empty-icon-wrap">
                  <i className="ti ti-wand"></i>
                </div>
                <h3>Start Designing Your Watermark</h3>
                <p>Pick a tool on the left rail to add a brand logo, handle text, or background ribbon.</p>
                <div className="wm-empty-quick-actions">
                  <button
                    type="button"
                    className="wm-empty-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      addElement("image");
                    }}
                    disabled={disabled}
                  >
                    <i className="ti ti-photo-plus" /> + Logo
                  </button>
                  <button
                    type="button"
                    className="wm-empty-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      addElement("text");
                    }}
                    disabled={disabled}
                  >
                    <i className="ti ti-typography" /> + Text Handle
                  </button>
                  <button
                    type="button"
                    className="wm-empty-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      addElement("shape");
                    }}
                    disabled={disabled}
                  >
                    <i className="ti ti-rectangle" /> + Shape
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 5. Persistent Bottom Controls: Format & Backdrop Bar */}
        <footer className="canva-bottom-bar">
          {/* Aspect Ratio Format Control */}
          <div className="canva-pill-segment">
            <span className="canva-segment-label">Format:</span>
            <button
              type="button"
              className={`canva-segment-item ${aspectRatio === "1:1" ? "is-active" : ""}`}
              onClick={() => setAspectRatio("1:1")}
              title="Square 1:1"
            >
              <i className="ti ti-square" /> Square (1:1)
            </button>
            <button
              type="button"
              className={`canva-segment-item ${aspectRatio === "4:3" ? "is-active" : ""}`}
              onClick={() => setAspectRatio("4:3")}
              title="Standard 4:3"
            >
              <i className="ti ti-rectangle" /> Standard (4:3)
            </button>
            <button
              type="button"
              className={`canva-segment-item ${aspectRatio === "16:9" ? "is-active" : ""}`}
              onClick={() => setAspectRatio("16:9")}
              title="Landscape 16:9"
            >
              <i className="ti ti-rectangle" /> Landscape (16:9)
            </button>
          </div>

          {/* Canvas Backdrop Toggle */}
          <div className="canva-pill-segment">
            <span className="canva-segment-label">Backdrop:</span>
            <button
              type="button"
              className={`canva-segment-item ${background === "checkerboard" ? "is-active" : ""}`}
              onClick={() => setBackground("checkerboard")}
              title="Transparent Checkerboard"
            >
              <i className="ti ti-grid-dots" /> Transparent
            </button>
            <button
              type="button"
              className={`canva-segment-item ${background === "sample-light" ? "is-active" : ""}`}
              onClick={() => setBackground("sample-light")}
              title="Light Photo Preview"
            >
              <i className="ti ti-sun" /> Light Photo
            </button>
            <button
              type="button"
              className={`canva-segment-item ${background === "sample-dark" ? "is-active" : ""}`}
              onClick={() => setBackground("sample-dark")}
              title="Dark Photo Preview"
            >
              <i className="ti ti-moon" /> Dark Photo
            </button>
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
