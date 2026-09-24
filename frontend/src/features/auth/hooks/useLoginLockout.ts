import { useCallback, useEffect, useRef, useState } from "react";

export const LOCKOUT_LIMIT = 5;
const LOCKOUT_SECONDS = 15 * 60;

/**
 * Client-side failed-login counter: after LOCKOUT_LIMIT failures the form is
 * locked for 15 minutes (the backend's AccountLockout is the real guard).
 */
export function useLoginLockout() {
  const [attempts, setAttempts] = useState(0);
  const [lockRemaining, setLockRemaining] = useState(0);
  const timerRef = useRef<number | null>(null);

  const clearTimer = useCallback(() => {
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
  }, []);

  useEffect(() => clearTimer, [clearTimer]);

  const startLockout = useCallback(() => {
    clearTimer();
    setLockRemaining(LOCKOUT_SECONDS);
    const id = window.setInterval(() => {
      setLockRemaining((prev) => {
        if (prev <= 1) {
          window.clearInterval(id);
          timerRef.current = null;
          setAttempts(0);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    timerRef.current = id;
  }, [clearTimer]);

  /** Counts a failed attempt; returns the new count and locks at the limit. */
  const recordFailure = useCallback(
    (current: number) => {
      const next = current + 1;
      setAttempts(next);
      if (next >= LOCKOUT_LIMIT) startLockout();
      return next;
    },
    [startLockout],
  );

  const reset = useCallback(() => {
    clearTimer();
    setAttempts(0);
    setLockRemaining(0);
  }, [clearTimer]);

  return { attempts, lockRemaining, recordFailure, reset };
}
