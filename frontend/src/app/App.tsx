import { useEffect, useMemo, useRef, useState } from "react";
import {
  Routes,
  Route,
  useNavigate,
  Navigate,
  useLocation,
} from "react-router-dom";
import {
  acceptInvitation,
  getMe,
  login,
  logout as logoutRequest,
  requestPasswordReset,
  resetPassword as resetPasswordRequest,
  setAuthToken,
  validateInvitation,
} from "../api/authApi";
import type { LoginResponse, UserProfileResponse } from "../api/authApi";
import type { User } from "../types/auth.types";
import LoginScreen from "../features/auth/LoginScreen";
import ForgotScreen from "../features/auth/ForgotScreen";
import ForgotSentScreen from "../features/auth/ForgotSentScreen";
import ResetPasswordScreen from "../features/auth/ResetPasswordScreen";
import InviteScreen from "../features/auth/InviteScreen";
import NoAccountScreen from "../features/auth/NoAccountScreen";
import DashboardScreen from "../features/dashboard/DashboardScreen";
import SubmissionScreen from "../features/submission/SubmissionScreen";
import UserInvitationsScreen from "../features/user-management/UserInvitationsScreen";
import DashboardLayout from "../components/layout/DashboardLayout";
import SessionModal from "../components/modals/SessionModal";
import Toast from "../components/common/Toast";
import LoginSplash from "../components/common/LoginSplash";
import PageLoader from "../components/common/PageLoader";
import { useToast } from "../context/ToastContext";

const LOCKOUT_LIMIT = 5;
const LOCKOUT_SECONDS = 15 * 60;
const SESSION_WARNING_SECONDS = 5 * 60;

function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();

  const [showSplash, setShowSplash] = useState(false);
  const [splashUser, setSplashUser] = useState<User | null>(null);

  const [loginLoading, setLoginLoading] = useState(false);
  const [forgotLoading, setForgotLoading] = useState(false);
  const [inviteLoading, setInviteLoading] = useState(false);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [showLoginPassword, setShowLoginPassword] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [attempts, setAttempts] = useState(0);
  const [lockRemaining, setLockRemaining] = useState(0);
  const [lockTimerId, setLockTimerId] = useState<number | null>(null);

  const [modalEmail, setModalEmail] = useState("");
  const [modalPassword, setModalPassword] = useState("");
  const [modalError, setModalError] = useState(false);
  const [showModalPassword, setShowModalPassword] = useState(false);

  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotSentEmail, setForgotSentEmail] = useState("");
  const [resetToken, setResetToken] = useState<string | null>(null);
  const [resetPassword, setResetPassword] = useState("");
  const [resetConfirmPassword, setResetConfirmPassword] = useState("");
  const [showResetPassword, setShowResetPassword] = useState(false);
  const [showResetConfirmPassword, setShowResetConfirmPassword] =
    useState(false);
  const [resetLoading, setResetLoading] = useState(false);
  const [resetError, setResetError] = useState("");
  const [resetSuccess, setResetSuccess] = useState(false);

  const [inviteToken, setInviteToken] = useState<string | null>(null);
  const [inviteState, setInviteState] = useState<
    "form" | "success" | "expired" | "already"
  >("form");
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState("");
  const [inviteInstitution, setInviteInstitution] = useState("");
  const [invitePassword, setInvitePassword] = useState("");
  const [inviteConfirmPassword, setInviteConfirmPassword] = useState("");
  const [showInvitePassword, setShowInvitePassword] = useState(false);
  const [showInviteConfirmPassword, setShowInviteConfirmPassword] =
    useState(false);
  const [inviteCountdown, setInviteCountdown] = useState("");

  const [showDropdown, setShowDropdown] = useState(false);
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [showSessionModal, setShowSessionModal] = useState(false);

  const [bannerRemaining, setBannerRemaining] = useState(0);
  const [bannerTimerId, setBannerTimerId] = useState<number | null>(null);
  const [, setSessionWarningDismissed] = useState(false);
  const bannerTimerRef = useRef<number | null>(null);
  const sessionWarningDismissedRef = useRef(false);

  const inviteRules = useMemo(() => {
    const length = invitePassword.length >= 8;
    const upper = /[A-Z]/.test(invitePassword);
    const number = /[0-9]/.test(invitePassword);
    const symbol = /[^A-Za-z0-9]/.test(invitePassword);
    const match =
      inviteConfirmPassword.length > 0 &&
      invitePassword === inviteConfirmPassword;
    return { length, upper, number, symbol, match };
  }, [invitePassword, inviteConfirmPassword]);

  useEffect(() => {
    const savedToken = localStorage.getItem("dasigconnect_token");
    const savedUser = localStorage.getItem("dasigconnect_user");
    if (savedToken && savedUser) {
      setAuthToken(savedToken);
      try {
        const parsedUser = JSON.parse(savedUser) as User;
        setCurrentUser(parsedUser);
        startSessionCountdown(savedToken);
        void refreshCurrentUser(parsedUser);
      } catch {
        localStorage.removeItem("dasigconnect_token");
        localStorage.removeItem("dasigconnect_user");
      }
    }
    setAuthReady(true);
  }, []);

  useEffect(() => {
    if (location.pathname !== "/reset-password") return;
    const params = new URLSearchParams(location.search);
    const token = params.get("token");
    setResetToken(token);
    setResetError(token ? "" : "Reset token is missing or invalid.");
    setResetSuccess(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    if (location.pathname !== "/invite") return;
    const params = new URLSearchParams(location.search);
    const token = params.get("token") || params.get("inviteToken");
    if (token) {
      setInviteToken(token);
      void validateInviteToken(token);
    } else {
      setInviteState("expired");
    }
  }, [location.pathname, location.search]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "Enter") return;

      if (location.pathname === "/" || location.pathname === "/login") {
        void handleLogin();
      } else if (location.pathname === "/forgot-password") {
        void handleForgotSubmit();
      } else if (location.pathname === "/reset-password") {
        void handleResetPassword();
      } else if (location.pathname === "/dashboard" && showSessionModal) {
        void handleModalLogin();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [
    location.pathname,
    showSessionModal,
    loginEmail,
    loginPassword,
    modalEmail,
    modalPassword,
  ]);

  useEffect(() => {
    return () => {
      if (lockTimerId) window.clearInterval(lockTimerId);
      if (bannerTimerId) window.clearInterval(bannerTimerId);
    };
  }, [lockTimerId, bannerTimerId]);

  const bannerTime = formatTimer(bannerRemaining);

  function triggerLockout() {
    setLockRemaining(LOCKOUT_SECONDS);
    const id = window.setInterval(() => {
      setLockRemaining((prev) => {
        if (prev <= 1) {
          window.clearInterval(id);
          setLockTimerId(null);
          setAttempts(0);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    setLockTimerId(id);
  }

  function resetLoginState() {
    setLoginPassword("");
    setLoginError("");
    setAttempts(0);
    setLockRemaining(0);
    if (lockTimerId) {
      window.clearInterval(lockTimerId);
      setLockTimerId(null);
    }
  }

  async function handleLogin() {
    if (lockRemaining > 0) return;
    setLoginLoading(true);
    const email = loginEmail.trim().toLowerCase();
    try {
      const response = await login(email, loginPassword);
      const apiUser = response.data;
      const fallbackUser = buildUserFromLogin(email, apiUser);
      setAuthToken(apiUser.accessToken);
      const user = await loadCurrentUser(fallbackUser);
      localStorage.setItem("dasigconnect_token", apiUser.accessToken);
      localStorage.setItem("dasigconnect_user", JSON.stringify(user));
      setCurrentUser(user);
      startSessionCountdown(apiUser.accessToken);
      setSplashUser(user);
      setShowSplash(true);
      window.setTimeout(() => setShowSplash(false), 1900);
      navigate("/dashboard");
      resetLoginState();
    } catch (err: unknown) {
      const nextAttempts = attempts + 1;
      setAttempts(nextAttempts);
      if (nextAttempts >= LOCKOUT_LIMIT) {
        triggerLockout();
      } else {
        setLoginError(
          getApiErrorMessage(err, "") ||
            `Invalid credentials. ${LOCKOUT_LIMIT - nextAttempts} attempts remaining before lockout.`,
        );
      }
    } finally {
      setLoginLoading(false);
    }
  }

  async function handleResetPassword() {
    if (!resetToken) {
      setResetError("Reset token is missing or invalid.");
      return;
    }
    if (resetPassword.length < 8) {
      setResetError("Password must be at least 8 characters.");
      return;
    }
    if (resetPassword !== resetConfirmPassword) {
      setResetError("Passwords do not match.");
      return;
    }

    setResetLoading(true);
    setResetError("");
    try {
      await resetPasswordRequest(resetToken, resetPassword);
      setResetSuccess(true);
      setResetPassword("");
      setResetConfirmPassword("");
    } catch (err: unknown) {
      setResetError(getApiErrorMessage(err, "Password reset failed."));
    } finally {
      setResetLoading(false);
    }
  }

  async function handleForgotSubmit() {
    const email = forgotEmail.trim() || "yourname@institution.edu.ph";
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

  async function handleInviteActivate() {
    if (!inviteToken) return;
    setInviteLoading(true);
    try {
      const response = await acceptInvitation(inviteToken, invitePassword);
      setAuthToken(response.data.accessToken);
      const email = inviteEmail.trim().toLowerCase();
      const fallbackUser = buildUserFromLogin(
        email,
        response.data,
        inviteInstitution,
      );
      const user = await loadCurrentUser(fallbackUser);
      localStorage.setItem("dasigconnect_token", response.data.accessToken);
      localStorage.setItem("dasigconnect_user", JSON.stringify(user));
      setCurrentUser(user);
      startSessionCountdown(response.data.accessToken);
      setInviteState("success");
    } catch {
      setInviteState("expired");
    } finally {
      setInviteLoading(false);
    }
  }

  async function handleLogout() {
    try {
      await logoutRequest();
    } catch {
      // Best-effort logout.
    }
    localStorage.removeItem("dasigconnect_token");
    localStorage.removeItem("dasigconnect_user");
    setAuthToken(null);
    setCurrentUser(null);
    setShowDropdown(false);
    setShowSessionModal(false);
    stopSessionCountdown();
    resetLoginState();
    navigate("/login");
    toast.info("You have been signed out.");
  }

  async function handleModalLogin() {
    const email = modalEmail.trim().toLowerCase();
    try {
      const response = await login(email, modalPassword);
      setAuthToken(response.data.accessToken);
      const fallbackUser = buildUserFromLogin(email, response.data);
      const user = await loadCurrentUser(fallbackUser);
      localStorage.setItem("dasigconnect_token", response.data.accessToken);
      localStorage.setItem("dasigconnect_user", JSON.stringify(user));
      setCurrentUser(user);
      startSessionCountdown(response.data.accessToken);
      setShowSessionModal(false);
      setModalError(false);
    } catch {
      setModalError(true);
    }
  }

  function stopSessionCountdown() {
    if (bannerTimerRef.current) window.clearInterval(bannerTimerRef.current);
    bannerTimerRef.current = null;
    setBannerRemaining(0);
    setBannerTimerId(null);
    sessionWarningDismissedRef.current = false;
    setSessionWarningDismissed(false);
  }

  function handleStayLoggedIn() {
    setModalEmail(currentUser?.email || "");
    setShowSessionModal(true);
    setBannerRemaining(0);
  }

  function dismissSessionBanner() {
    sessionWarningDismissedRef.current = true;
    setSessionWarningDismissed(true);
    setBannerRemaining(0);
  }

  function startSessionCountdown(token: string) {
    if (bannerTimerRef.current) window.clearInterval(bannerTimerRef.current);
    bannerTimerRef.current = null;
    sessionWarningDismissedRef.current = false;
    setSessionWarningDismissed(false);

    const expiresAt = getTokenExpiryMs(token);
    if (!expiresAt) return;

    let timerId: number | null = null;
    const tick = () => {
      const remaining = Math.ceil((expiresAt - Date.now()) / 1000);
      if (remaining <= 0) {
        if (timerId) window.clearInterval(timerId);
        bannerTimerRef.current = null;
        setBannerTimerId(null);
        setBannerRemaining(0);
        setModalEmail(currentUser?.email || loginEmail);
        setShowSessionModal(true);
        return;
      }
      if (
        remaining <= SESSION_WARNING_SECONDS &&
        !sessionWarningDismissedRef.current
      ) {
        setBannerRemaining(remaining);
      }
    };

    tick();
    timerId = window.setInterval(tick, 1000);
    bannerTimerRef.current = timerId;
    setBannerTimerId(timerId);
  }

  async function refreshCurrentUser(fallbackUser: User) {
    const user = await loadCurrentUser(fallbackUser);
    localStorage.setItem("dasigconnect_user", JSON.stringify(user));
    setCurrentUser(user);
  }

  async function validateInviteToken(token: string) {
    try {
      const response = await validateInvitation(token);
      const data = response.data;
      setInviteEmail(data.recipientEmail);
      setInviteRole(formatRoleLabel(data.assignedRole));
      setInviteInstitution(data.institutionName);
      setInviteCountdown(`Invitation expires ${formatExpiry(data.expiresAt)}`);
    } catch {
      setInviteState("expired");
    }
  }

  if (!authReady) {
    return <PageLoader />;
  }

  return (
    <>
      <Toast />
      <LoginSplash user={splashUser} visible={showSplash} />
      <Routes>
        <Route
          path="/"
          element={
            currentUser ? (
              <Navigate to="/dashboard" replace />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />

        <Route
          path="/login"
          element={
            <LoginScreen
              active={true}
              email={loginEmail}
              password={loginPassword}
              showPassword={showLoginPassword}
              loginError={loginError}
              attempts={attempts}
              lockRemaining={lockRemaining}
              onEmailChange={setLoginEmail}
              onPasswordChange={setLoginPassword}
              onTogglePassword={() => setShowLoginPassword(!showLoginPassword)}
              onLogin={() => void handleLogin()}
              onForgot={() => navigate("/forgot-password")}
              onNoAccount={() => navigate("/no-account")}
              onRequestReset={() => navigate("/forgot-password")}
              loading={loginLoading}
            />
          }
        />

        <Route
          path="/forgot-password"
          element={
            <ForgotScreen
              active={true}
              email={forgotEmail}
              onEmailChange={setForgotEmail}
              onSubmit={() => void handleForgotSubmit()}
              onBack={() => navigate("/login")}
              loading={forgotLoading}
            />
          }
        />

        <Route
          path="/forgot-password-sent"
          element={
            <ForgotSentScreen
              active={true}
              email={forgotSentEmail}
              onBack={() => navigate("/login")}
            />
          }
        />

        <Route
          path="/reset-password"
          element={
            <ResetPasswordScreen
              active={true}
              password={resetPassword}
              confirmPassword={resetConfirmPassword}
              showPassword={showResetPassword}
              showConfirmPassword={showResetConfirmPassword}
              loading={resetLoading}
              error={resetError}
              success={resetSuccess}
              onPasswordChange={setResetPassword}
              onConfirmPasswordChange={setResetConfirmPassword}
              onTogglePassword={() => setShowResetPassword(!showResetPassword)}
              onToggleConfirmPassword={() =>
                setShowResetConfirmPassword(!showResetConfirmPassword)
              }
              onSubmit={() => void handleResetPassword()}
              onBack={() => navigate("/login")}
            />
          }
        />

        <Route
          path="/invite"
          element={
            <InviteScreen
              active={true}
              state={inviteState}
              email={inviteEmail}
              roleLabel={inviteRole}
              institution={inviteInstitution}
              password={invitePassword}
              confirmPassword={inviteConfirmPassword}
              rules={inviteRules}
              inviteCountdown={inviteCountdown}
              onPasswordChange={setInvitePassword}
              onConfirmPasswordChange={setInviteConfirmPassword}
              onTogglePassword={() =>
                setShowInvitePassword(!showInvitePassword)
              }
              onToggleConfirmPassword={() =>
                setShowInviteConfirmPassword(!showInviteConfirmPassword)
              }
              onActivate={() => void handleInviteActivate()}
              onBackToLogin={() => navigate("/login")}
              showPassword={showInvitePassword}
              showConfirmPassword={showInviteConfirmPassword}
              loading={inviteLoading}
            />
          }
        />

        <Route
          path="/no-account"
          element={
            <NoAccountScreen active={true} onBack={() => navigate("/login")} />
          }
        />

        <Route
          element={
            currentUser ? (
              <DashboardLayout
                user={currentUser}
                showBanner={bannerRemaining > 0}
                bannerTime={bannerTime}
                showDropdown={showDropdown}
                onToggleDropdown={() => setShowDropdown(!showDropdown)}
                onDismissBanner={dismissSessionBanner}
                onStayLoggedIn={handleStayLoggedIn}
                onLogout={() => void handleLogout()}
              />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        >
          <Route
            path="/dashboard"
            element={<DashboardScreen user={currentUser!} />}
          />
          <Route
            path="/admin/user-management/invitations"
            element={<UserInvitationsScreen user={currentUser!} />}
          />
        </Route>

        <Route
          path="/submissions/new"
          element={
            currentUser ? (
              <SubmissionScreen user={currentUser} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <SessionModal
        open={showSessionModal}
        email={modalEmail}
        password={modalPassword}
        error={modalError}
        onEmailChange={setModalEmail}
        onPasswordChange={setModalPassword}
        onTogglePassword={() => setShowModalPassword(!showModalPassword)}
        onSubmit={() => void handleModalLogin()}
        showPassword={showModalPassword}
      />
    </>
  );
}

function formatTimer(seconds: number) {
  const safeSeconds = Math.max(seconds, 0);
  const minutes = Math.floor(safeSeconds / 60);
  const remaining = safeSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${remaining
    .toString()
    .padStart(2, "0")}`;
}

function mapApiRole(role: string): User["role"] {
  const normalized = role.toLowerCase();
  if (normalized.includes("admin")) return "admin";
  if (normalized.includes("validator")) return "validator";
  return "contributor";
}

async function loadCurrentUser(fallbackUser: User) {
  try {
    const response = await getMe();
    return buildUserFromProfile(response.data, fallbackUser.email);
  } catch {
    return fallbackUser;
  }
}

function buildUserFromLogin(
  email: string,
  apiUser: LoginResponse,
  fallbackInstitutionName?: string,
): User {
  return {
    email,
    pw: "",
    role: mapApiRole(apiUser.role),
    name: displayNameFromEmail(email),
    inst: fallbackInstitutionName || institutionFallbackFromEmail(email),
    institutionId: apiUser.institutionId,
    initials: initialsFromEmail(email),
  };
}

function buildUserFromProfile(
  profile: UserProfileResponse,
  fallbackEmail: string,
): User {
  const email = (profile.email || fallbackEmail).trim().toLowerCase();
  return {
    email,
    pw: "",
    role: mapApiRole(profile.role),
    name: displayNameFromEmail(email),
    inst: profile.institutionName || institutionFallbackFromEmail(email),
    institutionId: profile.institutionId,
    initials: initialsFromEmail(email),
  };
}

function displayNameFromEmail(email: string) {
  const name = email.split("@")[0] || "User";
  return name
    .split(".")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function institutionFallbackFromEmail(email: string) {
  const emailDomain = email.split("@")[1]?.split(".")[0]?.toLowerCase() || "";
  return emailDomain.toUpperCase() || "Institution";
}

function initialsFromEmail(email: string) {
  const name = email.split("@")[0] || "U";
  const parts = name.split(".");
  if (parts.length >= 2) {
    return `${parts[0].charAt(0)}${parts[1].charAt(0)}`.toUpperCase();
  }
  return name.substring(0, 2).toUpperCase();
}

function formatRoleLabel(role: string) {
  if (!role) return "Contributor";
  const normalized = role.toLowerCase();
  return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function formatExpiry(expiresAt: string) {
  const date = new Date(expiresAt);
  if (Number.isNaN(date.getTime())) return "soon";
  const diff = date.getTime() - Date.now();
  if (diff <= 0) return "soon";
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  return `in ${hours}h ${minutes}m`;
}

function getTokenExpiryMs(token: string) {
  const [, payload] = token.split(".");
  if (!payload) return null;
  try {
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const claims = JSON.parse(window.atob(padded));
    return typeof claims.exp === "number" ? claims.exp * 1000 : null;
  } catch {
    return null;
  }
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) return fallback;
  const response = error.response;
  if (isRecord(response)) {
    const data = response.data;
    if (isRecord(data)) {
      if (typeof data.error === "string") return data.error;
      if (typeof data.message === "string") return data.message;
    }
  }
  return typeof error.message === "string" ? error.message : fallback;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

export default App;
