import { useCallback, useEffect, useMemo, useRef } from "react";
import { logTaskComplete, logTaskStart } from "../api/telemetryApi";

/** Page views shorter than this are treated as accidental (redirects, StrictMode remounts). */
export const MIN_PAGE_VIEW_MS = 1000;

/**
 * Builds a role-scoped task name such as `ADMIN_VIEW_ANALYTICS`. The backend
 * upper-cases and trims task names and the column is VARCHAR(64).
 */
export function roleTaskName(role: string | null | undefined, task: string): string {
  const prefix = (role ?? "user").trim().toUpperCase() || "USER";
  return `${prefix}_${task}`.slice(0, 64);
}

export interface TaskTelemetryOptions {
  /**
   * After a completion, restart the client-side clock (and error count) so a
   * second action on the same screen is timed from the previous completion
   * instead of from first page load. No extra server row is opened: the server
   * creates the next completed row from the client-reported duration.
   */
  rearmAfterComplete?: boolean;
}

/**
 * Tracks start, error count, and completion time-on-task for a core role task
 * so first-attempt completion and time-on-task are measured directly by the system.
 */
export function useTaskTelemetry(
  taskName: string,
  autoStart = true,
  options: TaskTelemetryOptions = {},
) {
  const rearmAfterComplete = options.rearmAfterComplete ?? false;
  const startedAtMsRef = useRef<number | null>(null);
  const errorCountRef = useRef<number>(0);
  const startedLoggedRef = useRef<boolean>(false);

  const startTask = useCallback(
    (targetEntityId?: string | null) => {
      startedAtMsRef.current = Date.now();
      errorCountRef.current = 0;
      startedLoggedRef.current = true;
      void logTaskStart(taskName, targetEntityId).catch(() => {
        // Non-blocking telemetry
      });
    },
    [taskName],
  );

  /** Starts the task only if it is not already running (safe to call repeatedly). */
  const ensureStarted = useCallback(
    (targetEntityId?: string | null) => {
      if (startedLoggedRef.current) return;
      startTask(targetEntityId);
    },
    [startTask],
  );

  useEffect(() => {
    if (autoStart && !startedLoggedRef.current) {
      startTask();
    }
  }, [autoStart, startTask]);

  const recordError = useCallback((count = 1) => {
    errorCountRef.current += Math.max(0, count);
  }, []);

  const completeTask = useCallback(
    (targetEntityId?: string | null) => {
      const startedAtMs = startedAtMsRef.current;
      const durationMs = startedAtMs === null ? 0 : Math.max(0, Date.now() - startedAtMs);
      const errors = errorCountRef.current;
      void logTaskComplete({
        taskName,
        targetEntityId,
        durationMs,
        errorCount: errors,
        firstAttemptSuccess: errors === 0,
      }).catch(() => {
        // Non-blocking telemetry
      });
      if (rearmAfterComplete) {
        startedAtMsRef.current = Date.now();
        errorCountRef.current = 0;
      } else {
        startedLoggedRef.current = false;
      }
    },
    [rearmAfterComplete, taskName],
  );

  return useMemo(
    () => ({ startTask, ensureStarted, recordError, completeTask }),
    [completeTask, ensureStarted, recordError, startTask],
  );
}

/**
 * View-only tasks (open a page and read it): logs a start when the page mounts
 * and a completion with the dwell time when the user leaves it. Dwell shorter
 * than {@link MIN_PAGE_VIEW_MS} is not completed, so redirects do not count.
 * A tab being closed outright is not captured (no reliable unmount signal).
 */
export function usePageViewTelemetry(taskName: string, enabled = true) {
  useEffect(() => {
    if (!enabled) return undefined;
    const startedAtMs = Date.now();
    void logTaskStart(taskName).catch(() => {
      // Non-blocking telemetry
    });
    return () => {
      const durationMs = Date.now() - startedAtMs;
      if (durationMs < MIN_PAGE_VIEW_MS) return;
      void logTaskComplete({
        taskName,
        durationMs,
        errorCount: 0,
        firstAttemptSuccess: true,
      }).catch(() => {
        // Non-blocking telemetry
      });
    };
  }, [enabled, taskName]);
}
