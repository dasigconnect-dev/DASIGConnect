import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { requestPasswordReset, resetPassword as resetPasswordRequest } from "../../../api/authApi";
import { useToast } from "../../../context/ToastContext";
import { firstPasswordError } from "../../../lib/passwordPolicy";
import { readPasswordResetToken } from "../../../utils/passwordResetLink";
import { getApiErrorMessage, isPasswordResetPath } from "../utils";

/** Forgot-password request + reset-password form (both reset routes share it). */
export function usePasswordResetFlow() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotSentEmail, setForgotSentEmail] = useState("");
  const [forgotLoading, setForgotLoading] = useState(false);

  const [resetToken, setResetToken] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const resetTokenRef = useRef<string | null>(null);

  function adoptToken(token: string | null) {
    resetTokenRef.current = token;
    setResetToken(token);
    setError(token ? "" : "Reset token is missing or invalid.");
    setSuccess(false);
    setPassword("");
    setConfirmPassword("");
  }

  // Read the token whenever a reset route is opened.
  useEffect(() => {
    if (!isPasswordResetPath(location.pathname)) return;
    const token = readPasswordResetToken(window.location.href);
    resetTokenRef.current = token;
    let active = true;
    queueMicrotask(() => {
      if (active) adoptToken(token);
    });
    return () => {
      active = false;
    };
  }, [location.pathname, location.search]);

  // Mobile in-app browsers may reuse this SPA instance for a newly opened
  // email link without a route change; re-read the live URL when the page
  // is shown or focused again.
  useEffect(() => {
    function syncResetTokenFromLiveUrl() {
      if (!isPasswordResetPath(window.location.pathname)) return;
      const token = readPasswordResetToken(window.location.href);
      if (token === resetTokenRef.current) return;
      adoptToken(token);
    }
    function handleVisibilityChange() {
      if (document.visibilityState === "visible") syncResetTokenFromLiveUrl();
    }
    window.addEventListener("pageshow", syncResetTokenFromLiveUrl);
    window.addEventListener("focus", syncResetTokenFromLiveUrl);
    window.addEventListener("popstate", syncResetTokenFromLiveUrl);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
      window.removeEventListener("pageshow", syncResetTokenFromLiveUrl);
      window.removeEventListener("focus", syncResetTokenFromLiveUrl);
      window.removeEventListener("popstate", syncResetTokenFromLiveUrl);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  async function submitForgot() {
    const email = forgotEmail.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      toast.error("Enter a valid email address.");
      return;
    }
    setForgotLoading(true);
    try {
      await requestPasswordReset(email);
    } catch {
      // Intentionally silent to avoid email enumeration.
    } finally {
      setForgotLoading(false);
      setForgotSentEmail(email);
      navigate("/forgot-password-sent");
    }
  }

  async function submitReset() {
    // Read the live URL at submission time so an older token retained in
    // state (reused SPA instance) is never sent to the backend.
    const liveToken = isPasswordResetPath(window.location.pathname)
      ? readPasswordResetToken(window.location.href)
      : resetToken;
    if (!liveToken) {
      setError("Reset token is missing or invalid.");
      return;
    }
    if (liveToken !== resetTokenRef.current) {
      resetTokenRef.current = liveToken;
      setResetToken(liveToken);
    }
    const passwordError = firstPasswordError(password);
    if (passwordError) {
      setError(passwordError);
      return;
    }
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await resetPasswordRequest(liveToken, password);
      setSuccess(true);
      setPassword("");
      setConfirmPassword("");
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, "Password reset failed."));
    } finally {
      setLoading(false);
    }
  }

  return {
    forgot: {
      email: forgotEmail,
      setEmail: setForgotEmail,
      sentEmail: forgotSentEmail,
      loading: forgotLoading,
      submit: submitForgot,
    },
    reset: {
      password,
      setPassword,
      confirmPassword,
      setConfirmPassword,
      showPassword,
      toggleShowPassword: () => setShowPassword((v) => !v),
      showConfirmPassword,
      toggleShowConfirmPassword: () => setShowConfirmPassword((v) => !v),
      loading,
      error,
      success,
      submit: submitReset,
    },
  };
}
