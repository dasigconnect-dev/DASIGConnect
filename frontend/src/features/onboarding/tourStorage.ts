import type { TourPreferences } from "./types";

const TOUR_STORAGE_KEY = "dasig_tour_preferences";
const TOUR_CHANGE_EVENT = "dasig_tour_preferences_changed";

const DEFAULT_PREFERENCES: TourPreferences = {
  enabled: true,
  seenScreens: [],
};

export function getTourPreferences(): TourPreferences {
  try {
    const raw = localStorage.getItem(TOUR_STORAGE_KEY);
    if (!raw) return DEFAULT_PREFERENCES;
    const parsed = JSON.parse(raw);
    return {
      enabled: typeof parsed.enabled === "boolean" ? parsed.enabled : true,
      seenScreens: Array.isArray(parsed.seenScreens) ? parsed.seenScreens : [],
    };
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

export function setTourPreferences(prefs: TourPreferences): void {
  try {
    localStorage.setItem(TOUR_STORAGE_KEY, JSON.stringify(prefs));
    window.dispatchEvent(new CustomEvent(TOUR_CHANGE_EVENT, { detail: prefs }));
  } catch (error) {
    console.error("Failed to save tour preferences:", error);
  }
}

export function isTourEnabled(): boolean {
  return getTourPreferences().enabled;
}

export function hasSeenTour(screenId: string): boolean {
  const prefs = getTourPreferences();
  return prefs.seenScreens.includes(screenId);
}

export function markTourAsSeen(screenId: string): void {
  const prefs = getTourPreferences();
  if (!prefs.seenScreens.includes(screenId)) {
    setTourPreferences({
      ...prefs,
      seenScreens: [...prefs.seenScreens, screenId],
    });
  }
}

export function resetAllTours(): void {
  setTourPreferences({
    enabled: true,
    seenScreens: [],
  });
}

export function toggleToursEnabled(enabled: boolean): void {
  const prefs = getTourPreferences();
  setTourPreferences({
    ...prefs,
    enabled,
  });
}

/** Subscribe to preferences updates */
export function subscribeTourPreferences(callback: (prefs: TourPreferences) => void): () => void {
  const handler = (event: Event) => {
    const customEvent = event as CustomEvent<TourPreferences>;
    callback(customEvent.detail || getTourPreferences());
  };

  window.addEventListener(TOUR_CHANGE_EVENT, handler);
  window.addEventListener("storage", handler);

  return () => {
    window.removeEventListener(TOUR_CHANGE_EVENT, handler);
    window.removeEventListener("storage", handler);
  };
}
