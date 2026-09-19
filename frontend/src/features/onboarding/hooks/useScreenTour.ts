import { useState, useEffect, useCallback, useRef } from "react";
import type { TourStep } from "../types";
import {
  hasSeenTour,
  isTourEnabled,
  markTourAsSeen,
  subscribeTourPreferences,
} from "../tourStorage";

interface UseScreenTourOptions {
  screenId: string;
  steps: TourStep[];
  autoStartDelayMs?: number;
  canStart?: boolean;
}

export function useScreenTour({
  screenId,
  steps,
  autoStartDelayMs = 600,
  canStart = true,
}: UseScreenTourOptions) {
  const [isActive, setIsActive] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isGloballyEnabled, setIsGloballyEnabled] = useState(isTourEnabled());
  const timerRef = useRef<number | null>(null);

  // Synchronize with global preferences updates
  useEffect(() => {
    return subscribeTourPreferences((prefs) => {
      setIsGloballyEnabled(prefs.enabled);
      if (!prefs.enabled) {
        setIsActive(false);
      }
    });
  }, []);

  const startTour = useCallback(
    (force = false) => {
      if (!steps || steps.length === 0) return;
      if (!force && (!isGloballyEnabled || hasSeenTour(screenId))) return;

      setCurrentStepIndex(0);
      setIsActive(true);
    },
    [steps, isGloballyEnabled, screenId]
  );

  const stopTour = useCallback(
    (markSeen = true) => {
      setIsActive(false);
      if (markSeen) {
        markTourAsSeen(screenId);
      }
    },
    [screenId]
  );

  const nextStep = useCallback(() => {
    setCurrentStepIndex((prev) => Math.min(prev + 1, steps.length - 1));
  }, [steps.length]);

  const prevStep = useCallback(() => {
    setCurrentStepIndex((prev) => Math.max(prev - 1, 0));
  }, []);

  // Automatic trigger on first visit
  useEffect(() => {
    if (!canStart || !isGloballyEnabled || hasSeenTour(screenId) || steps.length === 0) {
      return;
    }

    timerRef.current = window.setTimeout(() => {
      startTour(false);
    }, autoStartDelayMs);

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
      }
    };
  }, [screenId, isGloballyEnabled, canStart, steps.length, autoStartDelayMs, startTour]);

  return {
    isActive,
    currentStepIndex,
    startTour,
    nextStep,
    prevStep,
    skipTour: () => stopTour(true),
    completeTour: () => stopTour(true),
    tourProps: {
      steps,
      currentStepIndex,
      isOpen: isActive,
      onNext: nextStep,
      onBack: prevStep,
      onSkip: () => stopTour(true),
      onComplete: () => stopTour(true),
    },
  };
}
