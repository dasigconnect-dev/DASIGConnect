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

  const positionCard = useCallback((padded: TargetRect, placementPref = "auto") => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const isMobile = vw < 640;
    const effectiveCardWidth = Math.min(CARD_WIDTH, vw - (isMobile ? 24 : 32));

    let calculatedTop = padded.bottom + CARD_MARGIN;
    let calculatedLeft = padded.left + (padded.width / 2) - (effectiveCardWidth / 2);

    if (isMobile) {
      // On mobile phones, always horizontally center the card across the screen
      calculatedLeft = (vw - effectiveCardWidth) / 2;

      // Check space above vs below
      const spaceBelow = vh - padded.bottom;
      const spaceAbove = padded.top;

      if (spaceBelow >= ESTIMATED_CARD_HEIGHT + CARD_MARGIN + 16) {
        calculatedTop = padded.bottom + CARD_MARGIN;
      } else if (spaceAbove >= ESTIMATED_CARD_HEIGHT + CARD_MARGIN + 16) {
        calculatedTop = padded.top - CARD_MARGIN - ESTIMATED_CARD_HEIGHT;
      } else {
        calculatedTop = spaceBelow > spaceAbove
          ? Math.min(padded.bottom + CARD_MARGIN, vh - ESTIMATED_CARD_HEIGHT - 12)
          : Math.max(12, padded.top - CARD_MARGIN - ESTIMATED_CARD_HEIGHT);
      }
    } else {
      const placement = placementPref || "auto";

      if (placement === "top" || (placement === "auto" && calculatedTop + ESTIMATED_CARD_HEIGHT > vh - 16)) {
        if (padded.top - CARD_MARGIN - ESTIMATED_CARD_HEIGHT > 16) {
          calculatedTop = padded.top - CARD_MARGIN - ESTIMATED_CARD_HEIGHT;
        }
      } else if (placement === "left") {
        calculatedLeft = padded.left - effectiveCardWidth - CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (ESTIMATED_CARD_HEIGHT / 2);
      } else if (placement === "right") {
        calculatedLeft = padded.right + CARD_MARGIN;
        calculatedTop = padded.top + (padded.height / 2) - (ESTIMATED_CARD_HEIGHT / 2);
      }
    }

    // Clamp inside viewport boundaries
    const margin = isMobile ? 12 : 16;
    const clampedLeft = Math.min(Math.max(margin, calculatedLeft), Math.max(margin, vw - effectiveCardWidth - margin));
    const clampedTop = Math.min(Math.max(12, calculatedTop), vh - ESTIMATED_CARD_HEIGHT - 12);

    setCardPosition({ top: clampedTop, left: clampedLeft });
  }, []);

  const fallbackCenterCard = useCallback(() => {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const isMobile = vw < 640;
    const effectiveCardWidth = Math.min(CARD_WIDTH, vw - (isMobile ? 24 : 32));
    const margin = isMobile ? 12 : 16;
    setTargetRect(null);
    setCardPosition({
      top: Math.max(16, (vh - ESTIMATED_CARD_HEIGHT) / 2),
      left: Math.max(margin, (vw - effectiveCardWidth) / 2),
    });
  }, []);

  // Calculate target element dimensions and card positioning
  const updatePositions = useCallback(() => {
    if (!currentStep) return;

    const el = document.querySelector(currentStep.target);
    if (!el) {
      // Retry once after 150ms in case element is rendering or animating
      if (retryTimeoutRef.current) window.clearTimeout(retryTimeoutRef.current);
      retryTimeoutRef.current = window.setTimeout(() => {
        const retryEl = document.querySelector(currentStep.target);
        if (retryEl) {
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

  // Scroll into view & update rect on step change
  useEffect(() => {
    if (!isOpen || !currentStep) return;

    const el = document.querySelector(currentStep.target);
    if (el) {
      // Check if inside a custom scrollable container like .sub-form-canvas
      const scrollParent = el.closest(".sub-form-canvas") || el.closest(".dash-body") || el.parentElement;
      if (scrollParent && scrollParent.scrollHeight > scrollParent.clientHeight) {
        const parentRect = scrollParent.getBoundingClientRect();
        const elRect = el.getBoundingClientRect();
        // If element is behind sticky navbar (within top 70px) or below fold, center it
        if (elRect.top < parentRect.top + 70 || elRect.bottom > parentRect.bottom - 40) {
          el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
        }
      } else {
        el.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
      }
    }

    // Repeated updates as smooth scrolling moves the element into its final settled spot
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
  }, [isOpen, currentStepIndex, currentStep, updatePositions]);


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

  // Compute clip-path cutout polygon with evenodd winding rule
  const clipPathStyle = targetRect
    ? {
        clipPath: `polygon(
          evenodd,
          0 0,
          100% 0,
          100% 100%,
          0 100%,
          0 0,
          ${targetRect.left}px ${targetRect.top}px,
          ${targetRect.right}px ${targetRect.top}px,
          ${targetRect.right}px ${targetRect.bottom}px,
          ${targetRect.left}px ${targetRect.bottom}px,
          ${targetRect.left}px ${targetRect.top}px
        )`,
        WebkitClipPath: `polygon(
          evenodd,
          0 0,
          100% 0,
          100% 100%,
          0 100%,
          0 0,
          ${targetRect.left}px ${targetRect.top}px,
          ${targetRect.right}px ${targetRect.top}px,
          ${targetRect.right}px ${targetRect.bottom}px,
          ${targetRect.left}px ${targetRect.bottom}px,
          ${targetRect.left}px ${targetRect.top}px
        )`,
      }
    : undefined;

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
