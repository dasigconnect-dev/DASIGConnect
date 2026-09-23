export type TourPlacement = "top" | "bottom" | "left" | "right" | "auto";

export interface TourStep {
  /**
   * Optional stable id, so a screen can set up the view a step needs (switch
   * a wizard step, open a panel) via useScreenTour's onStepChange.
   */
  id?: string;
  /** CSS selector targeting the DOM element to highlight */
  target: string;
  /** Title displayed in the guide card */
  title: string;
  /** Explanation and tips */
  description: string;
  /** Optional badge icon or Tabler icon class */
  icon?: string;
  /** Preferred card placement relative to the target */
  placement?: TourPlacement;
}

export interface TourPreferences {
  /** Master toggle to enable or disable automatic screen guides */
  enabled: boolean;
  /** List of screen IDs where the tour has already been seen or skipped */
  seenScreens: string[];
}
