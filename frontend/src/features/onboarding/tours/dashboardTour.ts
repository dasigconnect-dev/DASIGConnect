import type { TourStep } from "../types";

export const dashboardTourSteps: TourStep[] = [
  {
    target: "#dash-greeting",
    title: "Welcome to DASIGConnect",
    description: "Your personalized command center for managing event media, social content, and approval workflows.",
    icon: "ti ti-home",
    placement: "bottom",
  },
  {
    target: "#stat-grid",
    title: "Real-Time Metrics",
    description: "Monitor live counts for active events, submissions under review, and published content across your network.",
    icon: "ti ti-chart-bar",
    placement: "bottom",
  },
  {
    target: "#action-grid",
    title: "Quick Actions",
    description: "Instantly launch key workflows like drafting new event content, inspecting the review queue, or checking scheduled posts.",
    icon: "ti ti-bolt",
    placement: "top",
  },
  {
    target: ".section-header-row",
    title: "Recent Activity Stream",
    description: "Track recent submission changes, approvals, revision requests, and publishing status in real time.",
    icon: "ti ti-history",
    placement: "top",
  },
];
