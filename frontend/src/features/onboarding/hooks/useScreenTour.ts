import { useState, useEffect, useCallback, useRef } from "react";
import type { TourStep } from "../types";
import {
  hasSeenTour,
  isTourEnabled,
  isTourPreferencesLoaded,
  markTourAsSeen,
  subscribeTourPreferences,
} from "../tourStorage";

interface UseScreenTourOptions {
  screenId: string;
  steps: TourStep[];
  autoStartDelayMs?: number;
  canStart?: boolean;
  /**
   * Called with the active step whenever it changes, and with null when the
   * tour closes — lets a screen show the UI a step talks about (and put it
   * back afterwards).
   */
  onStepChange?: (step: TourStep | null) => void;
}

let activeTourScreenId: string | null = null;
const tourActiveSubscribers = new Set<(activeId: string | null) => void>();

function setActiveTourScreenId(id: string | null) {
  if (activeTourScreenId === id) return;
  activeTourScreenId = id;
  tourActiveSubscribers.forEach((cb) => cb(activeTourScreenId));
}

export function getActiveTourScreenId(): string | null {
  return activeTourScreenId;
}

export function useScreenTour({
  screenId,
  steps,
  autoStartDelayMs = 600,
  canStart = true,
  onStepChange,
}: UseScreenTourOptions) {
  const [isActive, setIsActive] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isGloballyEnabled, setIsGloballyEnabled] = useState(isTourEnabled());
  const [preferencesLoaded, setPreferencesLoaded] = useState(isTourPreferencesLoaded());
  const timerRef = useRef<number | null>(null);
  const onStepChangeRef = useRef(onStepChange);
  useEffect(() => {
    onStepChangeRef.current = onStepChange;
  });
  const activeStep = isActive ? steps[currentStepIndex] ?? null : null;
  useEffect(() => {
    onStepChangeRef.current?.(activeStep);
  }, [activeStep]);

  // Synchronize with global preferences updates (hydration from the account
  // profile, a mutation, or a logout reset).
  useEffect(() => {
    return subscribeTourPreferences((prefs) => {
      setIsGloballyEnabled(prefs.enabled);
      setPreferencesLoaded(isTourPreferencesLoaded());
      if (!prefs.enabled) {
        setIsActive(false);
      }
    });
  }, []);

  // Guarantee that only one tour can be active at a time across the entire app
  useEffect(() => {
    const handleActiveTourChange = (activeId: string | null) => {
      if (activeId !== null && activeId !== screenId) {
        setIsActive(false);
      }
    };
    tourActiveSubscribers.add(handleActiveTourChange);
    return () => {
      tourActiveSubscribers.delete(handleActiveTourChange);
      if (activeTourScreenId === screenId) {
        setActiveTourScreenId(null);
      }
    };
  }, [screenId]);

  const startTour = useCallback(
    (force = false) => {
      if (!steps || steps.length === 0) return;
      if (!force && (!isGloballyEnabled || hasSeenTour(screenId))) return;
      if (!force && activeTourScreenId !== null && activeTourScreenId !== screenId) return;

      setActiveTourScreenId(screenId);
      setCurrentStepIndex(0);
      setIsActive(true);
    },
    [steps, isGloballyEnabled, screenId]
  );

  const stopTour = useCallback(
    (markSeen = true) => {
      if (activeTourScreenId === screenId) {
        setActiveTourScreenId(null);
      }
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

  // Automatic trigger on first visit. Gated on preferencesLoaded so this
  // can't fire before the account's real "seen" state has loaded — without
  // it, a guide the account already dismissed would flash on every login
  // while the profile fetch is still in flight.
  useEffect(() => {
    if (
      !canStart ||
      !preferencesLoaded ||
      !isGloballyEnabled ||
      hasSeenTour(screenId) ||
      steps.length === 0 ||
      (activeTourScreenId !== null && activeTourScreenId !== screenId)
    ) {
      return;
    }

    timerRef.current = window.setTimeout(() => {
      if (activeTourScreenId !== null && activeTourScreenId !== screenId) return;
      startTour(false);
    }, autoStartDelayMs);

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
      }
    };
  }, [screenId, isGloballyEnabled, preferencesLoaded, canStart, steps.length, autoStartDelayMs, startTour]);

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
