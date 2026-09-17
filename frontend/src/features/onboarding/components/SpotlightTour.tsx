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
  const cardRef = useRef<HTMLDivElement>(null);
  const retryTimeoutRef = useRef<number | null>(null);

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
      return;
    }

    let calculatedTop = padded.bottom + CARD_MARGIN;
    let calculatedLeft = padded.left + (padded.width / 2) - (effectiveCardWidth / 2);

    if (isMobile) {
      // Mobile positioning:
      // Always horizontally center across viewport with safe gutters
      calculatedLeft = (vw - effectiveCardWidth) / 2;

      const elementCenterY = (padded.top + padded.bottom) / 2;
      const spaceAbove = padded.top;
      const spaceBelow = vh - padded.bottom;

      if (elementCenterY <= vh * 0.52) {
        // Element is in upper viewport area
        if (spaceBelow >= cardHeight + 20) {
          calculatedTop = padded.bottom + 10;
        } else {
          // Dock at bottom sheet position to maximize top spotlight visibility
          calculatedTop = vh - cardHeight - 12;
        }
      } else {
        // Element is in lower viewport area
        if (spaceAbove >= cardHeight + 20) {
          calculatedTop = padded.top - cardHeight - 10;
        } else {
          // Dock at top position to leave lower spotlight element visible
          calculatedTop = 12;
        }
      }
    } else {
      const placement = placementPref || "auto";

      if (placement === "top" || (placement === "auto" && calculatedTop + cardHeight > vh - 16)) {
        if (padded.top - CARD_MARGIN - cardHeight > 16) {
          calculatedTop = padded.top - CARD_MARGIN - cardHeight;
        }
      } else if (placement === "left") {
        calculatedLeft = padded.left - effectiveCardWidth - CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (cardHeight / 2);
      } else if (placement === "right") {
        calculatedLeft = padded.right + CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (cardHeight / 2);
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
      // Retry once after 150ms in case element is rendering or animating
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

    if (!isMobile) {
      el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
      return;
    }

    // On mobile devices, check if target is already visible without overlapping card
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

  // Scroll into view & update rect on step change
  useEffect(() => {
    if (!isOpen || !currentStep) return;

    const el = document.querySelector(currentStep.target);
    if (el instanceof HTMLElement && isElementRenderedAndVisible(el)) {
      scrollTargetIntoView(el);
    }

    // Repeated updates as smooth scrolling and CSS transitions settle
    const timers = [
      setTimeout(updatePositions, 60),
      setTimeout(updatePositions, 150),
      setTimeout(updatePositions, 300),
      setTimeout(updatePositions, 500),
    ];

    return () => {
      timers.forEach(clearTimeout);
      if (retryTimeoutRef.current) clearTimeout(retryTimeoutRef.current);
    };
  }, [isOpen, currentStepIndex, currentStep, scrollTargetIntoView, updatePositions]);

  // Resize and scroll tracking
  useEffect(() => {
    if (!isOpen) return;

    const handleResizeOrScroll = () => {
      updatePositions();
    };

    window.addEventListener("resize", handleResizeOrScroll, { passive: true });
    window.addEventListener("scroll", handleResizeOrScroll, { passive: true, capture: true });

    return () => {
      window.removeEventListener("resize", handleResizeOrScroll);
      window.removeEventListener("scroll", handleResizeOrScroll, true);
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
