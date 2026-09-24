import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { acceptInvitation, login, logout as logoutRequest, setAuthToken } from "../../../api/authApi";
import { isRequestDeadlineError } from "../../../api/requestPolicy";
import type { User } from "../../../types/auth.types";
import { useToast } from "../../../context/ToastContext";
import { firstPasswordError } from "../../../lib/passwordPolicy";
import { clearAppCaches } from "../../../lib/appCache";
import { appQueryClient, clearAuthenticatedQueryCache } from "../../../lib/queryClient";
import { seedCurrentProfile } from "../../../hooks/useCurrentProfile";
import { hydrateTourPreferences, resetTourPreferencesCache } from "../../onboarding/tourStorage";
import {
  TOKEN_STORAGE_KEY,
  USER_STORAGE_KEY,
  getApiErrorMessage,
  isAlreadyUsedInviteError,
  isExpiredInviteError,
  isPasswordResetPath,
  isValidProfileName,
  loadCurrentUser,
  normalizeProfileName,
} from "../utils";
import { LOCKOUT_LIMIT, useLoginLockout } from "./useLoginLockout";
import { useSessionCountdown } from "./useSessionCountdown";
import { usePasswordResetFlow } from "./usePasswordResetFlow";
import { useInviteForm } from "./useInviteForm";

const LOGIN_SPLASH_VISIBLE_MS = 500;

function hasSavedSession() {
  return Boolean(localStorage.getItem(TOKEN_STORAGE_KEY) && localStorage.getItem(USER_STORAGE_KEY));
}

/**
 * The app's whole auth/session lifecycle: restoring a saved session, the
 * login form (with lockout), the session-expired re-login modal, the expiry
 * banner, invite activation, password reset, and sign-out. App.tsx only
 * renders routes from what this returns.
 */
export function useAuthSession() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  // ── Session ─────────────────────────────────────────────────────────────
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  // Nothing saved means there's nothing to verify — ready immediately.
  const [authReady, setAuthReady] = useState(() => !hasSavedSession());
  const [showDropdown, setShowDropdown] = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);
  // Where a signed-out visit to a signed-in page goes: /login normally, but "/"
  // right after signing out. navigate() is a low-priority transition (and the
  // landing page is lazy), so clearing the user would otherwise let the still-
  // mounted layout's /login redirect win. Cleared once "/" is reached.
  const [afterSignOutPath, setAfterSignOutPath] = useState<string | null>(null);
  const profileRequestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const profileRequestIdRef = useRef(0);
  // Bumped by every sign-in/out, so a slower, superseded flow can't apply its result.
  const authenticationFlowIdRef = useRef(0);

  // ── Login form ──────────────────────────────────────────────────────────
  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [loginLoading, setLoginLoading] = useState(false);
  const lockout = useLoginLockout();

  const [showSplash, setShowSplash] = useState(false);
  const [splashUser, setSplashUser] = useState<User | null>(null);
  const splashTimerRef = useRef<number | null>(null);

  // ── Session-expired modal (re-login in place) ───────────────────────────
  const [showSessionModal, setShowSessionModal] = useState(false);
  const [modalEmail, setModalEmail] = useState("");
  const [modalPassword, setModalPassword] = useState("");
  const [modalError, setModalError] = useState<string | null>(null);
  const [modalLoginLoading, setModalLoginLoading] = useState(false);
  const [showModalPassword, setShowModalPassword] = useState(false);

  const passwordReset = usePasswordResetFlow();
  const invite = useInviteForm();

  function openSessionModal() {
    setModalEmail(currentUser?.email || loginEmail);
    setShowSessionModal(true);
  }
  const countdown = useSessionCountdown(openSessionModal);

  function cancelPendingProfileRequest() {
    profileRequestIdRef.current += 1;
    profileRequestRef.current?.controller.abort();
    profileRequestRef.current = null;
  }

  /**
   * Drops the stored session and every in-memory cache. `keepUser` leaves the
   * current user in place while a replacement session is being verified (the
   * session-expired modal): clearing it would make every signed-in route
   * redirect to /login mid-re-login and strand the user there.
   */
  async function clearLocalAuthentication({ keepUser = false } = {}) {
    cancelPendingProfileRequest();
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    setAuthToken(null);
    if (!keepUser) setCurrentUser(null);
    resetTourPreferencesCache();
    await clearAuthenticatedQueryCache();
    clearAppCaches();
  }

  async function loadVerifiedCurrentUser(email: string) {
    cancelPendingProfileRequest();
    const request = { id: profileRequestIdRef.current, controller: new AbortController() };
    profileRequestRef.current = request;
    try {
      const result = await loadCurrentUser(email, request.controller.signal);
      if (profileRequestRef.current?.id !== request.id) {
        throw new DOMException("Superseded profile request.", "AbortError");
      }
      seedCurrentProfile(appQueryClient, result.profile);
      hydrateTourPreferences(result.profile);
      return result.user;
    } finally {
      if (profileRequestRef.current?.id === request.id) {
        profileRequestRef.current = null;
      }
    }
  }

  /**
   * The shared end of every sign-in (login form, session modal, invite
   * activation): verify the profile with the new token, persist the session,
   * and start the expiry countdown. Returns null if a newer flow took over.
   */
  async function completeSignIn(token: string, email: string, flowId: number): Promise<User | null> {
    setAuthToken(token);
    const user = await loadVerifiedCurrentUser(email);
    if (authenticationFlowIdRef.current !== flowId) return null;
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
    setCurrentUser(user);
    countdown.start(token);
    return user;
  }

  async function refreshCurrentUserProfile() {
    if (!currentUser) return;
    const user = await loadVerifiedCurrentUser(currentUser.email);
    setCurrentUser(user);
  }

  useEffect(() => {
    return () => {
      profileRequestIdRef.current += 1;
      profileRequestRef.current?.controller.abort();
      profileRequestRef.current = null;
      if (splashTimerRef.current) window.clearTimeout(splashTimerRef.current);
    };
  }, []);

  // Restore a saved session on load, verifying it against /me.
  const startSessionCountdown = countdown.start;
  useEffect(() => {
    const savedToken = localStorage.getItem(TOKEN_STORAGE_KEY);
    const savedUser = localStorage.getItem(USER_STORAGE_KEY);
    if (!savedToken || !savedUser) return;

    let active = true;
    const controller = new AbortController();
    setAuthToken(savedToken);
    queueMicrotask(() => {
      void (async () => {
        let parsedUser: User | null;
        try {
          parsedUser = JSON.parse(savedUser) as User;
        } catch {
          parsedUser = null;
        }
        if (!parsedUser) {
          localStorage.removeItem(TOKEN_STORAGE_KEY);
          localStorage.removeItem(USER_STORAGE_KEY);
          setAuthToken(null);
          if (active) {
            setCurrentUser(null);
            setAuthReady(true);
          }
          return;
        }
        try {
          const result = await loadCurrentUser(parsedUser.email, controller.signal);
          if (!active) return;
          seedCurrentProfile(appQueryClient, result.profile);
          hydrateTourPreferences(result.profile);
          localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(result.user));
          setCurrentUser(result.user);
          startSessionCountdown(savedToken);
        } catch (err: unknown) {
          if (!active) return;
          const status = (err as { response?: { status?: number } })?.response?.status;
          if (status === 401) {
            // The server explicitly rejected the token — genuinely invalid/expired.
            localStorage.removeItem(TOKEN_STORAGE_KEY);
            localStorage.removeItem(USER_STORAGE_KEY);
            setAuthToken(null);
            setCurrentUser(null);
          } else {
            // Couldn't verify (network blip, timeout, backend cold start, a
            // dropped CORS preflight, etc.) — the token itself may still be
            // valid, so don't force a logout just because this one request
            // failed. Fall back to the cached profile; a genuine 401 on any
            // later request still triggers the normal session-expired flow
            // via the axios interceptor in authApi.ts.
            setCurrentUser(parsedUser);
            startSessionCountdown(savedToken);
          }
        } finally {
          if (active) setAuthReady(true);
        }
      })();
    });

    return () => {
      active = false;
      controller.abort();
    };
  }, [startSessionCountdown]);

  // Enter submits the form on the current auth screen. The handler is read
  // from a ref so it always sees the latest form state.
  const enterKeyActionRef = useRef<() => void>(() => {});
  useEffect(() => {
    enterKeyActionRef.current = () => {
      if (location.pathname === "/login") {
        void handleLogin();
      } else if (location.pathname === "/forgot-password") {
        void passwordReset.forgot.submit();
      } else if (isPasswordResetPath(location.pathname)) {
        void passwordReset.reset.submit();
      } else if (showSessionModal && !modalLoginLoading && !logoutLoading) {
        void handleModalLogin();
      }
    };
  });
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Enter") enterKeyActionRef.current();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, []);

  // The countdown tracks the JWT's own `exp`, but a session can also end
  // server-side first (revocation, a password reset, a session_version bump,
  // a throttled background tab). The axios interceptor in authApi.ts
  // dispatches this event on any real 401.
  const stopSessionCountdown = countdown.stop;
  useEffect(() => {
    function handleSessionExpiredEvent() {
      if (!currentUser || showSessionModal) return;
      stopSessionCountdown();
      setModalEmail(currentUser.email || loginEmail);
      setShowSessionModal(true);
    }
    window.addEventListener("dasigconnect:session-expired", handleSessionExpiredEvent);
    return () => window.removeEventListener("dasigconnect:session-expired", handleSessionExpiredEvent);
  }, [currentUser, loginEmail, showSessionModal, stopSessionCountdown]);

  useEffect(() => {
    if (!afterSignOutPath || location.pathname !== afterSignOutPath) return;
    queueMicrotask(() => setAfterSignOutPath(null));
  }, [afterSignOutPath, location.pathname]);

  function resetLoginState() {
    setLoginPassword("");
    setLoginError("");
    lockout.reset();
  }

  function showLoginSplash(user: User) {
    if (splashTimerRef.current) window.clearTimeout(splashTimerRef.current);
    setSplashUser(user);
    setShowSplash(true);
    splashTimerRef.current = window.setTimeout(() => {
      setShowSplash(false);
      splashTimerRef.current = null;
    }, LOGIN_SPLASH_VISIBLE_MS);
  }

  function stopLoginSplash() {
    if (splashTimerRef.current) window.clearTimeout(splashTimerRef.current);
    splashTimerRef.current = null;
    setShowSplash(false);
  }

  async function handleLogin() {
    if (lockout.lockRemaining > 0) return;
    const flowId = ++authenticationFlowIdRef.current;
    setLoginLoading(true);
    setLoginError("");
    // Drop any in-memory caches from a prior session on this tab so a new
    // account never sees the previous user's role-scoped data.
    await clearLocalAuthentication();
    if (authenticationFlowIdRef.current !== flowId) return;
    const email = loginEmail.trim().toLowerCase();
    let loginCompleted = false;
    try {
      const response = await login(email, loginPassword);
      if (authenticationFlowIdRef.current !== flowId) return;
      loginCompleted = true;
      const user = await completeSignIn(response.data.accessToken, email, flowId);
      if (!user) return;
      showLoginSplash(user);
      navigate("/dashboard");
      resetLoginState();
    } catch (err: unknown) {
      if (authenticationFlowIdRef.current !== flowId) return;
      if (loginCompleted) {
        await clearLocalAuthentication();
        setLoginError(
          isRequestDeadlineError(err)
            ? "Session verification timed out. Please sign in again."
            : "Sign-in succeeded, but the session could not be verified. Please try again.",
        );
        return;
      }
      const attempts = lockout.recordFailure(lockout.attempts);
      if (attempts < LOCKOUT_LIMIT) {
        setLoginError(
          getApiErrorMessage(err, "") ||
            `Invalid credentials. ${LOCKOUT_LIMIT - attempts} attempts remaining before lockout.`,
        );
      }
    } finally {
      if (authenticationFlowIdRef.current === flowId) setLoginLoading(false);
    }
  }

  async function handleModalLogin() {
    if (modalLoginLoading || logoutLoading) return;
    setModalLoginLoading(true);
    setModalError(null);
    const flowId = ++authenticationFlowIdRef.current;
    const email = modalEmail.trim().toLowerCase();
    let loginCompleted = false;
    try {
      const response = await login(email, modalPassword);
      if (authenticationFlowIdRef.current !== flowId) return;
      loginCompleted = true;
      await clearLocalAuthentication({ keepUser: true });
      const user = await completeSignIn(response.data.accessToken, email, flowId);
      if (!user) return;
      setShowSessionModal(false);
      setModalError(null);
      setModalPassword("");
    } catch (err: unknown) {
      if (authenticationFlowIdRef.current !== flowId) return;
      if (loginCompleted) {
        await clearLocalAuthentication();
        setShowSessionModal(false);
        navigate("/login");
        toast.error("Session verification failed. Please sign in again.");
        return;
      }
      setModalError(getApiErrorMessage(err, "Invalid credentials. Please try again."));
    } finally {
      if (authenticationFlowIdRef.current === flowId) setModalLoginLoading(false);
    }
  }

  async function handleInviteActivate() {
    if (!invite.token) return;
    const firstName = normalizeProfileName(invite.firstName);
    const lastName = normalizeProfileName(invite.lastName);
    if (!isValidProfileName(firstName) || !isValidProfileName(lastName)) {
      toast.error("Please enter a valid first and last name.");
      return;
    }
    const passwordError = firstPasswordError(invite.password, [invite.email, firstName, lastName]);
    if (passwordError) {
      toast.error(passwordError);
      return;
    }
    invite.setLoading(true);
    const flowId = ++authenticationFlowIdRef.current;
    let activationCompleted = false;
    try {
      const response = await acceptInvitation({
        token: invite.token,
        firstName,
        lastName,
        password: invite.password,
      });
      if (authenticationFlowIdRef.current !== flowId) return;
      activationCompleted = true;
      await clearLocalAuthentication();
      const user = await completeSignIn(response.data.accessToken, invite.email.trim().toLowerCase(), flowId);
      if (!user) return;
      toast.success("Account activated. Welcome to DASIGConnect.");
      invite.setState("success");
      navigate("/dashboard");
    } catch (err: unknown) {
      if (authenticationFlowIdRef.current !== flowId) return;
      if (activationCompleted) {
        await clearLocalAuthentication();
        invite.setState("success");
        navigate("/login");
        toast.error("Account activated, but session verification failed. Please sign in.");
        return;
      }
      const message = getApiErrorMessage(err, "We could not activate this invitation. Please try again.");
      toast.error(message);
      if (isAlreadyUsedInviteError(message)) {
        invite.setState("already");
      } else if (isExpiredInviteError(message)) {
        invite.setState("expired");
      }
    } finally {
      if (authenticationFlowIdRef.current === flowId) invite.setLoading(false);
    }
  }

  async function handleLogout() {
    if (logoutLoading) return;
    authenticationFlowIdRef.current += 1;
    setLogoutLoading(true);
    try {
      try {
        await logoutRequest();
      } catch {
        // Server revocation is best-effort; the request has a short deadline.
      }
      setAfterSignOutPath("/");
      navigate("/");
      await clearLocalAuthentication();
      setShowDropdown(false);
      setShowSessionModal(false);
      stopLoginSplash();
      countdown.stop();
      resetLoginState();
      setLoginLoading(false);
      setModalLoginLoading(false);
      invite.setLoading(false);
      toast.info("You have been signed out.");
    } finally {
      setLogoutLoading(false);
    }
  }

  function handleStayLoggedIn() {
    setModalEmail(currentUser?.email || "");
    setShowSessionModal(true);
    countdown.hideBanner();
  }

  return {
    currentUser,
    authReady,
    /** Where a signed-out visit to a signed-in page should redirect. */
    signedOutRedirect: afterSignOutPath ?? "/login",
    refreshCurrentUserProfile,
    splash: { user: splashUser, visible: showSplash },
    login: {
      email: loginEmail,
      setEmail: setLoginEmail,
      password: loginPassword,
      setPassword: setLoginPassword,
      showPassword: showLoginPassword,
      toggleShowPassword: () => setShowLoginPassword((v) => !v),
      error: loginError,
      loading: loginLoading,
      attempts: lockout.attempts,
      lockRemaining: lockout.lockRemaining,
      submit: handleLogin,
    },
    passwordReset,
    invite: { ...invite, activate: handleInviteActivate },
    layout: {
      showBanner: countdown.showBanner,
      bannerTime: countdown.bannerTime,
      dismissBanner: countdown.dismiss,
      stayLoggedIn: handleStayLoggedIn,
      showDropdown,
      toggleDropdown: () => setShowDropdown((v) => !v),
      logout: handleLogout,
      logoutLoading,
    },
    sessionModal: {
      open: showSessionModal,
      email: modalEmail,
      setEmail: setModalEmail,
      password: modalPassword,
      setPassword: setModalPassword,
      error: modalError,
      loading: modalLoginLoading,
      showPassword: showModalPassword,
      toggleShowPassword: () => setShowModalPassword((v) => !v),
      submit: handleModalLogin,
    },
  };
}
