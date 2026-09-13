import { useState, useRef, useEffect, useCallback } from "react";
import type { AspectRatioType, WatermarkElement, WatermarkElementType } from "../../../types/watermark.types";
import "../../../styles/watermark-editor.css";

interface WatermarkCanvasEditorProps {
  elements: WatermarkElement[];
  onChange: (elements: WatermarkElement[]) => void;
  disabled?: boolean;
  institutionName?: string;
  institutionLogoUrl?: string | null;
}

type CanvasBackground = "checkerboard" | "sample-light" | "sample-dark";
type SideTab = "shapes" | "text" | "uploads" | "layers" | null;

const COLOR_PRESETS = [
  "#FFFFFF",
  "#000000",
  "#1877F2",
  "#0F172A",
  "#475569",
  "#2563EB",
  "#059669",
  "#DC2626",
  "#D97706",
  "rgba(15, 23, 42, 0.75)",
  "rgba(255, 255, 255, 0.85)",
];

const FONT_OPTIONS = [
  { label: "Modern Sans", value: "inherit" },
  { label: "Geist / Clean", value: "Geist, -apple-system, sans-serif" },
  { label: "Outfit (Geometric)", value: "'Outfit', sans-serif" },
  { label: "Montserrat", value: "'Montserrat', sans-serif" },
  { label: "Playfair (Serif)", value: "'Playfair Display', Georgia, serif" },
  { label: "Cinzel (Classic)", value: "'Cinzel', Times, serif" },
  { label: "JetBrains Mono", value: "'JetBrains Mono', monospace" },
  { label: "Impact / Display", value: "Impact, 'Arial Black', sans-serif" },
];

export default function WatermarkCanvasEditor({
  elements,
  onChange,
  disabled = false,
  institutionName,
  institutionLogoUrl,
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
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});

  const canvasRef = useRef<HTMLDivElement>(null);
  const justDraggedRef = useRef(false);

  const dragRef = useRef<{
    isDragging: boolean;
    isResizing: boolean;
    hasMoved: boolean;
    resizeHandle: string | null;
    elementId: string;
    startX: number;
    startY: number;
    initialX: number;
    initialY: number;
    initialW: number;
    initialH: number;
  }>({
    isDragging: false,
    isResizing: false,
    hasMoved: false,
    resizeHandle: null,
    elementId: "",
    startX: 0,
    startY: 0,
    initialX: 0,
    initialY: 0,
    initialW: 0,
    initialH: 0,
  });

  const selectedElement = elements.find((el) => el.id === selectedId) ?? null;

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
    if (elements.length >= 3 || disabled) return;

    const id = `el_${Date.now()}`;
    let newElement: WatermarkElement;

    if (type === "image") {
      newElement = {
        id,
        type: "image",
        xPercent: 78,
        yPercent: 78,
        widthPercent: 18,
        heightPercent: 18,
        opacity: 0.9,
        imageUrl: initialVariant || institutionLogoUrl || "/dasig-logo.png",
      };
    } else if (type === "text") {
      const defaultText = initialVariant || (institutionName ? `@${institutionName.replace(/\s+/g, "")}` : "@DASIGConnect");
      newElement = {
        id,
        type: "text",
        xPercent: 50,
        yPercent: 90,
        widthPercent: 46,
        heightPercent: 7,
        opacity: 0.95,
        text: defaultText,
        textColor: "#FFFFFF",
        fontSizePercent: 3.2,
        fontWeight: "700",
        fontStyle: "normal",
        fontFamily: "inherit",
      };
    } else {
      // Shape
      const isLine = initialVariant === "line";
      newElement = {
        id,
        type: "shape",
        shapeType: isLine ? "line" : "rectangle",
        xPercent: 0,
        yPercent: 86,
        widthPercent: 100,
        heightPercent: isLine ? 2 : 14,
        opacity: 0.5,
        fillColor: "rgba(15, 23, 42, 0.75)",
        strokeColor: "transparent",
      };
    }

    const updated = [...elements, newElement];
    onChange(updated);
    setSelectedId(id);
    setActiveTab(null);
  }

  function updateSelected(updates: Partial<WatermarkElement>) {
    if (!selectedId || disabled) return;
    const updated = elements.map((el) => (el.id === selectedId ? { ...el, ...updates } : el));
    onChange(updated);
  }

  function removeSelected() {
    if (!selectedId || disabled) return;
    const updated = elements.filter((el) => el.id !== selectedId);
    onChange(updated);
    setSelectedId(null);
  }

  function alignElement(alignment: "bottom-right" | "bottom-left" | "top-right" | "top-left" | "bottom-bar" | "top-bar" | "center") {
    if (!selectedElement || disabled) return;

    if (alignment === "bottom-right") {
      updateSelected({
        xPercent: Math.max(0, 100 - selectedElement.widthPercent - 3),
        yPercent: Math.max(0, 100 - selectedElement.heightPercent - 3),
      });
    } else if (alignment === "bottom-left") {
      updateSelected({
        xPercent: 3,
        yPercent: Math.max(0, 100 - selectedElement.heightPercent - 3),
      });
    } else if (alignment === "top-right") {
      updateSelected({
        xPercent: Math.max(0, 100 - selectedElement.widthPercent - 3),
        yPercent: 3,
      });
    } else if (alignment === "top-left") {
      updateSelected({
        xPercent: 3,
        yPercent: 3,
      });
    } else if (alignment === "bottom-bar") {
      updateSelected({
        xPercent: 0,
        yPercent: Math.max(0, 100 - (selectedElement.heightPercent || 14)),
        widthPercent: 100,
      });
    } else if (alignment === "top-bar") {
      updateSelected({
        xPercent: 0,
        yPercent: 0,
        widthPercent: 100,
      });
    } else if (alignment === "center") {
      updateSelected({
        xPercent: Math.max(0, (100 - selectedElement.widthPercent) / 2),
        yPercent: Math.max(0, (100 - selectedElement.heightPercent) / 2),
      });
    }
    setShowAlignMenu(false);
  }

  function moveLayer(id: string, direction: "up" | "down" | "top" | "bottom") {
    const index = elements.findIndex((el) => el.id === id);
    if (index === -1 || disabled) return;
    const newElements = [...elements];

    if (direction === "up" && index < newElements.length - 1) {
      const temp = newElements[index];
      newElements[index] = newElements[index + 1];
      newElements[index + 1] = temp;
    } else if (direction === "down" && index > 0) {
      const temp = newElements[index];
      newElements[index] = newElements[index - 1];
      newElements[index - 1] = temp;
    } else if (direction === "top") {
      const [item] = newElements.splice(index, 1);
      newElements.push(item);
    } else if (direction === "bottom") {
      const [item] = newElements.splice(index, 1);
      newElements.unshift(item);
    }
    onChange(newElements);
  }

  // Pointer Drag & Resize Handling
  const handlePointerDown = (event: React.PointerEvent, elementId: string, handle: string | null = null) => {
    if (disabled) return;
    if (editingTextId === elementId && handle === null) return;
    event.stopPropagation();

    const targetEl = elements.find((el) => el.id === elementId);
    if (!targetEl) return;

    setSelectedId(elementId);
    if (editingTextId && editingTextId !== elementId) {
      setEditingTextId(null);
    }

    dragRef.current = {
      isDragging: handle === null,
      isResizing: handle !== null,
      hasMoved: false,
      resizeHandle: handle,
      elementId,
      startX: event.clientX,
      startY: event.clientY,
      initialX: targetEl.xPercent,
      initialY: targetEl.yPercent,
      initialW: targetEl.widthPercent,
      initialH: targetEl.heightPercent,
    };
  };

  const elementsRef = useRef(elements);
  useEffect(() => {
    elementsRef.current = elements;
  }, [elements]);

  const onChangeRef = useRef(onChange);
  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  const handlePointerMove = useCallback(
    (event: PointerEvent) => {
      const drag = dragRef.current;
      if (!drag.isDragging && !drag.isResizing) return;
      if (!canvasRef.current) return;

      const deltaPixelX = event.clientX - drag.startX;
      const deltaPixelY = event.clientY - drag.startY;

      if (!drag.hasMoved && Math.hypot(deltaPixelX, deltaPixelY) < 3) {
        return;
      }
      drag.hasMoved = true;
      justDraggedRef.current = true;

      const canvasRect = canvasRef.current.getBoundingClientRect();
      if (canvasRect.width === 0 || canvasRect.height === 0) return;

      const deltaXPercent = (deltaPixelX / canvasRect.width) * 100;
      const deltaYPercent = (deltaPixelY / canvasRect.height) * 100;

      const currentElements = elementsRef.current;

      if (drag.isDragging) {
        const nextX = Math.max(0, Math.min(100 - drag.initialW, drag.initialX + deltaXPercent));
        const nextY = Math.max(0, Math.min(100 - drag.initialH, drag.initialY + deltaYPercent));

        onChangeRef.current(
          currentElements.map((el) =>
            el.id === drag.elementId
              ? {
                  ...el,
                  xPercent: Math.round(nextX * 10) / 10,
                  yPercent: Math.round(nextY * 10) / 10,
                }
              : el
          )
        );
      } else if (drag.isResizing && drag.resizeHandle) {
        let nextW = drag.initialW;
        let nextH = drag.initialH;
        let nextX = drag.initialX;
        let nextY = drag.initialY;

        if (drag.resizeHandle.includes("e")) {
          nextW = Math.max(4, Math.min(100 - drag.initialX, drag.initialW + deltaXPercent));
        }
        if (drag.resizeHandle.includes("s")) {
          nextH = Math.max(2, Math.min(100 - drag.initialY, drag.initialH + deltaYPercent));
        }
        if (drag.resizeHandle.includes("w")) {
          const maxDelta = drag.initialX + drag.initialW - 4;
          const clampedDelta = Math.min(deltaXPercent, drag.initialW - 4);
          nextX = Math.max(0, Math.min(maxDelta, drag.initialX + clampedDelta));
          nextW = drag.initialW - (nextX - drag.initialX);
        }
        if (drag.resizeHandle.includes("n")) {
          const maxDelta = drag.initialY + drag.initialH - 2;
          const clampedDelta = Math.min(deltaYPercent, drag.initialH - 2);
          nextY = Math.max(0, Math.min(maxDelta, drag.initialY + clampedDelta));
          nextH = drag.initialH - (nextY - drag.initialY);
        }

        onChangeRef.current(
          currentElements.map((el) =>
            el.id === drag.elementId
              ? {
                  ...el,
                  xPercent: Math.round(nextX * 10) / 10,
                  yPercent: Math.round(nextY * 10) / 10,
                  widthPercent: Math.round(nextW * 10) / 10,
                  heightPercent: Math.round(nextH * 10) / 10,
                }
              : el
          )
        );
      }
    },
    []
  );

  const handlePointerUp = useCallback(() => {
    dragRef.current.isDragging = false;
    dragRef.current.isResizing = false;
    dragRef.current.hasMoved = false;
    dragRef.current.resizeHandle = null;
    setTimeout(() => {
      justDraggedRef.current = false;
    }, 150);
  }, []);

  useEffect(() => {
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);
    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [handlePointerMove, handlePointerUp]);

  function handleImageUpload(file: File) {
    if (!file || !file.type.startsWith("image/")) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === "string") {
        if (selectedElement && selectedElement.type === "image") {
          updateSelected({ imageUrl: reader.result });
        } else {
          addElement("image", reader.result);
        }
      }
    };
    reader.readAsDataURL(file);
  }

  function toggleElementVisibility(id: string) {
    onChange(
      elements.map((el) =>
        el.id === id ? { ...el, opacity: el.opacity === 0 ? 0.9 : 0 } : el
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
    return el.shapeType === "line" ? "Divider Line" : "Background Ribbon";
  }

  // Floating pill positioning helpers
  function getFloatingPillPlacement(el: WatermarkElement) {
    const isDockedTop = el.yPercent < 14;
    const isDockedLeft = el.xPercent < 10;
    const isDockedRight = el.xPercent + el.widthPercent > 88;

    let positionClass = isDockedTop ? "pill-docked-top" : "pill-docked-bottom";
    if (isDockedLeft) positionClass += " pill-align-left";
    else if (isDockedRight) positionClass += " pill-align-right";
    else positionClass += " pill-align-center";

    return positionClass;
  }

  return (
    <div className="canva-studio-app">
      {/* 1. Left Vertical Navigation Rail (Blue & White) */}
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

        {/* Info button at the outmost bottom of rail */}
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
                  <strong>Customize:</strong> Click any element to edit font family, style, colors, or opacity.
                </li>
                <li>
                  <strong>Drag & Resize:</strong> Move freely or drag the 8-point handles to scale.
                </li>
                <li>
                  <strong>Snap Position:</strong> Use the <em>Position</em> tool or the floating snap icon.
                </li>
                <li>
                  <strong>Format:</strong> Preview 1:1, 4:5, or 16:9 aspect ratios at the bottom bar.
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
                  <p className="canva-drawer-desc">Add solid backdrop ribbons or divider lines to highlight text.</p>
                  <div className="canva-item-card-grid">
                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("shape", "rectangle")}
                      disabled={elements.length >= 3 || disabled}
                    >
                      <div className="canva-item-preview shape-ribbon-preview">
                        <div className="mini-ribbon" />
                      </div>
                      <div className="canva-item-meta">
                        <strong>Background Ribbon</strong>
                        <span>Bottom or top branding banner</span>
                      </div>
                      <i className="ti ti-plus canva-add-icon"></i>
                    </button>

                    <button
                      type="button"
                      className="canva-item-card"
                      onClick={() => addElement("shape", "line")}
                      disabled={elements.length >= 3 || disabled}
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
                      disabled={elements.length >= 3 || disabled}
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
                      disabled={elements.length >= 3 || disabled}
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
                      accept="image/png,image/jpeg,image/svg+xml,image/webp"
                      style={{ display: "none" }}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) handleImageUpload(file);
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
                              updateSelected({ imageUrl: institutionLogoUrl });
                            } else {
                              addElement("image", institutionLogoUrl);
                            }
                          }}
                          disabled={elements.length >= 3 && (!selectedElement || selectedElement.type !== "image") || disabled}
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
                            updateSelected({ imageUrl: "/dasig-logo.png" });
                          } else {
                            addElement("image", "/dasig-logo.png");
                          }
                        }}
                        disabled={elements.length >= 3 && (!selectedElement || selectedElement.type !== "image") || disabled}
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
                  {elements.length === 0 ? (
                    <div className="canva-empty-layers">
                      <i className="ti ti-layers-subtract"></i>
                      <p>No elements added yet. Click Shape, Text, or Uploads on the left to add a layer.</p>
                    </div>
                  ) : (
                    <div className="canva-layers-stack">
                      {elements.map((el, index) => {
                        const isSelected = el.id === selectedId;
                        const isHidden = el.opacity === 0;

                        return (
                          <div
                            key={el.id}
                            className={`canva-layer-row ${isSelected ? "is-selected" : ""}`}
                            onClick={() => setSelectedId(el.id)}
                          >
                            <div className="canva-layer-info">
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

                            <div className="canva-layer-actions" onClick={(e) => e.stopPropagation()}>
                              <button
                                type="button"
                                className="canva-layer-icon-btn"
                                onClick={() => moveLayer(el.id, "up")}
                                disabled={index === elements.length - 1 || disabled}
                                title="Bring Forward"
                              >
                                <i className="ti ti-arrow-up" />
                              </button>
                              <button
                                type="button"
                                className="canva-layer-icon-btn"
                                onClick={() => moveLayer(el.id, "down")}
                                disabled={index === 0 || disabled}
                                title="Send Backward"
                              >
                                <i className="ti ti-arrow-down" />
                              </button>
                              <button
                                type="button"
                                className={`canva-layer-icon-btn ${isHidden ? "is-hidden" : ""}`}
                                onClick={() => toggleElementVisibility(el.id)}
                                title={isHidden ? "Show element" : "Hide element"}
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
        {/* Top Contextual Canva Toolbar (Centered Adjusting Tools) */}
        <header className="canva-topbar">
          {/* A. If Text Element is Selected */}
          {selectedElement && selectedElement.type === "text" && (
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
                  <span>{FONT_OPTIONS.find((f) => f.value === (selectedElement.fontFamily || "inherit"))?.label || "Modern Sans"}</span>
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
                          className={`canva-font-item ${(selectedElement.fontFamily || "inherit") === f.value ? "is-selected" : ""}`}
                          style={{ fontFamily: f.value }}
                          onClick={() => {
                            updateSelected({ fontFamily: f.value });
                            setShowFontPicker(false);
                          }}
                        >
                          <span>{f.label}</span>
                          {(selectedElement.fontFamily || "inherit") === f.value && (
                            <i className="ti ti-check" />
                          )}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Font Size Stepper */}
              <div className="canva-stepper-pill" title="Font Size (% of canvas)">
                <button
                  type="button"
                  className="canva-stepper-btn"
                  onClick={() => {
                    const newFont = Math.max(1.5, Math.round(((selectedElement.fontSizePercent || 3.2) - 0.3) * 10) / 10);
                    const lines = (selectedElement.text || "").split("\n").length;
                    updateSelected({
                      fontSizePercent: newFont,
                      heightPercent: Math.round((lines * (newFont * 1.25) + 0.6) * 10) / 10,
                    });
                  }}
                  disabled={disabled}
                >
                  <i className="ti ti-minus" />
                </button>
                <span className="canva-stepper-val">{Math.round((selectedElement.fontSizePercent || 3.2) * 10) / 10}%</span>
                <button
                  type="button"
                  className="canva-stepper-btn"
                  onClick={() => {
                    const newFont = Math.min(8.0, Math.round(((selectedElement.fontSizePercent || 3.2) + 0.3) * 10) / 10);
                    const lines = (selectedElement.text || "").split("\n").length;
                    updateSelected({
                      fontSizePercent: newFont,
                      heightPercent: Math.round((lines * (newFont * 1.25) + 0.6) * 10) / 10,
                    });
                  }}
                  disabled={disabled}
                >
                  <i className="ti ti-plus" />
                </button>
              </div>

              {/* Text Color Swatch Popover Toggle */}
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
                    style={{ backgroundColor: selectedElement.textColor || "#FFFFFF" }}
                  />
                  <span>Color</span>
                  <i className="ti ti-chevron-down" />
                </button>

                {showColorPicker && (
                  <div className="canva-floating-menu canva-color-dropdown">
                    <div className="canva-dropdown-title">Text Color</div>
                    <div className="canva-color-grid">
                      {COLOR_PRESETS.map((color) => (
                        <button
                          key={color}
                          type="button"
                          className={`canva-color-dot ${(selectedElement.textColor || "#FFFFFF").toLowerCase() === color.toLowerCase() ? "is-selected" : ""}`}
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
                        value={selectedElement.textColor?.startsWith("#") ? selectedElement.textColor : "#FFFFFF"}
                        onChange={(e) => updateSelected({ textColor: e.target.value })}
                      />
                    </div>
                  </div>
                )}
              </div>

              {/* Bold Toggle */}
              <button
                type="button"
                className={`canva-tool-btn ${selectedElement.fontWeight === "700" || selectedElement.fontWeight === "800" ? "is-active" : ""}`}
                onClick={() =>
                  updateSelected({
                    fontWeight: selectedElement.fontWeight === "700" ? "400" : "700",
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
                className={`canva-tool-btn ${selectedElement.fontStyle === "italic" ? "is-active" : ""}`}
                onClick={() =>
                  updateSelected({
                    fontStyle: selectedElement.fontStyle === "italic" ? "normal" : "italic",
                  })
                }
                title="Italic"
                disabled={disabled}
              >
                <i className="ti ti-italic" />
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedElement.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedElement.opacity ?? 1.0}
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
                    <div className="canva-dropdown-title">Quick Alignment</div>
                    <button type="button" onClick={() => alignElement("bottom-right")}>
                      <i className="ti ti-layout-align-right" /> Bottom-Right
                    </button>
                    <button type="button" onClick={() => alignElement("bottom-left")}>
                      <i className="ti ti-layout-align-left" /> Bottom-Left
                    </button>
                    <button type="button" onClick={() => alignElement("top-right")}>
                      <i className="ti ti-layout-align-top" /> Top-Right
                    </button>
                    <button type="button" onClick={() => alignElement("top-left")}>
                      <i className="ti ti-layout-align-top" /> Top-Left
                    </button>
                    <button type="button" onClick={() => alignElement("center")}>
                      <i className="ti ti-layout-align-middle" /> Center
                    </button>
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">Layer Stacking</div>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "up")}>
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "down")}>
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "top")}>
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "bottom")}>
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                  </div>
                )}
              </div>

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
          {selectedElement && selectedElement.type === "shape" && (
            <div className="canva-context-group canva-anim-bar">
              {/* Shape Type Segmented Control */}
              <div className="canva-pill-segment">
                <button
                  type="button"
                  className={`canva-segment-item ${selectedElement.shapeType !== "line" ? "is-active" : ""}`}
                  onClick={() => updateSelected({ shapeType: "rectangle" })}
                  disabled={disabled}
                >
                  Ribbon
                </button>
                <button
                  type="button"
                  className={`canva-segment-item ${selectedElement.shapeType === "line" ? "is-active" : ""}`}
                  onClick={() => updateSelected({ shapeType: "line" })}
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
                  title="Shape Fill Color"
                  disabled={disabled}
                >
                  <span
                    className="canva-color-preview-pill"
                    style={{
                      backgroundColor:
                        selectedElement.shapeType === "line"
                          ? selectedElement.strokeColor || "#FFFFFF"
                          : selectedElement.fillColor || "rgba(15, 23, 42, 0.75)",
                    }}
                  />
                  <span>Color</span>
                  <i className="ti ti-chevron-down" />
                </button>

                {showColorPicker && (
                  <div className="canva-floating-menu canva-color-dropdown">
                    <div className="canva-dropdown-title">
                      {selectedElement.shapeType === "line" ? "Line Color" : "Fill Color"}
                    </div>
                    <div className="canva-color-grid">
                      {COLOR_PRESETS.map((color) => {
                        const activeColor =
                          selectedElement.shapeType === "line"
                            ? selectedElement.strokeColor || "#FFFFFF"
                            : selectedElement.fillColor || "rgba(15, 23, 42, 0.75)";
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
                <div className="canva-stepper-pill" title="Ribbon Height (% of canvas)">
                  <span className="canva-stepper-label">Height:</span>
                  <span className="canva-stepper-val">{Math.round((selectedElement.heightPercent || 14) * 10) / 10}%</span>
                  <input
                    type="range"
                    min={3}
                    max={45}
                    step={0.5}
                    className="canva-mini-slider"
                    value={selectedElement.heightPercent || 14}
                    onChange={(e) => {
                      const newH = parseFloat(e.target.value);
                      const isDockedBottom = (selectedElement.yPercent + selectedElement.heightPercent) >= 97;
                      if (isDockedBottom) {
                        updateSelected({
                          heightPercent: newH,
                          yPercent: Math.max(0, Math.round((100 - newH) * 10) / 10),
                        });
                      } else {
                        updateSelected({ heightPercent: newH });
                      }
                    }}
                    disabled={disabled}
                  />
                </div>
              ) : (
                <div className="canva-stepper-pill" title="Line Thickness">
                  <span className="canva-stepper-label">Thickness:</span>
                  <span className="canva-stepper-val">{Math.round((selectedElement.heightPercent || 2) * 10) / 10}%</span>
                  <input
                    type="range"
                    min={0.5}
                    max={6.0}
                    step={0.5}
                    className="canva-mini-slider"
                    value={selectedElement.heightPercent || 2}
                    onChange={(e) => updateSelected({ heightPercent: parseFloat(e.target.value) })}
                    disabled={disabled}
                  />
                </div>
              )}

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedElement.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedElement.opacity ?? 1.0}
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
                    <div className="canva-dropdown-title">Quick Alignment</div>
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
                    <div className="canva-dropdown-title">Layer Stacking</div>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "up")}>
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "down")}>
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "top")}>
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "bottom")}>
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                  </div>
                )}
              </div>

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
          {selectedElement && selectedElement.type === "image" && (
            <div className="canva-context-group canva-anim-bar">
              <label className="canva-tool-btn" title="Replace Logo">
                <i className="ti ti-upload" />
                <span>Replace Logo</span>
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/svg+xml,image/webp"
                  style={{ display: "none" }}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleImageUpload(file);
                  }}
                  disabled={disabled}
                />
              </label>

              {institutionLogoUrl && (
                <button
                  type="button"
                  className="canva-tool-btn"
                  onClick={() => updateSelected({ imageUrl: institutionLogoUrl })}
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
                onClick={() => updateSelected({ imageUrl: "/dasig-logo.png" })}
                title="Use Regional DASIG Logo"
                disabled={disabled}
              >
                <i className="ti ti-world" />
                <span>DASIG Logo</span>
              </button>

              {/* Opacity Stepper */}
              <div className="canva-stepper-pill" title="Transparency / Opacity">
                <i className="ti ti-blur" />
                <span className="canva-stepper-val">{Math.round((selectedElement.opacity ?? 1) * 100)}%</span>
                <input
                  type="range"
                  min={0.1}
                  max={1.0}
                  step={0.05}
                  className="canva-mini-slider"
                  value={selectedElement.opacity ?? 1.0}
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
                    <div className="canva-dropdown-title">Quick Alignment</div>
                    <button type="button" onClick={() => alignElement("bottom-right")}>
                      <i className="ti ti-layout-align-right" /> Bottom-Right
                    </button>
                    <button type="button" onClick={() => alignElement("bottom-left")}>
                      <i className="ti ti-layout-align-left" /> Bottom-Left
                    </button>
                    <button type="button" onClick={() => alignElement("top-right")}>
                      <i className="ti ti-layout-align-top" /> Top-Right
                    </button>
                    <button type="button" onClick={() => alignElement("top-left")}>
                      <i className="ti ti-layout-align-top" /> Top-Left
                    </button>
                    <div className="canva-dropdown-divider" />
                    <div className="canva-dropdown-title">Layer Stacking</div>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "up")}>
                      <i className="ti ti-arrow-up" /> Bring Forward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "down")}>
                      <i className="ti ti-arrow-down" /> Send Backward
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "top")}>
                      <i className="ti ti-stack-2" /> Bring to Front
                    </button>
                    <button type="button" onClick={() => moveLayer(selectedElement.id, "bottom")}>
                      <i className="ti ti-stack" /> Send to Back
                    </button>
                  </div>
                )}
              </div>

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
                <i className="ti ti-wand" /> Select a tool on the left to build your watermark layout
              </span>
              <span className="canva-layer-counter-pill">
                <i className="ti ti-layers-intersect" /> {elements.length}/3 Elements Added
              </span>
            </div>
          )}
        </header>

        {/* 4. Canvas Stage Area (Hero Centered) */}
        <div
          className="canva-stage-wrap"
          onClick={(e) => {
            if (justDraggedRef.current) return;
            if (e.target === e.currentTarget || (e.target as HTMLElement).classList.contains("canva-stage-wrap")) {
              setSelectedId(null);
            }
          }}
        >
          <div
            className={`canva-frame wm-aspect-${aspectRatio.replace(":", "-")} wm-bg-${background}`}
            ref={canvasRef}
            onClick={(e) => {
              if (justDraggedRef.current) return;
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
            {elements.map((el, index) => {
              const isSelected = el.id === selectedId;
              const pillPlacement = getFloatingPillPlacement(el);

              return (
                <div
                  key={el.id}
                  className={`wm-canvas-element wm-type-${el.type} ${isSelected ? "is-selected" : ""}`}
                  style={{
                    left: `${el.xPercent}%`,
                    top: `${el.yPercent}%`,
                    width: `${el.widthPercent}%`,
                    height: `${el.heightPercent}%`,
                    zIndex: isSelected ? 45 : 10 + index * 5,
                  }}
                  onPointerDown={(event) => handlePointerDown(event, el.id)}
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
                  {el.type === "image" && (
                    <div
                      className="wm-element-content wm-image-content"
                      style={{ opacity: el.opacity }}
                    >
                      {el.imageUrl && !failedImages[el.id] ? (
                        <img
                          src={el.imageUrl}
                          alt=""
                          draggable={false}
                          onError={() => setFailedImages((prev) => ({ ...prev, [el.id]: true }))}
                        />
                      ) : (
                        <div className="wm-image-placeholder">
                          <i className="ti ti-photo" />
                        </div>
                      )}
                    </div>
                  )}

                  {el.type === "text" && (
                    editingTextId === el.id && !disabled ? (
                      <textarea
                        autoFocus
                        rows={Math.max(1, (el.text || "").split("\n").length)}
                        className="wm-element-content wm-text-content wm-text-inline-input"
                        value={el.text || ""}
                        onChange={(e) => {
                          const val = e.target.value;
                          const lineCount = val.split("\n").length;
                          const fontPercent = el.fontSizePercent || 3.2;
                          const calculatedHeight = Math.round((lineCount * (fontPercent * 1.25) + 0.6) * 10) / 10;
                          const currentY = el.yPercent;
                          const clampedY = currentY + calculatedHeight > 100 ? Math.max(0, 100 - calculatedHeight) : currentY;
                          
                          updateSelected({
                            text: val,
                            heightPercent: calculatedHeight,
                            yPercent: Math.round(clampedY * 10) / 10,
                          });
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
                          opacity: el.opacity,
                          color: el.textColor || "#FFFFFF",
                          fontWeight: el.fontWeight || "700",
                          fontStyle: el.fontStyle || "normal",
                          fontFamily: el.fontFamily || "inherit",
                          fontSize: `clamp(10px, ${el.fontSizePercent || 3.2}cqi, 40px)`,
                        }}
                      />
                    ) : (
                      <div
                        className="wm-element-content wm-text-content"
                        style={{
                          opacity: el.opacity,
                          color: el.textColor || "#FFFFFF",
                          fontWeight: el.fontWeight || "700",
                          fontStyle: el.fontStyle || "normal",
                          fontFamily: el.fontFamily || "inherit",
                          fontSize: `clamp(10px, ${el.fontSizePercent || 3.2}cqi, 40px)`,
                        }}
                      >
                        {el.text || "Watermark Text"}
                      </div>
                    )
                  )}

                  {el.type === "shape" && (
                    <div
                      className={`wm-element-content wm-shape-content ${el.shapeType === "line" ? "is-line" : "is-rect"}`}
                      style={{
                        opacity: el.opacity,
                        backgroundColor: el.shapeType === "line" ? "transparent" : el.fillColor || "rgba(0,0,0,0.5)",
                        borderTop: el.shapeType === "line" ? `3px solid ${el.strokeColor || "#FFFFFF"}` : "none",
                      }}
                    />
                  )}

                  {/* 8-Point Selection Handles & Mini Action Bar */}
                  {isSelected && !disabled && (
                    <>
                      <div className="wm-element-handles" onClick={(e) => e.stopPropagation()} onPointerDown={(e) => e.stopPropagation()}>
                        <span className="wm-handle handle-nw" onPointerDown={(e) => handlePointerDown(e, el.id, "nw")} onClick={(e) => e.stopPropagation()} title="Resize" />
                        <span className="wm-handle handle-n" onPointerDown={(e) => handlePointerDown(e, el.id, "n")} onClick={(e) => e.stopPropagation()} title="Adjust Height" />
                        <span className="wm-handle handle-ne" onPointerDown={(e) => handlePointerDown(e, el.id, "ne")} onClick={(e) => e.stopPropagation()} title="Resize" />
                        <span className="wm-handle handle-e" onPointerDown={(e) => handlePointerDown(e, el.id, "e")} onClick={(e) => e.stopPropagation()} title="Adjust Width" />
                        <span className="wm-handle handle-se" onPointerDown={(e) => handlePointerDown(e, el.id, "se")} onClick={(e) => e.stopPropagation()} title="Resize" />
                        <span className="wm-handle handle-s" onPointerDown={(e) => handlePointerDown(e, el.id, "s")} onClick={(e) => e.stopPropagation()} title="Adjust Height" />
                        <span className="wm-handle handle-sw" onPointerDown={(e) => handlePointerDown(e, el.id, "sw")} onClick={(e) => e.stopPropagation()} title="Resize" />
                        <span className="wm-handle handle-w" onPointerDown={(e) => handlePointerDown(e, el.id, "w")} onClick={(e) => e.stopPropagation()} title="Adjust Width" />
                      </div>

                      {/* Mini Floating Action Tooltip with Boundary Detection */}
                      <div className={`canva-floating-pill-bar ${pillPlacement}`} onClick={(e) => e.stopPropagation()} onPointerDown={(e) => e.stopPropagation()}>
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
              title="Square 1:1 (1080×1080)"
            >
              <i className="ti ti-square" /> Square (1:1)
            </button>
            <button
              type="button"
              className={`canva-segment-item ${aspectRatio === "4:5" ? "is-active" : ""}`}
              onClick={() => setAspectRatio("4:5")}
              title="Portrait 4:5 (1080×1350)"
            >
              <i className="ti ti-rectangle-vertical" /> Portrait (4:5)
            </button>
            <button
              type="button"
              className={`canva-segment-item ${aspectRatio === "16:9" ? "is-active" : ""}`}
              onClick={() => setAspectRatio("16:9")}
              title="Landscape 16:9 (1920×1080)"
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
        </footer>
      </main>
    </div>
  );
}
