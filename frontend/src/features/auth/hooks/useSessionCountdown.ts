import { useCallback, useEffect, useRef, useState } from "react";
import { formatTimer, getTokenExpiryMs } from "../utils";

const SESSION_WARNING_SECONDS = 5 * 60;

/**
 * Tracks the JWT's own `exp`: shows the expiry banner for the last 5 minutes
 * (unless dismissed) and calls `onExpired` when it runs out. `onExpired` is
 * read from a ref, so it always sees current state.
 */
export function useSessionCountdown(onExpired: () => void) {
  const [remaining, setRemaining] = useState(0);
  const timerRef = useRef<number | null>(null);
  const dismissedRef = useRef(false);
  const onExpiredRef = useRef(onExpired);
  useEffect(() => {
    onExpiredRef.current = onExpired;
  });

  const clearTimer = useCallback(() => {
    if (timerRef.current) window.clearInterval(timerRef.current);
    timerRef.current = null;
  }, []);

  useEffect(() => clearTimer, [clearTimer]);

  const start = useCallback(
    (token: string) => {
      clearTimer();
      dismissedRef.current = false;
      const expiresAt = getTokenExpiryMs(token);
      if (!expiresAt) return;

      const tick = () => {
        const secondsLeft = Math.ceil((expiresAt - Date.now()) / 1000);
        if (secondsLeft <= 0) {
          clearTimer();
          setRemaining(0);
          onExpiredRef.current();
          return;
        }
        if (secondsLeft <= SESSION_WARNING_SECONDS && !dismissedRef.current) {
          setRemaining(secondsLeft);
        }
      };
      tick();
      timerRef.current = window.setInterval(tick, 1000);
    },
    [clearTimer],
  );

  /** Stops the countdown entirely (sign-out, or the server already ended the session). */
  const stop = useCallback(() => {
    clearTimer();
    setRemaining(0);
    dismissedRef.current = false;
  }, [clearTimer]);

  /** Hides the banner for the rest of this session; expiry still fires. */
  const dismiss = useCallback(() => {
    dismissedRef.current = true;
    setRemaining(0);
  }, []);

  /** Hides the banner once (e.g. "Stay logged in" opened the modal). */
  const hideBanner = useCallback(() => setRemaining(0), []);

  return {
    showBanner: remaining > 0,
    bannerTime: formatTimer(remaining),
    start,
    stop,
    dismiss,
    hideBanner,
  };
}
