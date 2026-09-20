import { updateTourPreferences } from "../../api/authApi";
import type { TourPreferences } from "./types";

const TOUR_CHANGE_EVENT = "dasig_tour_preferences_changed";

/**
 * In-memory cache of the current account's onboarding-guide preferences.
 * Previously this lived in localStorage (per browser); it's now seeded from
 * the account's own profile (see App.tsx's hydrateTourPreferences calls) so
 * a guide dismissed once never reappears regardless of browser/device/cleared
 * storage — matching "seen once per account," not "seen once per browser."
 */
let state: TourPreferences = { enabled: true, seenScreens: [] };
/** False until the first hydrate call from a loaded profile — useScreenTour
 * must not decide "not seen yet" before it actually knows the account's real
 * state, or it would flash a guide the account already dismissed. */
let loaded = false;

function emit(): void {
  window.dispatchEvent(new CustomEvent(TOUR_CHANGE_EVENT, { detail: state }));
}

/** Seeds the cache from a loaded account profile. Call once per successful profile fetch. */
export function hydrateTourPreferences(profile: { toursEnabled?: boolean; tourSeenScreens?: string[] }): void {
  state = {
    enabled: profile.toursEnabled ?? true,
    seenScreens: profile.tourSeenScreens ?? [],
  };
  loaded = true;
  emit();
}

/** Call on logout — clears the cache so the next account's hydrate call starts clean. */
export function resetTourPreferencesCache(): void {
  state = { enabled: true, seenScreens: [] };
  loaded = false;
  emit();
}

export function isTourPreferencesLoaded(): boolean {
  return loaded;
}

export function getTourPreferences(): TourPreferences {
  return state;
}

function persist(next: TourPreferences): void {
  state = next;
  emit();
  updateTourPreferences({ enabled: next.enabled, seenScreens: next.seenScreens }).catch((error: unknown) => {
    console.error("Failed to save tour preferences:", error);
  });
}

export function isTourEnabled(): boolean {
  return state.enabled;
}

export function hasSeenTour(screenId: string): boolean {
  return state.seenScreens.includes(screenId);
}

export function markTourAsSeen(screenId: string): void {
  if (state.seenScreens.includes(screenId)) return;
  persist({ ...state, seenScreens: [...state.seenScreens, screenId] });
}

export function resetAllTours(): void {
  persist({ enabled: true, seenScreens: [] });
}

export function toggleToursEnabled(enabled: boolean): void {
  persist({ ...state, enabled });
}

/** Subscribe to preferences updates (hydration, mutation, or logout reset). */
export function subscribeTourPreferences(callback: (prefs: TourPreferences) => void): () => void {
  const handler = (event: Event) => {
    const customEvent = event as CustomEvent<TourPreferences>;
    callback(customEvent.detail || state);
  };

  window.addEventListener(TOUR_CHANGE_EVENT, handler);

  return () => {
    window.removeEventListener(TOUR_CHANGE_EVENT, handler);
  };
}
