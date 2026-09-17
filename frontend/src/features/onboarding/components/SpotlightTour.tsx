import { useEffect, useState, useCallback, useRef } from "react";
import { createPortal } from "react-dom";
import type { TourStep } from "../types";
import "./SpotlightTour.css";

interface SpotlightTourProps {
  steps: TourStep[];
  currentStepIndex: number;
  isOpen: boolean;
  onNext: () => void;
  onBack: () => void;
  onSkip: () => void;
  onComplete: () => void;
}

interface TargetRect {
  top: number;
  left: number;
  width: number;
  height: number;
  bottom: number;
  right: number;
}

interface ArrowPosition {
  direction: "up" | "down" | "none";
  left: number;
}

const PADDING = 8;
const CARD_MARGIN = 14;
const CARD_WIDTH = 380;
const ESTIMATED_CARD_HEIGHT = 220;

function isElementRenderedAndVisible(el: Element | null): el is HTMLElement {
  if (!el || !(el instanceof HTMLElement)) return false;
  const style = window.getComputedStyle(el);
  if (
    style.display === "none" ||
    style.visibility === "hidden" ||
    style.opacity === "0"
  ) {
    return false;
  }
  const rect = el.getBoundingClientRect();
  return rect.width > 2 && rect.height > 2;
}

function getScrollParent(node: HTMLElement | null): HTMLElement | Window {
  if (!node) return window;
  let parent: HTMLElement | null = node.parentElement;
  while (parent && parent !== document.body && parent !== document.documentElement) {
    const style = window.getComputedStyle(parent);
    const overflowY = style.overflowY;
    if (
      (overflowY === "auto" || overflowY === "scroll") &&
      parent.scrollHeight > parent.clientHeight
    ) {
      return parent;
    }
    parent = parent.parentElement;
  }
  return window;
}

export default function SpotlightTour({
  steps,
  currentStepIndex,
  isOpen,
  onNext,
  onBack,
  onSkip,
  onComplete,
}: SpotlightTourProps) {
  const [targetRect, setTargetRect] = useState<TargetRect | null>(null);
  const [cardPosition, setCardPosition] = useState<{ top: number; left: number }>({ top: 100, left: 100 });
  const [arrowPosition, setArrowPosition] = useState<ArrowPosition | null>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const retryTimeoutRef = useRef<number | null>(null);
  const rafIdRef = useRef<number | null>(null);

  const currentStep = steps[currentStepIndex];
  const isLastStep = currentStepIndex === steps.length - 1;

  const positionCard = useCallback((padded: TargetRect | null, placementPref = "auto") => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const isMobile = vw < 640;
    const effectiveCardWidth = Math.min(CARD_WIDTH, vw - (isMobile ? 24 : 32));
    const cardHeight = cardRef.current ? cardRef.current.offsetHeight : ESTIMATED_CARD_HEIGHT;

    if (!padded) {
      // Centered modal card fallback
      const margin = isMobile ? 12 : 16;
      setCardPosition({
        top: Math.max(16, (vh - cardHeight) / 2),
        left: Math.max(margin, (vw - effectiveCardWidth) / 2),
      });
      setArrowPosition(null);
      return;
    }

    let calculatedTop = padded.bottom + CARD_MARGIN;
    let calculatedLeft = padded.left + (padded.width / 2) - (effectiveCardWidth / 2);
    let arrowDir: "up" | "down" | "none" = "up";

    if (isMobile) {
      // Mobile positioning: center horizontally across screen
      calculatedLeft = (vw - effectiveCardWidth) / 2;

      const elementCenterY = (padded.top + padded.bottom) / 2;

      // Check proximity: can card fit directly below?
      const fitsBelow = (padded.bottom + 12 + cardHeight) <= (vh - 12);
      const fitsAbove = (padded.top - 12 - cardHeight) >= 60;

      if (elementCenterY <= vh * 0.52) {
        if (fitsBelow) {
          calculatedTop = padded.bottom + 12;
          arrowDir = "up";
        } else if (fitsAbove) {
          calculatedTop = padded.top - cardHeight - 12;
          arrowDir = "down";
        } else {
          // Dock at bottom to maximize upper target visibility
          calculatedTop = vh - cardHeight - 12;
          arrowDir = "up";
        }
      } else {
        if (fitsAbove) {
          calculatedTop = padded.top - cardHeight - 12;
          arrowDir = "down";
        } else if (fitsBelow) {
          calculatedTop = padded.bottom + 12;
          arrowDir = "up";
        } else {
          // Dock at top to maximize lower target visibility
          calculatedTop = 64;
          arrowDir = "down";
        }
      }
    } else {
      // Desktop positioning: preserve exact desktop placements and alignments
      const placement = placementPref || "auto";

      if (placement === "top" || (placement === "auto" && calculatedTop + cardHeight > vh - 16)) {
        if (padded.top - CARD_MARGIN - cardHeight > 16) {
          calculatedTop = padded.top - CARD_MARGIN - cardHeight;
          arrowDir = "down";
        } else {
          arrowDir = "up";
        }
      } else if (placement === "left") {
        calculatedLeft = padded.left - effectiveCardWidth - CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (cardHeight / 2);
        arrowDir = "none";
      } else if (placement === "right") {
        calculatedLeft = padded.right + CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (cardHeight / 2);
        arrowDir = "none";
      } else {
        arrowDir = "up";
      }
    }

    // Clamp inside viewport boundaries
    const margin = isMobile ? 12 : 16;
    const clampedLeft = Math.min(
      Math.max(margin, calculatedLeft),
      Math.max(margin, vw - effectiveCardWidth - margin)
    );
    const clampedTop = Math.min(
      Math.max(12, calculatedTop),
      Math.max(12, vh - cardHeight - 12)
    );

    setCardPosition({ top: clampedTop, left: clampedLeft });

    // Calculate pointer arrow horizontal position pointing directly at element center
    if (arrowDir !== "none") {
      const targetCenterX = padded.left + (padded.width / 2);
      const rawArrowX = targetCenterX - clampedLeft;
      // Clamp arrow so it never overflows rounded corners (16px radius)
      const clampedArrowX = Math.max(24, Math.min(rawArrowX, effectiveCardWidth - 24));
      setArrowPosition({ direction: arrowDir, left: clampedArrowX });
    } else {
      setArrowPosition(null);
    }
  }, []);

  const fallbackCenterCard = useCallback(() => {
    setTargetRect(null);
    positionCard(null);
  }, [positionCard]);

  // Calculate target element dimensions and card positioning
  const updatePositions = useCallback(() => {
    if (!currentStep) return;

    const el = document.querySelector(currentStep.target);
    if (!isElementRenderedAndVisible(el)) {
      if (retryTimeoutRef.current) window.clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = window.setTimeout(() => {
        const retryEl = document.querySelector(currentStep.target);
        if (isElementRenderedAndVisible(retryEl)) {
          const rect = retryEl.getBoundingClientRect();
          const padded: TargetRect = {
            top: Math.max(0, rect.top - PADDING),
            left: Math.max(0, rect.left - PADDING),
            width: rect.width + PADDING * 2,
            height: rect.height + PADDING * 2,
            bottom: rect.bottom + PADDING,
            right: rect.right + PADDING,
          };
          setTargetRect(padded);
          positionCard(padded, currentStep.placement);
        } else {
          fallbackCenterCard();
        }
      }, 150);
      return;
    }

    const rect = el.getBoundingClientRect();
    const padded: TargetRect = {
      top: Math.max(0, rect.top - PADDING),
      left: Math.max(0, rect.left - PADDING),
      width: rect.width + PADDING * 2,
      height: rect.height + PADDING * 2,
      bottom: rect.bottom + PADDING,
      right: rect.right + PADDING,
    };

    setTargetRect(padded);
    positionCard(padded, currentStep.placement);
  }, [currentStep, positionCard, fallbackCenterCard]);

  // Smooth mobile/desktop scroll target into view
  const scrollTargetIntoView = useCallback((el: HTMLElement) => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const isMobile = vw < 640;

    // Desktop: standard center scroll (preserves desktop experience 100%)
    if (!isMobile) {
      el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
      return;
    }

    // Mobile: check if target is already visible without overlapping card
    const rect = el.getBoundingClientRect();
    const cardHeight = cardRef.current?.offsetHeight || ESTIMATED_CARD_HEIGHT;
    const isAlreadyFullyVisible = rect.top >= 64 && rect.bottom <= (vh - cardHeight - 16);

    if (isAlreadyFullyVisible) {
      return;
    }

    const scrollParent = getScrollParent(el);
    if (scrollParent === window || scrollParent === document.documentElement || scrollParent === document.body) {
      const targetScrollY = window.scrollY + rect.top - 72;
      window.scrollTo({
        top: Math.max(0, targetScrollY),
        behavior: "smooth",
      });
    } else if (scrollParent instanceof HTMLElement) {
      const parentRect = scrollParent.getBoundingClientRect();
      const currentScrollTop = scrollParent.scrollTop;
      const targetScrollTop = currentScrollTop + (rect.top - parentRect.top) - 16;
      scrollParent.scrollTo({
        top: Math.max(0, targetScrollTop),
        behavior: "smooth",
      });
    } else {
      el.scrollIntoView({ behavior: "smooth", block: "start", inline: "nearest" });
    }
  }, []);

  // Continuous RAF tracking loop: guarantees spotlight & card track the element at 60fps while scrolling
  const startTrackingLoop = useCallback((durationMs = 900) => {
    if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    const start = performance.now();

    const loop = (now: number) => {
      updatePositions();
      if (now - start < durationMs) {
        rafIdRef.current = requestAnimationFrame(loop);
      } else {
        rafIdRef.current = null;
      }
    };

    rafIdRef.current = requestAnimationFrame(loop);
  }, [updatePositions]);

  // Scroll into view & update rect on step change
  useEffect(() => {
    if (!isOpen || !currentStep) return;

    const el = document.querySelector(currentStep.target);
    if (el instanceof HTMLElement && isElementRenderedAndVisible(el)) {
      scrollTargetIntoView(el);
    }

    // Run high-precision tracking loop as smooth scroll settles
    startTrackingLoop(900);

    return () => {
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
    };
  }, [isOpen, currentStepIndex, currentStep, scrollTargetIntoView, startTrackingLoop]);

  // Resize and scroll tracking
  useEffect(() => {
    if (!isOpen) return;

    const handleResizeOrScroll = () => {
      updatePositions();
    };

    window.addEventListener("resize", handleResizeOrScroll, { passive: true });
    window.addEventListener("scroll", handleResizeOrScroll, { passive: true, capture: true });
    window.addEventListener("scrollend", handleResizeOrScroll, { passive: true, capture: true });

    return () => {
      window.removeEventListener("resize", handleResizeOrScroll);
      window.removeEventListener("scroll", handleResizeOrScroll, true);
      window.removeEventListener("scrollend", handleResizeOrScroll, true);
      if (rafIdRef.current) cancelAnimationFrame(rafIdRef.current);
    };
  }, [isOpen, updatePositions]);

  // Dynamic card resize observation (e.g. step text length changes or orientation change)
  useEffect(() => {
    if (!isOpen || !cardRef.current) return;
    if (typeof ResizeObserver === "undefined") return;

    const observer = new ResizeObserver(() => {
      if (targetRect) {
        positionCard(targetRect, currentStep?.placement);
      } else {
        fallbackCenterCard();
      }
    });

    observer.observe(cardRef.current);
    return () => observer.disconnect();
  }, [isOpen, targetRect, currentStep?.placement, positionCard, fallbackCenterCard]);

  // Keyboard navigation (Esc to skip, Arrows to navigate)
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        onSkip();
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        if (isLastStep) onComplete();
        else onNext();
      } else if (e.key === "ArrowLeft") {
        e.preventDefault();
        if (currentStepIndex > 0) onBack();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, currentStepIndex, isLastStep, onSkip, onNext, onBack, onComplete]);

  if (!isOpen || !currentStep) return null;

  // Clamped cutout polygon calculation with evenodd winding rule
  const getClipPathStyle = () => {
    if (!targetRect) return undefined;
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    const left = Math.max(0, Math.min(targetRect.left, vw));
    const top = Math.max(0, Math.min(targetRect.top, vh));
    const right = Math.max(0, Math.min(targetRect.right, vw));
    const bottom = Math.max(0, Math.min(targetRect.bottom, vh));

    if (right - left <= 4 || bottom - top <= 4) {
      return undefined;
    }

    const polygon = `polygon(
      evenodd,
      0 0,
      100% 0,
      100% 100%,
      0 100%,
      0 0,
      ${left}px ${top}px,
      ${right}px ${top}px,
      ${right}px ${bottom}px,
      ${left}px ${bottom}px,
      ${left}px ${top}px
    )`;

    return {
      clipPath: polygon,
      WebkitClipPath: polygon,
    };
  };

  const clipPathStyle = getClipPathStyle();

  return createPortal(
    <aside className="spotlight-tour-overlay" aria-label="Feature Walkthrough" aria-modal="true">
      {/* Blurred dimmed backdrop with evenodd polygon cutout */}
      <div className="spotlight-tour-backdrop" style={clipPathStyle} onClick={onSkip} />

      {/* Target outline highlight ring */}
      {targetRect && (
        <div
          className="spotlight-target-highlight"
          style={{
            top: targetRect.top,
            left: targetRect.left,
            width: targetRect.width,
            height: targetRect.height,
          }}
        />
      )}

      {/* Floating Agentic Guide Card */}
      <div
        ref={cardRef}
        className="spotlight-card"
        style={{
          top: cardPosition.top,
          left: cardPosition.left,
        }}
        role="dialog"
      >
        {/* Directional pointer arrow pointing directly to the spotlighted element */}
        {targetRect && arrowPosition && arrowPosition.direction !== "none" && (
          <div
            className={`spotlight-card-arrow spotlight-card-arrow-${arrowPosition.direction}`}
            style={{ left: `${arrowPosition.left}px` }}
            aria-hidden="true"
          />
        )}

        <div className="spotlight-card-header">
          <div className="spotlight-badge">
            <i className={currentStep.icon || "ti ti-sparkles"} aria-hidden="true" />
            <span>DASIG Guide</span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <span className="spotlight-step-count">
              {currentStepIndex + 1} of {steps.length}
            </span>
            <button
              type="button"
              className="spotlight-close-btn"
              onClick={onSkip}
              title="Close guide (Esc)"
              aria-label="Close guide"
            >
              <i className="ti ti-x" aria-hidden="true" />
            </button>
          </div>
        </div>

        <div className="spotlight-card-body">
          <h2 className="spotlight-card-title">{currentStep.title}</h2>
          <p className="spotlight-card-desc">{currentStep.description}</p>

          <div className="spotlight-progress-dots" aria-hidden="true">
            {steps.map((_, idx) => (
              <span
                key={idx}
                className={`spotlight-dot ${idx === currentStepIndex ? "active" : ""}`}
              />
            ))}
          </div>
        </div>

        <div className="spotlight-card-footer">
          <button
            type="button"
            className="spotlight-btn-skip"
            onClick={onSkip}
            title="Skip this guide"
          >
            Skip Guide
          </button>

          <div className="spotlight-nav-actions">
            <button
              type="button"
              className="spotlight-btn-back"
              onClick={onBack}
              disabled={currentStepIndex === 0}
              aria-label="Previous step"
            >
              <i className="ti ti-arrow-left" aria-hidden="true" />
              Back
            </button>

            <button
              type="button"
              className="spotlight-btn-next"
              onClick={isLastStep ? onComplete : onNext}
              aria-label={isLastStep ? "Finish guide" : "Next step"}
            >
              <span>{isLastStep ? "Got it" : "Next"}</span>
              <i className={isLastStep ? "ti ti-check" : "ti ti-arrow-right"} aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </aside>,
    document.body
  );
}
