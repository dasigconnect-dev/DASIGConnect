import { useCallback, useEffect, useMemo, useRef } from "react";
import { logTaskComplete, logTaskStart } from "../api/telemetryApi";

/**
 * Tracks start, error count, and completion time-on-task for a core role task
 * so first-attempt completion and time-on-task are measured directly by the system.
 */
export function useTaskTelemetry(taskName: string, autoStart = true) {
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

  useEffect(() => {
    if (autoStart && !startedLoggedRef.current) {
      startTask();
    }
  }, [autoStart, startTask]);

  const recordError = useCallback(() => {
    errorCountRef.current += 1;
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
    },
    [taskName],
  );

  return useMemo(
    () => ({ startTask, recordError, completeTask }),
    [completeTask, recordError, startTask],
  );
}
