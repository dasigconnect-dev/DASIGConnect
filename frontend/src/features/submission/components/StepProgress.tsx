import type { ReactNode } from "react";
import type { ProgressStep } from "../types";
import { stepLabel } from "../utils";

export function StepProgress({
  steps,
  activeStep,
  hasMedia,
  isDetailsComplete,
  onStepClick,
}: {
  steps: Array<{
    id: ProgressStep;
    label: string;
    complete: boolean;
  }>;
  activeStep: ProgressStep;
  hasMedia: boolean;
  isDetailsComplete: boolean;
  onStepClick: (step: ProgressStep) => void;
}) {
  function isLocked(id: ProgressStep) {
    return (id === "details" && !hasMedia) || (id === "schedule" && (!hasMedia || !isDetailsComplete));
  }

  function lockTitle(id: ProgressStep) {
    if (id === "details" && !hasMedia) {
      return "Add media first before entering Post Details.";
    }
    if (id === "schedule" && !hasMedia) {
      return "Add media first before setting a schedule.";
    }
    if (id === "schedule" && !isDetailsComplete) {
      return "Complete Post Details first - title, event date, and caption are required.";
    }
    return undefined;
  }

  return (
    <div id="composer-step-nav" className="sub-step-nav" aria-label="Submission progress">
      {steps.map((step, index) => {
        const active = activeStep === step.id;
        const locked = isLocked(step.id);
        return (
          <button
            key={step.id}
            className={`sub-step ${active ? "active" : ""} ${step.complete ? "complete" : ""} ${locked ? "locked" : ""}`}
            type="button"
            title={lockTitle(step.id)}
            onClick={() => onStepClick(step.id)}
          >
            <span className="sub-step-circle">
              {locked ? (
                <i className="ti ti-lock"></i>
              ) : step.complete ? (
                <i className="ti ti-check"></i>
              ) : (
                index + 1
              )}
            </span>
            <span className="sub-step-text">
              <span>Step {index + 1}</span>
              {step.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

export function StepPanelActions({
  activeStep,
  hasMedia,
  isDetailsComplete,
  onStepChange,
  finalAction,
}: {
  activeStep: ProgressStep;
  hasMedia: boolean;
  isDetailsComplete: boolean;
  onStepChange: (step: ProgressStep) => void;
  /** Rendered in place of Next on the last step (the phone bottom bar passes Submit). */
  finalAction?: ReactNode;
}) {
  const order: ProgressStep[] = ["media", "details", "schedule"];
  const index = order.indexOf(activeStep);
  const previous = index > 0 ? order[index - 1] : null;
  const next = index < order.length - 1 ? order[index + 1] : null;
  const nextIsLocked =
    (next === "details" && !hasMedia) ||
    (next === "schedule" && (!hasMedia || !isDetailsComplete));
  const nextLockedTitle =
    next === "details" && !hasMedia
      ? "Add media first before entering Post Details."
      : next === "schedule" && !hasMedia
        ? "Add media first before setting a schedule."
        : next === "schedule" && !isDetailsComplete
          ? "Complete Post Details first - title, event date, and caption are required."
          : undefined;

  return (
    <div className="sub-step-panel-actions">
      {previous && (
        <button
          type="button"
          className="sub-step-panel-btn secondary"
          onClick={() => onStepChange(previous)}
        >
          <i className="ti ti-arrow-left"></i> Previous
        </button>
      )}
      {next ? (
        <button
          type="button"
          className={`sub-step-panel-btn ${nextIsLocked ? "locked" : "primary"}`}
          onClick={() => onStepChange(next)}
          title={nextLockedTitle ?? `Go to ${stepLabel(next)}`}
          aria-label={nextLockedTitle ? `Next: ${nextLockedTitle}` : `Next: ${stepLabel(next)}`}
        >
          {nextIsLocked && <i className="ti ti-lock"></i>}
          Next
          {!nextIsLocked && <i className="ti ti-arrow-right"></i>}
        </button>
      ) : finalAction ? (
        finalAction
      ) : (
        <span className="sub-step-panel-ready">
          <i className="ti ti-check"></i> Final step
        </span>
      )}
    </div>
  );
}
